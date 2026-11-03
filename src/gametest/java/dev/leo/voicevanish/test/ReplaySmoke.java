package dev.leo.voicevanish.test;

import dev.leo.voicevanish.VoiceVanish;
import net.fabricmc.fabric.api.client.gametest.v1.*;
import net.fabricmc.fabric.api.client.gametest.v1.context.*;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.block.Blocks;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.math.BlockPos;
import java.nio.file.*;
import java.util.zip.ZipFile;

/** Uses Flashback's own record/save/open/seek controls in the disposable test client. */
public final class ReplaySmoke implements FabricClientGameTest {
    private static boolean finishedZip(Path file) {
        try (ZipFile zip = new ZipFile(file.toFile())) { return zip.size() > 0; }
        catch (Exception ignored) { return false; }
    }
    @Override public void runTest(ClientGameTestContext context) {
        if (!FabricLoader.getInstance().isModLoaded("flashback")) return;
        try { runReplayTest(context); }
        catch (Exception ex) { throw new RuntimeException(ex); }
    }
    private void runReplayTest(ClientGameTestContext context) throws Exception {
        Class<?> flashback = Class.forName("com.moulberry.flashback.Flashback");
        Path folder = (Path) flashback.getMethod("getReplayFolder").invoke(null);
        Files.createDirectories(folder);
        java.util.Set<Path> before;
        try (var files = Files.list(folder)) { before = files.collect(java.util.stream.Collectors.toSet()); }
        var session = context.worldBuilder().create();
        BlockPos pos = context.computeOnClient(client -> client.player.getBlockPos().add(2, 2, 0));
        try {
            session.getServer().runOnServer(server -> server.getOverworld().setBlockState(pos, Blocks.OAK_PLANKS.getDefaultState(), 2));
            context.waitFor(client -> client.world != null && client.world.getBlockState(pos).isOf(Blocks.OAK_PLANKS));
            context.runOnClient(client -> {
                Object config = flashback.getMethod("getConfig").invoke(null);
                Object controls = config.getClass().getField("recordingControls").get(config);
                controls.getClass().getField("quicksave").setBoolean(controls, true);
                flashback.getMethod("startRecordingReplay").invoke(null);
                if (flashback.getField("RECORDER").get(null) == null) throw new AssertionError("Flashback did not start recording");
            });
            context.waitTicks(20);
            session.getServer().runOnServer(server -> VoiceVanish.controller(server).ban("minecraft:oak_planks", "replay-smoke"));
            context.waitFor(client -> client.world.getBlockState(pos).isAir());
            context.waitTicks(20);
            context.runOnClient(client -> flashback.getMethod("finishRecordingReplay").invoke(null));
            context.waitFor(client -> {
                try (var files = Files.list(folder)) { return files.anyMatch(path -> !before.contains(path) && path.toString().endsWith(".zip") && finishedZip(path)); }
                catch (Exception ex) { return false; }
            }, 600);
        } finally { session.close(); }
        Path replay;
        try (var files = Files.list(folder)) { replay = files.filter(path -> !before.contains(path) && path.toString().endsWith(".zip") && finishedZip(path)).findFirst().orElseThrow(); }
        var fixture = new com.google.gson.JsonObject();
        fixture.addProperty("replay", replay.toAbsolutePath().toString());
        fixture.addProperty("x", pos.getX()); fixture.addProperty("y", pos.getY()); fixture.addProperty("z", pos.getZ());
        Files.writeString(replay.resolveSibling(replay.getFileName() + ".probe.json"), fixture.toString());
        VoiceVanish.LOG.info("VOICE_VANISH_RECORDING_SMOKE_PASSED: saved {}", replay.getFileName());
        // ReplayStandalone runs playback in a normal launch. Fabric client
        // gametests replace server creation, bypassing Flashback's replay factory.
    }
}
