package dev.leo.voicevanish.client;

import com.k2fsa.sherpa.onnx.*;
import dev.leo.voicevanish.core.*;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.function.Consumer;

/** CPU-only local streaming speech. No microphone/audio upload or audio recording. */
final class StreamingSpeech implements AutoCloseable {
    record Update(long utterance, String source, String text, List<String> matches, boolean endpoint) {}
    private final OnlineRecognizer recognizer;
    private final OnlineStream stream;
    private final StableBlockDetector detector;
    private final Consumer<Update> result;
    private double audioMillis;
    private long utterance;

    StreamingSpeech(Path model, BlockNames names, Consumer<Update> result) throws Exception {
        String suffix = "-epoch-99-avg-1-chunk-16-left-128";
        String encoder = "encoder" + suffix + ".int8.onnx", decoder = "decoder" + suffix + ".onnx",
                joiner = "joiner" + suffix + ".int8.onnx";
        for (String file : List.of(encoder, decoder, joiner, "tokens.txt", "bpe.vocab"))
            if (!Files.isRegularFile(model.resolve(file)))
                throw new IllegalArgumentException("Streaming speech model missing: " + file + ". Install the 0.1.1 update.");
        Path hotwords = model.resolve("minecraft-hotwords.txt");
        Files.write(hotwords, names.aliases().stream().map(s -> s.toUpperCase(Locale.ROOT)).toList(), StandardCharsets.UTF_8);
        var modelConfig = OnlineModelConfig.builder().setTransducer(OnlineTransducerModelConfig.builder()
                .setEncoder(model.resolve(encoder).toString()).setDecoder(model.resolve(decoder).toString())
                .setJoiner(model.resolve(joiner).toString()).build())
                .setTokens(model.resolve("tokens.txt").toString()).setNumThreads(2).setDebug(false)
                .setModelingUnit("bpe").setBpeVocab(model.resolve("bpe.vocab").toString()).build();
        var config = OnlineRecognizerConfig.builder().setOnlineModelConfig(modelConfig)
                .setDecodingMethod("modified_beam_search").setMaxActivePaths(4)
                .setHotwordsFile(hotwords.toString()).setHotwordsScore(1.5f).setEnableEndpoint(true)
                .setEndpointConfig(EndpointConfig.builder().setRule2(EndpointRule.builder()
                        .setMustContainNonSilence(true).setMinTrailingSilence(.55f).build()).build()).build();
        recognizer = new OnlineRecognizer(config);
        stream = recognizer.createStream();
        detector = new StableBlockDetector(names, id -> !SpeechPolicy.needsVerification(id));
        this.result = result;
    }

    void accept(byte[] bytes, int count, int sampleRate) {
        float[] samples = new float[count / 2];
        for (int i = 0; i < samples.length; i++)
            samples[i] = (short) ((bytes[2*i] & 255) | (bytes[2*i+1] << 8)) / 32768f;
        audioMillis += samples.length * 1000.0 / sampleRate;
        stream.acceptWaveform(samples, sampleRate);
        boolean decoded = false;
        while (recognizer.isReady(stream)) { recognizer.decode(stream); decoded = true; }
        if (!decoded) return;
        boolean endpoint = recognizer.isEndpoint(stream);
        String text = recognizer.getResult(stream).getText().strip();
        List<String> matches = detector.update(text, (long) audioMillis, endpoint);
        if (!matches.isEmpty() || endpoint) result.accept(new Update(utterance, "fast", text, matches, endpoint));
        if (endpoint) { recognizer.reset(stream); utterance++; }
    }
    @Override public void close() { stream.release(); recognizer.release(); }
}
