package dev.leo.voicevanish.core;

import java.text.Normalizer;
import java.util.*;

/** Longest, non-overlapping whole-name matches. Never guess categories. */
public final class BlockNames {
    public static final Set<String> PROTECTED = Set.of("minecraft:end_portal", "minecraft:end_portal_frame",
            "minecraft:air", "minecraft:cave_air", "minecraft:void_air");
    private final List<Name> names;
    public record Hit(String blockId, int firstWord, int wordCount) {}
    private record Name(String id, List<String> words) {}

    public BlockNames(Map<String, String> aliases) {
        Map<List<String>, String> canonical = new LinkedHashMap<>();
        aliases.forEach((name, id) -> canonical.put(words(name), id));
        names = canonical.entrySet().stream()
                .filter(e -> !PROTECTED.contains(e.getValue()))
                .map(e -> new Name(e.getValue(), e.getKey()))
                .filter(n -> !n.words.isEmpty())
                .sorted(Comparator.<Name>comparingInt(n -> n.words.size()).reversed()
                        .thenComparing(n -> String.join(" ", n.words)))
                .toList();
    }

    public static List<String> words(String text) {
        String clean = Normalizer.normalize(text, Normalizer.Form.NFKC).toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", " ").strip();
        return clean.isEmpty() ? List.of() : List.of(clean.split(" +"));
    }

    public List<String> aliases() {
        return names.stream().map(n -> String.join(" ", n.words)).distinct().toList();
    }

    /** An unfinished longer name must not trigger its shorter prefix. */
    public boolean couldExtend(List<String> tokens, Hit hit) {
        String suffix = String.join(" ", tokens.subList(hit.firstWord, tokens.size()));
        return names.stream().map(n -> String.join(" ", n.words))
                .anyMatch(name -> name.length() > suffix.length() && name.startsWith(suffix));
    }

    /** Reject a shorter name if the next word resembles an unfinished longer name. */
    public boolean hasIncompleteExtension(List<String> tokens, Hit hit) {
        for (Name name : names) {
            for (int i = 0; i < name.words.size() && hit.firstWord + i < tokens.size(); i++) {
                String expected = name.words.get(i), heard = tokens.get(hit.firstWord + i);
                if (expected.equals(heard)) continue;
                if (i >= hit.wordCount && expected.startsWith(heard)) return true;
                break;
            }
        }
        return false;
    }

    public List<Hit> find(String text) {
        List<String> tokens = words(text);
        List<Hit> hits = new ArrayList<>();
        for (int i = 0; i < tokens.size();) {
            Name found = null;
            for (Name name : names) {
                if (i + name.words.size() <= tokens.size()
                        && tokens.subList(i, i + name.words.size()).equals(name.words)) {
                    found = name;
                    break;
                }
            }
            if (found == null) i++;
            else { hits.add(new Hit(found.id, i, found.words.size())); i += found.words.size(); }
        }
        return hits;
    }
}
