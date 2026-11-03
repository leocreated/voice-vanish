package dev.leo.voicevanish.test;

import com.google.gson.*;
import dev.leo.voicevanish.VoiceVanish;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.block.Blocks;
import net.minecraft.client.gui.screen.TitleScreen;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.math.BlockPos;
import java.nio.file.*;

/** Replay round trip without Fabric client-gametest server-factory interception. */
public final class ReplayStandalone implements ClientModInitializer {
    private int step;
    private long deadline;
    private Class<?> flashback;
    private Object replayServer;
    private BlockPos pos;
    private Path replay, result;
    @Override public void onInitializeClient() {
        String property = System.getProperty("voicevanish.replayProbe");
        if (property == null || property.isBlank()) return;
        try {
            JsonObject fixture = JsonParser.parseString(Files.readString(Path.of(property))).getAsJsonObject();
            replay = Path.of(fixture.get("replay").getAsString()); result = Path.of(property + ".result.json");
            Files.deleteIfExists(result);
            pos = new BlockPos(fixture.get("x").getAsInt(), fixture.get("y").getAsInt(), fixture.get("z").getAsInt());
            flashback = Class.forName("com.moulberry.flashback.Flashback");
            deadline = System.nanoTime() + 120_000_000_000L;
        } catch (Exception ex) { throw new RuntimeException(ex); }
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (step == 4) return;
            try {
                if (System.nanoTime() > deadline) throw new IllegalStateException("Replay probe timed out at step " + step
                        + "; server=" + (client.getServer() == null ? "none" : client.getServer().getClass().getName())
                        + "; screen=" + (client.currentScreen == null ? "none" : client.currentScreen.getClass().getName()));
                if (step == 0 && client.currentScreen instanceof TitleScreen && client.getOverlay() == null) {
                    flashback.getMethod("openReplayWorld", Path.class).invoke(null, replay); step = 1;
                } else if (step == 1 && client.world != null && (boolean) flashback.getMethod("isInReplay").invoke(null)) {
                    replayServer = flashback.getMethod("getReplayServer").invoke(null);
                    if (VoiceVanish.controller((MinecraftServer) replayServer) != null) throw new IllegalStateException("Gameplay controller attached to replay");
                    replayServer.getClass().getField("replayPaused").setBoolean(replayServer, true);
                    replayServer.getClass().getMethod("goToReplayTick", int.class).invoke(replayServer, 0); step = 2;
                } else if (step == 2 && client.world.getBlockState(pos).isOf(Blocks.OAK_PLANKS)) {
                    int ticks = (int) replayServer.getClass().getMethod("getTotalReplayTicks").invoke(replayServer);
                    replayServer.getClass().getMethod("goToReplayTick", int.class).invoke(replayServer, ticks - 1); step = 3;
                } else if (step == 3 && client.world.getBlockState(pos).isAir()) {
                    Files.writeString(result, "{\"passed\":true,\"checks\":[\"oak_planks_at_start\",\"air_at_end\",\"no_gameplay_controller_in_replay\"]}");
                    VoiceVanish.LOG.info("VOICE_VANISH_REPLAY_PROBE_PASSED"); step = 4; client.scheduleStop();
                }
            } catch (Exception ex) {
                VoiceVanish.LOG.error("Replay probe failed", ex);
                try { JsonObject failure = new JsonObject(); failure.addProperty("passed", false); failure.addProperty("error", ex.toString()); Files.writeString(result, failure.toString()); }
                catch (Exception ignored) { }
                step = 4; client.scheduleStop();
            }
        });
    }
}
