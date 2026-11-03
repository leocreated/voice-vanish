package dev.leo.voicevanish.core;

/** Bound CPU use and size the encoder for the actual 16 kHz clip, plus silence padding. */
public final class WhisperTuning {
    private WhisperTuning() {}
    public static int threads(int available) { return Math.max(1, Math.min(4, available / 2)); }
    public static int audioContext(int samples) {
        if (samples < 0) throw new IllegalArgumentException("Negative sample count");
        return (int) Math.min(1500L, Math.max(500L, ((long) samples + 319) / 320 + 250));
    }
}
