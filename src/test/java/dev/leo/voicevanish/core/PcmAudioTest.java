package dev.leo.voicevanish.core;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class PcmAudioTest {
    @Test void silenceAndClicksDoNotScheduleARecognitionPass() {
        byte[] pcm = new byte[16000 * 2];
        assertFalse(PcmAudio.hasVoice(pcm, 16000));
        pcm[100] = (byte) 255; pcm[101] = 127;
        assertFalse(PcmAudio.hasVoice(pcm, 16000));
        for (int i=0; i<4000; i+=2) { pcm[i]=0; pcm[i+1]=32; }
        assertTrue(PcmAudio.hasVoice(pcm, 16000));
    }
    @Test void decodesSignedPcmAndPreservesHalfASecondOfPreRoll() {
        var samples = PcmAudio.at16000(new byte[]{0,(byte)128,(byte)255,127},16000);
        assertEquals(8002, samples.length);
        assertEquals(0, samples[7999]); assertEquals(-1f, samples[8000]);
        assertEquals(32767f/32768f, samples[8001]);
    }
    @Test void resamplesGoXlr48kWithoutChangingDurationOrAmplitude() {
        byte[] pcm = new byte[48000 * 2];
        for (int i=0; i<pcm.length; i+=2) pcm[i+1]=32;
        var samples = PcmAudio.at16000(pcm,48000);
        assertEquals(24000,samples.length);
        assertEquals(.25f,samples[8000]); assertEquals(.25f,samples[23999]);
    }
}
