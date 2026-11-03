package dev.leo.voicevanish.core;

import java.util.*;

/** Accept whole names stable over separate decoder updates; defer ambiguous prefixes. */
public final class StableBlockDetector {
    private final BlockNames names;
    private final Map<String, Long> pending = new HashMap<>();
    private final Set<String> emitted = new HashSet<>();
    private final java.util.function.Predicate<String> include;
    public StableBlockDetector(BlockNames names) { this(names, id -> true); }
    public StableBlockDetector(BlockNames names, java.util.function.Predicate<String> include) { this.names = names; this.include = include; }

    public List<String> update(String text, long audioMillis, boolean endpoint) {
        List<String> words = BlockNames.words(text), accepted = new ArrayList<>();
        Set<String> present = new HashSet<>();
        for (var hit : names.find(text)) {
            if (!include.test(hit.blockId())) continue;
            String phrase = String.join(" ", words.subList(hit.firstWord(), hit.firstWord() + hit.wordCount()));
            String key = hit.firstWord() + ":" + phrase;
            present.add(key);
            long since = pending.computeIfAbsent(key, ignored -> audioMillis);
            boolean incompleteLongerName = hit.firstWord() + hit.wordCount() < words.size()
                    && (names.couldExtend(words, hit) || names.hasIncompleteExtension(words, hit));
            if (!incompleteLongerName && !emitted.contains(hit.blockId()) && (endpoint ||
                    (audioMillis - since >= 320 && !names.couldExtend(words, hit)))) {
                accepted.add(phrase); emitted.add(hit.blockId());
            }
        }
        pending.keySet().retainAll(present);
        if (endpoint) { pending.clear(); emitted.clear(); }
        return accepted;
    }
}
