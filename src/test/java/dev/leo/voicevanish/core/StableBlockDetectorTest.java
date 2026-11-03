package dev.leo.voicevanish.core;

import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class StableBlockDetectorTest {
    private StableBlockDetector detector() {
        Map<String,String> aliases = new HashMap<>(Map.of("birch log","minecraft:birch_log",
                "birch leaves","minecraft:birch_leaves", "stone","minecraft:stone",
                "stone bricks","minecraft:stone_bricks", "redstone ore","minecraft:redstone_ore",
                "redstone block","minecraft:redstone_block"));
        SpokenAliases.addTo(aliases); return new StableBlockDetector(new BlockNames(aliases));
    }
    @Test void namesWorkAnywhereWithoutAnActivationPhrase() {
        var d = detector();
        assertEquals(List.of("birch leaves"), d.update("oh do you have birch leaves", 1000, true));
        assertEquals(List.of("redstone"), d.update("redstone", 2000, true));
        assertEquals(List.of("grass"), d.update("let us grab grass over there", 3000, true));
    }
    @Test void aLongerNameNeverTriggersItsPrefixDuringStreaming() {
        var d = detector();
        assertTrue(d.update("I have stone", 0, false).isEmpty());
        assertTrue(d.update("I have stone", 400, false).isEmpty());
        assertTrue(d.update("I have stone bri", 800, false).isEmpty());
        assertTrue(d.update("I have stone bricks", 1200, false).isEmpty());
        assertEquals(List.of("stone bricks"), d.update("I have stone bricks", 1600, false));
        assertTrue(d.update("I have stone bricks", 2000, true).isEmpty());
    }
    @Test void redstoneOreWaitsForTheWholeName() {
        var d = detector();
        d.update("redstone", 0, false);
        assertTrue(d.update("redstone or", 400, false).isEmpty());
        assertEquals(List.of("redstone ore"), d.update("redstone ore", 800, true));
        assertTrue(d.update("redstone or", 1600, true).isEmpty());
        assertTrue(d.update("redstone or over there", 2000, true).isEmpty());
        assertEquals(List.of("red stone ore"), d.update("red stone ore", 2400, true));
    }
    @Test void sendsStableNamesBeforeEndOfConversationAndOnlyOncePerUtterance() {
        var d = detector();
        assertTrue(d.update("do you have redstone for the build", 0, false).isEmpty());
        assertEquals(List.of("redstone"), d.update("do you have redstone for the build please", 400, false));
        assertTrue(d.update("do you have redstone for the build please", 800, true).isEmpty());
        assertEquals(List.of("redstone"), d.update("redstone", 1600, true));
    }
    @Test void revisedPartialsAndNonBlockSpeechDoNotTrigger() {
        var d = detector();
        d.update("birch log", 0, false);
        assertTrue(d.update("birch law", 400, false).isEmpty());
        assertTrue(d.update("birch law", 800, true).isEmpty());
        assertTrue(d.update("we should head over there wood leaves", 1600, true).isEmpty());
    }
}
