package dev.leo.voicevanish.core;

import java.util.UUID;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class SpeechRateLimitTest {
    @Test void simultaneousDecodersAreAllowedButBurstsAreBounded() {
        var limit=new SpeechRateLimit(); var id=UUID.randomUUID();
        for(int i=0;i<4;i++) assertTrue(limit.allow(id,0));
        assertFalse(limit.allow(id,0));
    }
    @Test void sustainedRateRefillsWithoutUnlimitedIdleCredit() {
        var limit=new SpeechRateLimit(); var id=UUID.randomUUID();
        for(int i=0;i<4;i++) limit.allow(id,0);
        assertFalse(limit.allow(id,100_000_000));
        assertTrue(limit.allow(id,200_000_000));
        assertFalse(limit.allow(id,200_000_000));
        for(int i=0;i<4;i++) assertTrue(limit.allow(id,10_000_000_000L));
        assertFalse(limit.allow(id,10_000_000_000L));
    }
    @Test void onePlayersSpeechDoesNotThrottleAnother() {
        var limit=new SpeechRateLimit(); var first=UUID.randomUUID(); var second=UUID.randomUUID();
        for(int i=0;i<4;i++) limit.allow(first,0);
        assertFalse(limit.allow(first,0)); assertTrue(limit.allow(second,0));
        limit.clear(); assertTrue(limit.allow(first,0));
    }
}
