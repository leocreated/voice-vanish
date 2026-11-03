package dev.leo.voicevanish.core;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import java.util.Map;

class SpeechTranscriptTest {
    @Test void uncertainLongNameCannotAccidentallyBanItsShortSuffix() {
        var names = new BlockNames(Map.of("dirt", "minecraft:dirt", "coarse dirt", "minecraft:coarse_dirt"));
        String text = SpeechTranscript.safeText("{\"result\":[{\"word\":\"coarse\",\"conf\":0.1},{\"word\":\"dirt\",\"conf\":1}]}", 0.8, names);
        assertTrue(names.find(text).isEmpty());
    }
    @Test void lowConfidenceWordsCannotJoinIntoNewPhrases() {
        assertEquals("oak unrecognized planks", SpeechTranscript.safeText("{\"result\":[{\"word\":\"oak\",\"conf\":1},{\"word\":\"something\",\"conf\":0.1},{\"word\":\"planks\",\"conf\":1}]}", 0.8));
    }
    @Test void missingConfidenceDoesNotTriggerBans() {
        assertEquals("unrecognized", SpeechTranscript.safeText("{\"result\":[{\"word\":\"dirt\"}]}", 0.8));
        assertEquals("", SpeechTranscript.safeText("{\"partial\":\"dirt\"}", 0.8));
    }
}
