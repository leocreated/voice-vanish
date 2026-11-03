package dev.leo.voicevanish.core;

import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class SpeechPolicyTest {
    private BlockNames names() { return new BlockNames(Map.of("andesite","minecraft:andesite",
            "granite","minecraft:granite", "diorite","minecraft:diorite",
            "polished diorite","minecraft:polished_diorite", "birch log","minecraft:birch_log")); }
    @Test void fastAndCheckedPassesHaveDistinctAuthority() {
        var fast=new StableBlockDetector(names(),id -> !SpeechPolicy.needsVerification(id));
        var checked=new StableBlockDetector(names(),SpeechPolicy::needsVerification);
        assertEquals(List.of("birch log"),fast.update("andesite granite diorite birch log",0,true));
        assertEquals(List.of("andesite","granite","diorite"),checked.update("andesite granite diorite birch log",0,true));
        assertEquals(List.of("polished diorite"),checked.update("polished diorite",0,true));
    }
    @Test void ordinarySimilarWordsAreNeverAliasesForRocks() {
        assertTrue(new StableBlockDetector(names()).update("and a sight granted do you write died right diarite",0,true).isEmpty());
        assertTrue(SpeechPolicy.needsVerification("minecraft:polished_andesite_stairs"));
        assertTrue(SpeechPolicy.needsVerification("minecraft:granite_wall"));
        assertFalse(SpeechPolicy.needsVerification("minecraft:stone_bricks"));
    }
}
