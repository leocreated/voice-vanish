package dev.leo.voicevanish.core;

import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class VerifiedBlockNamesTest {
    private VerifiedBlockNames matcher() { return new VerifiedBlockNames(new BlockNames(Map.of(
            "granite","minecraft:granite", "granite stairs","minecraft:granite_stairs",
            "polished granite","minecraft:polished_granite", "diorite","minecraft:diorite",
            "andesite","minecraft:andesite"))); }
    @Test void overlappingWindowsDoNotRepeatBans() {
        var names=matcher();
        assertEquals(List.of("andesite","granite"),names.find("andesite and granite",true));
        assertEquals(List.of("diorite"),names.find("andesite granite diorite",true));
        assertTrue(names.find("granite diorite",true).isEmpty());
    }
    @Test void unfinishedWindowsAndLongerNamesDoNotBanShorterTypes() {
        var names=matcher();
        assertTrue(names.find("granite",false).isEmpty());
        assertTrue(names.find("granite stai",false).isEmpty());
        assertEquals(List.of("granite stairs"),names.find("granite stairs",true));
        assertEquals(List.of("polished granite"),names.find("polished granite",true));
        assertTrue(names.find("and a sight granted diarite",true).isEmpty());
    }
    @Test void ringWindowPreservesContextAcrossWrapAndTrimsLookAhead() {
        var window=new PcmWindow(6);
        window.append(new byte[]{1,2,3,4},4); window.append(new byte[]{5,6,7,8},4);
        assertArrayEquals(new byte[]{3,4,5,6,7,8},window.snapshot(20,0));
        assertArrayEquals(new byte[]{3,4,5,6},window.snapshot(20,2));
        assertArrayEquals(new byte[]{5,6},window.snapshot(2,2));
    }
}
