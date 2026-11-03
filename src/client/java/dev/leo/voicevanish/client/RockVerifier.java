package dev.leo.voicevanish.client;

import dev.leo.voicevanish.core.*;
import io.github.givimad.whisperjni.*;
import java.nio.file.*;
import java.util.concurrent.*;
import java.util.function.Consumer;

/** One bounded background decoder. PCM exists only in memory while checking speech. */
final class RockVerifier implements AutoCloseable {
    private final WhisperJNI whisper;
    private final WhisperContext context;
    private final VerifiedBlockNames names;
    private final Consumer<StreamingSpeech.Update> result;
    private final Consumer<Throwable> failure;
    private final ThreadPoolExecutor executor;
    private volatile boolean closed;
    private static final String PROMPT = "We are playing Minecraft. Blocks include andesite, granite, diorite, polished andesite, polished granite, polished diorite, redstone ore, redstone, birch leaves, birch log, grass block and stone bricks.";

    RockVerifier(Path model, BlockNames names, Consumer<StreamingSpeech.Update> result, Consumer<Throwable> failure) throws Exception {
        if (!Files.isRegularFile(model)) throw new IllegalArgumentException("Whisper model missing. Install the 0.1.3 update.");
        WhisperJNI.loadLibrary(); WhisperJNI.setLibraryLogger(null);
        whisper = new WhisperJNI(); context = whisper.init(model);
        this.names = new VerifiedBlockNames(names); this.result = result; this.failure = failure;
        executor = new ThreadPoolExecutor(1, 1, 0, TimeUnit.MILLISECONDS, new ArrayBlockingQueue<>(8), runnable -> {
            Thread thread = new Thread(runnable, "voicevanish-rock-check"); thread.setDaemon(true); return thread;
        });
    }
    void submit(long utterance, byte[] pcm, int rate, boolean endpoint) {
        if (closed || !PcmAudio.hasVoice(pcm, rate)) return;
        executor.execute(() -> {
            if (closed) return;
            try {
                float[] samples = PcmAudio.at16000(pcm, rate);
                var params = new WhisperFullParams(WhisperSamplingStrategy.GREEDY);
                params.nThreads = WhisperTuning.threads(Runtime.getRuntime().availableProcessors());
                params.audioCtx = WhisperTuning.audioContext(samples.length);
                params.language = "en"; params.noContext = true;
                params.noTimestamps = true; params.printProgress = false; params.printTimestamps = false;
                params.temperatureInc = 0; params.initialPrompt = PROMPT;
                int code = whisper.full(context, params, samples, samples.length);
                if (code != 0) throw new IllegalStateException("Whisper returned " + code);
                StringBuilder text = new StringBuilder();
                for (int i = 0; i < whisper.fullNSegments(context); i++) text.append(whisper.fullGetSegmentText(context, i));
                var matches = names.find(text.toString(), endpoint);
                if (!closed && (!matches.isEmpty() || endpoint))
                    result.accept(new StreamingSpeech.Update(utterance, "checked", text.toString().strip(), matches, endpoint));
            } catch (Throwable ex) { if (!closed) failure.accept(ex); }
        });
    }
    void awaitIdle() throws Exception { executor.submit(() -> {}).get(60, TimeUnit.SECONDS); }
    @Override public void close() {
        closed = true; executor.getQueue().clear();
        executor.execute(context::close); executor.shutdown();
    }
}
