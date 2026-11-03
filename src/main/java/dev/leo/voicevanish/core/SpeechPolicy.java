package dev.leo.voicevanish.core;

/** These easily confused mineral names are decided by the contextual second pass. */
public final class SpeechPolicy {
    private SpeechPolicy() {}
    public static boolean needsVerification(String id) {
        return id.matches("minecraft:(polished_)?(andesite|granite|diorite)(_(stairs|slab|wall))?");
    }
}
