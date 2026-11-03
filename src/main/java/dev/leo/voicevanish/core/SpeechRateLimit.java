package dev.leo.voicevanish.core;

import java.util.*;

/** Per-player rate limit with room for the two decoders to report together. */
public final class SpeechRateLimit {
    private static final class Bucket { double tokens = 4; long updated; Bucket(long now) { updated = now; } }
    private final Map<UUID,Bucket> players = new HashMap<>();
    public boolean allow(UUID player, long now) {
        Bucket bucket = players.computeIfAbsent(player, ignored -> new Bucket(now));
        bucket.tokens = Math.min(4, bucket.tokens + Math.max(0, now - bucket.updated) / 200_000_000.0);
        bucket.updated = now;
        if (bucket.tokens < 1) return false;
        bucket.tokens--; return true;
    }
    public void clear() { players.clear(); }
}
