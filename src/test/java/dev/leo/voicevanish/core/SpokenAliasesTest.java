package dev.leo.voicevanish.core;

import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class SpokenAliasesTest {
    private BlockNames names() {
        Map<String, String> aliases = new LinkedHashMap<>(Map.of(
                "grass block", "minecraft:grass_block", "short grass", "minecraft:short_grass",
                "tall grass", "minecraft:tall_grass", "birch log", "minecraft:birch_log",
                "stripped birch log", "minecraft:stripped_birch_log", "birch leaves", "minecraft:birch_leaves"));
        SpokenAliases.addTo(aliases); return new BlockNames(aliases);
    }
    private List<String> ids(String text) { return names().find(text).stream().map(BlockNames.Hit::blockId).toList(); }
    @Test void naturalGrassPhraseSelectsOnlyGrassBlock() {
        assertEquals(List.of("minecraft:grass_block"), ids("we need grass"));
        assertEquals(List.of("minecraft:grass_block"), ids("we need grass block"));
        assertEquals(List.of("minecraft:tall_grass", "minecraft:short_grass"), ids("tall grass and short grass"));
    }
    @Test void naturalAliasesOverrideCaseEquivalentDisplayNames() {
        var aliases = new LinkedHashMap<String,String>();
        aliases.put("Grass", "minecraft:grass");
        SpokenAliases.addTo(aliases);
        assertEquals("minecraft:grass_block", new BlockNames(aliases).find("grass").getFirst().blockId());
    }
    @Test void birchPhrasesMatchWithOrWithoutConversationalPrefix() {
        assertEquals(List.of("minecraft:birch_log", "minecraft:birch_leaves"), ids("we need birch log and we need birch leaves"));
        assertEquals(List.of("minecraft:birch_log", "minecraft:birch_leaves"), ids("birch logs and birch leaf"));
    }
    @Test void noBroadCategoriesOrSuffixBansAreIntroduced() {
        assertTrue(ids("wood logs leaves log leaf").isEmpty());
        assertEquals(List.of("minecraft:stripped_birch_log"), ids("we need stripped birch logs"));
    }
}
