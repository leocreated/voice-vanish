package dev.leo.voicevanish.core;

import java.util.LinkedHashMap;
import java.util.Map;

/** Natural forms for specific block types, never material-wide categories. */
public final class SpokenAliases {
    private SpokenAliases() {}
    public static void addTo(Map<String, String> aliases) {
        aliases.put("grass", "minecraft:grass_block");
        aliases.put("redstone", "minecraft:redstone_wire");
        for (var entry : new LinkedHashMap<>(aliases).entrySet()) {
            String name = entry.getKey(), id = entry.getValue();
            if (name.toLowerCase(java.util.Locale.ROOT).contains("redstone"))
                aliases.put(name.toLowerCase(java.util.Locale.ROOT).replace("redstone", "red stone"), id);
            if (id.endsWith("_log") && name.endsWith(" log")) aliases.put(name + "s", id);
            if (id.endsWith("_leaves") && name.endsWith(" leaves"))
                aliases.put(name.substring(0, name.length() - "leaves".length()) + "leaf", id);
        }
    }
}
