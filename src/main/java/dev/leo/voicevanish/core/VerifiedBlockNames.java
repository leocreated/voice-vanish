package dev.leo.voicevanish.core;

import java.util.*;

/** Whole names in completed neural windows. Never turn similar ordinary words into names. */
public final class VerifiedBlockNames {
    private final BlockNames names;
    private final Set<String> emitted = new HashSet<>();
    public VerifiedBlockNames(BlockNames names) { this.names = names; }
    public List<String> find(String text, boolean endpoint) {
        List<String> tokens = BlockNames.words(text), accepted = new ArrayList<>();
        for (var hit : names.find(text)) {
            if (!SpeechPolicy.needsVerification(hit.blockId()) || emitted.contains(hit.blockId())) continue;
            if (!endpoint && names.couldExtend(tokens, hit)) continue;
            if (hit.firstWord() + hit.wordCount() < tokens.size()
                    && (names.couldExtend(tokens, hit) || names.hasIncompleteExtension(tokens, hit))) continue;
            accepted.add(String.join(" ", tokens.subList(hit.firstWord(), hit.firstWord()+hit.wordCount())));
            emitted.add(hit.blockId());
        }
        return accepted;
    }
}
