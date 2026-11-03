package dev.leo.voicevanish.client;

import dev.leo.voicevanish.VoiceVanish;
import dev.leo.voicevanish.core.BlockNames;
import javax.sound.sampled.*;
import java.nio.file.*;
import java.util.*;
import java.util.function.Consumer;

final class Microphone implements AutoCloseable {
    private final MicConfig config;
    private final Consumer<StreamingSpeech.Update> result;
    private final BlockNames names;
    private volatile boolean running = true;
    private volatile TargetDataLine line;
    volatile String status = "Loading speech model";
    volatile boolean failed;
    Microphone(MicConfig config, BlockNames names, Consumer<StreamingSpeech.Update> result) {
        this.config = config; this.names = names; this.result = result;
        Thread thread = new Thread(this::run, "voicevanish-microphone"); thread.setDaemon(true); thread.start();
    }
    static List<Mixer.Info> devices() {
        return Arrays.stream(AudioSystem.getMixerInfo()).filter(info -> Arrays.stream(AudioSystem.getMixer(info).getTargetLineInfo())
                .anyMatch(line -> TargetDataLine.class.isAssignableFrom(line.getLineClass()))).toList();
    }
    private TargetDataLine openLine() throws Exception {
        Mixer selected = null;
        if (!config.deviceName.isEmpty()) {
            Mixer.Info info = devices().stream().filter(i -> i.getName().equals(config.deviceName)).findFirst()
                    .orElseThrow(() -> new IllegalArgumentException("Selected microphone is unavailable. Use /vvmic devices and /vvmic device."));
            selected = AudioSystem.getMixer(info);
        }
        Exception last = null;
        for (float rate : new float[]{16000, 48000, 44100}) {
            TargetDataLine attempt = null;
            try {
                AudioFormat format = new AudioFormat(rate, 16, 1, true, false);
                DataLine.Info info = new DataLine.Info(TargetDataLine.class, format);
                attempt = (TargetDataLine) (selected == null ? AudioSystem.getLine(info) : selected.getLine(info));
                attempt.open(format, (int) rate); return attempt;
            } catch (Exception ex) { if (attempt != null) attempt.close(); last = ex; }
        }
        throw new LineUnavailableException("Cannot open mono PCM microphone: " + (last == null ? "unknown error" : last.getMessage()));
    }
    private void run() {
        try {
            Path modelPath = config.model();
            try (HybridSpeech recognizer = new HybridSpeech(modelPath, config.checkModel(), names, result, ex -> {
                if (running) {
                    failed = true; status = "ERROR: speech check failed: " + ex.getMessage();
                    VoiceVanish.LOG.error("Rock speech check failed", ex); close();
                }
            })) {
                if (!running) return;
                line = openLine();
                try {
                    if (!running) return;
                    line.start(); status = "Streaming + Whisper rock check, " + (int) line.getFormat().getSampleRate() + " Hz, "
                            + (config.deviceName.isEmpty() ? "Windows default" : config.deviceName);
                    byte[] bytes = new byte[(int) line.getFormat().getSampleRate() / 5];
                    while (running) {
                        int count = line.read(bytes, 0, bytes.length);
                        if (count > 0 && running) recognizer.accept(bytes, count, (int) line.getFormat().getSampleRate());
                    }
                } finally { if (line != null) line.close(); }
            }
        } catch (Throwable ex) {
            if (running) { failed = true; status = "ERROR: " + ex.getMessage(); VoiceVanish.LOG.error("Microphone stopped", ex); }
        } finally { running = false; if (!failed) status = "Stopped"; }
    }
    @Override public void close() { running = false; TargetDataLine current = line; if (current != null) { current.stop(); current.close(); } }
}
