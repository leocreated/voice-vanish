package dev.leo.voicevanish.core;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class WhisperTuningTest {
    @Test void encoderContextFitsTheCaptureWindowWithPaddingAndRespectsModelLimit() {
        for (int seconds : new int[]{1,2,5,10,12}) {
            int samples=seconds*16000+8000;
            int frames=WhisperTuning.audioContext(samples);
            assertTrue(frames*320 >= samples+80000, "Payload plus five seconds of padding must fit");
            assertTrue(frames>=500 && frames<=1500);
        }
        assertEquals(1500,WhisperTuning.audioContext(480000));
    }
    @Test void backgroundDecoderLeavesCpuCapacityForTheGame() {
        for(int available : new int[]{1,2,4,8,16,32}) {
            int threads=WhisperTuning.threads(available);
            assertTrue(threads>=1 && threads<=4);
            assertTrue(threads<=Math.max(1,available/2));
        }
    }
}
