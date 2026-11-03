package dev.leo.voicevanish.core;

/** A bounded in-memory audio window; decoding never takes audio away from capture. */
public final class PcmWindow {
    private final byte[] bytes;
    private int next, size;
    public PcmWindow(int capacity) { bytes = new byte[capacity]; }
    public void append(byte[] input, int count) {
        for (int i=0; i<count; i++) { bytes[next] = input[i]; next = (next+1) % bytes.length; if (size < bytes.length) size++; }
    }
    public byte[] snapshot(int limit, int trimEnd) {
        int trim = Math.min(trimEnd, size), length = Math.min(limit, size-trim);
        byte[] result = new byte[length];
        int start = Math.floorMod(next - trim - length, bytes.length);
        for (int i=0; i<length; i++) result[i] = bytes[(start+i) % bytes.length];
        return result;
    }
}
