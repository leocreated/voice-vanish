package dev.leo.voicevanish.core;

/** In-memory mono PCM conversion, with a small pre-roll for Whisper's first word. */
public final class PcmAudio {
    private PcmAudio() {}
    public static boolean hasVoice(byte[] pcm, int rate) {
        int frame = Math.max(1, rate / 50), active = 0;
        for (int start = 0; start + frame * 2 <= pcm.length; start += frame * 2) {
            double energy = 0;
            for (int i=0; i<frame; i++) {
                double value = sample(pcm, start / 2 + i) / 32768.0;
                energy += value * value;
            }
            if (Math.sqrt(energy / frame) >= .003 && ++active >= 6) return true;
        }
        return false;
    }
    private static short sample(byte[] pcm, int index) {
        return (short) ((pcm[2*index] & 255) | (pcm[2*index+1] << 8));
    }
    public static float[] at16000(byte[] pcm, int rate) {
        if (rate <= 0 || (pcm.length & 1) != 0) throw new IllegalArgumentException("Invalid PCM");
        int count = pcm.length / 2, length = (int) ((long) count * 16000 / rate), padding = 8000;
        float[] result = new float[length + padding];
        for (int i = 0; i < length; i++) {
            double position = i * (double) rate / 16000;
            int first = Math.min((int) position, count - 1), second = Math.min(first + 1, count - 1);
            double fraction = position - first;
            result[padding + i] = (float) ((sample(pcm, first) * (1-fraction) + sample(pcm, second) * fraction) / 32768.0);
        }
        return result;
    }
}
