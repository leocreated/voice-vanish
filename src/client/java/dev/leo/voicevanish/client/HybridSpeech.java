package dev.leo.voicevanish.client;

import dev.leo.voicevanish.core.*;
import java.nio.file.Path;
import java.util.function.Consumer;

/** Fast normal-name detection plus an independent contextual check for rock names. */
final class HybridSpeech implements AutoCloseable {
    private final StreamingSpeech streaming;
    private final RockVerifier verifier;
    private PcmWindow pcm;
    private int rate;
    private double audioMillis, previousEndpoint, nextCheck = 3000;
    private long utterance;
    HybridSpeech(Path streamingModel, Path checkModel, BlockNames names, Consumer<StreamingSpeech.Update> result,
                 Consumer<Throwable> failure) throws Exception {
        verifier = new RockVerifier(checkModel, names, result, failure);
        try {
            streaming = new StreamingSpeech(streamingModel, names, update -> {
                if (!update.text().isBlank() || !update.matches().isEmpty()) result.accept(update);
                if (update.endpoint()) {
                    int fresh = (int) ((audioMillis - previousEndpoint + 800) * rate / 1000) * 2;
                    if (PcmAudio.hasVoice(pcm.snapshot(fresh, 0), rate)) check(update.utterance(), true);
                    previousEndpoint = audioMillis; nextCheck = audioMillis + 3000; utterance = update.utterance() + 1;
                }
            });
        } catch (Throwable ex) { verifier.close(); throw ex; }
    }
    void accept(byte[] bytes, int count, int sampleRate) {
        rate = sampleRate;
        if (pcm == null) pcm = new PcmWindow(rate * 2 * 12);
        pcm.append(bytes, count); audioMillis += count * 500.0 / rate;
        streaming.accept(bytes, count, sampleRate);
        if (audioMillis >= nextCheck) {
            if (PcmAudio.hasVoice(pcm.snapshot(rate * 2 * 3, 0), rate)) check(utterance, false);
            nextCheck = audioMillis + 3000;
        }
    }
    private void check(long utterance, boolean endpoint) {
        // Keep complete recent context, excluding encoder look-ahead at the end.
        verifier.submit(utterance, pcm.snapshot(rate * 2 * 12, rate * 4 / 5), rate, endpoint);
    }
    void awaitIdle() throws Exception { verifier.awaitIdle(); }
    @Override public void close() { streaming.close(); verifier.close(); pcm = null; }
}
