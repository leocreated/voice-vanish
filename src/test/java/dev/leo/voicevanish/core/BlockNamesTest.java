package dev.leo.voicevanish.core;

import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class BlockNamesTest {
    private final BlockNames names = new BlockNames(Map.of(
            "dirt", "minecraft:dirt", "coarse dirt", "minecraft:coarse_dirt",
            "stone", "minecraft:stone", "smooth stone", "minecraft:smooth_stone",
            "oak planks", "minecraft:oak_planks", "redstone block", "minecraft:redstone_block",
            "end portal", "minecraft:end_portal", "end portal frame", "minecraft:end_portal_frame"));
    private List<String> ids(String text) { return names.find(text).stream().map(BlockNames.Hit::blockId).toList(); }
    @Test void namesInsideNaturalSentencesCount() {
        assertEquals(List.of("minecraft:dirt", "minecraft:oak_planks"), ids("We need DIRT, then oak planks!"));
    }
    @Test void longerNamesDoNotAlsoBanTheirSuffix() {
        assertEquals(List.of("minecraft:coarse_dirt", "minecraft:smooth_stone"), ids("coarse dirt and smooth stone"));
    }
    @Test void wordFragmentsAndCategoriesDoNotCount() {
        assertTrue(ids("redstone sandstone dirty wood planks leaves").isEmpty());
    }
    @Test void protectedPhrasesCannotFallThroughToOtherBlocks() {
        assertTrue(ids("end portal frame and end portal").isEmpty());
    }
    @Test void severalNamesAndRepeatedNamesAreReturned() {
        assertEquals(List.of("minecraft:dirt", "minecraft:dirt", "minecraft:stone"), ids("dirt dirt stone"));
    }
}
