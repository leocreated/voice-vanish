package dev.leo.voicevanish.test;

import dev.leo.voicevanish.client.SpeechProbe;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;

/** Exercise both actual native decoders inside Fabric, without microphone capture. */
public final class SpeechGameSmoke implements FabricClientGameTest {
    @Override public void runTest(ClientGameTestContext context) {
        String model = System.getProperty("voicevanish.testSpeechModel");
        if (model == null) return;
        try {
            SpeechProbe.main(new String[]{model, System.getProperty("voicevanish.testSpeechWav"),
                    System.getProperty("voicevanish.testSpeechExpected", "minecraft:andesite,minecraft:granite,minecraft:diorite"), System.getProperty("voicevanish.testCheckModel")});
        } catch (Exception ex) { throw new RuntimeException(ex); }
    }
}
