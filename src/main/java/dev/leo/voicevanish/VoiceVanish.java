package dev.leo.voicevanish;

import com.mojang.brigadier.arguments.StringArgumentType;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.*;
import net.fabricmc.fabric.api.networking.v1.*;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.text.Text;
import org.slf4j.*;
import java.util.*;
import static net.minecraft.server.command.CommandManager.*;

public final class VoiceVanish implements ModInitializer {
    public static final Logger LOG = LoggerFactory.getLogger("voicevanish");
    private static final Map<MinecraftServer, WorldBans> SERVERS = new IdentityHashMap<>();
    public static WorldBans controller(MinecraftServer server) { return SERVERS.get(server); }
    private static boolean realServer(MinecraftServer server) {
        // Flashback owns its replay world; never run gameplay mutations in it.
        return !server.getClass().getName().startsWith("com.moulberry.flashback.");
    }
    @Override public void onInitialize() {
        PayloadTypeRegistry.playC2S().register(Packets.Speech.ID, Packets.Speech.CODEC);
        PayloadTypeRegistry.playS2C().register(Packets.State.ID, Packets.State.CODEC);
        ServerLifecycleEvents.SERVER_STARTING.register(server -> {
            if (!realServer(server)) return;
            try { SERVERS.put(server, new WorldBans(server)); }
            catch (Exception ex) { LOG.error("Voice Vanish disabled: could not load world ledger", ex); }
        });
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
            WorldBans controller = SERVERS.remove(server);
            if (controller != null) try { controller.close(); } catch (Exception ex) { LOG.error("Could not close world ledger", ex); }
        });
        ServerTickEvents.END_SERVER_TICK.register(server -> { var c = controller(server); if (c != null) c.tick(); });
        ServerChunkEvents.CHUNK_LOAD.register((world, chunk) -> { var c = controller(world.getServer()); if (c != null) c.loaded(world, chunk); });
        ServerChunkEvents.CHUNK_UNLOAD.register((world, chunk) -> { var c = controller(world.getServer()); if (c != null) c.unloaded(world, chunk); });
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> { var c = controller(server); if (c != null) c.sendState(handler.player); });
        ServerPlayNetworking.registerGlobalReceiver(Packets.Speech.ID, (payload, context) -> context.server().execute(() -> {
            var c = controller(context.server()); if (c != null) c.speech(context.player(), payload.text());
        }));
        CommandRegistrationCallback.EVENT.register((dispatcher, registries, environment) -> {
            var root = literal("voicevanish").requires(VoiceVanish::host)
                    .then(literal("start").executes(ctx -> execute(ctx.getSource(), c -> { c.setArmed(true); return "Speech armed. Each player must enable their microphone locally with /vvmic on."; })))
                    .then(literal("pause").executes(ctx -> execute(ctx.getSource(), c -> { c.setArmed(false); return "Speech paused. Existing bans remain enforced."; })))
                    .then(literal("status").executes(ctx -> execute(ctx.getSource(), WorldBans::status)))
                    .then(literal("ban").then(argument("block", StringArgumentType.word()).executes(ctx -> execute(ctx.getSource(), c -> {
                        String id = StringArgumentType.getString(ctx, "block"); if (!id.contains(":")) id = "minecraft:" + id;
                        return c.ban(id, ctx.getSource().getName()) ? "Banned " + id : "Already banned: " + id;
                    }))))
                    .then(literal("undo").executes(ctx -> execute(ctx.getSource(), WorldBans::undo)))
                    .then(literal("reset").then(literal("confirm").executes(ctx -> execute(ctx.getSource(), WorldBans::reset))))
                    .executes(ctx -> execute(ctx.getSource(), WorldBans::status));
            dispatcher.register(root);
            dispatcher.register(literal("vv").requires(VoiceVanish::host).redirect(dispatcher.getRoot().getChild("voicevanish")));
        });
    }
    private static boolean host(ServerCommandSource source) {
        return source.getEntity() == null || (!source.getServer().isDedicated() && source.getPlayer() != null
                && source.getServer().isHost(source.getPlayer().getGameProfile()))
                || (source.getServer().isDedicated() && source.hasPermissionLevel(4));
    }
    @FunctionalInterface private interface Action { String run(WorldBans controller) throws Exception; }
    private static int execute(ServerCommandSource source, Action action) {
        var controller = controller(source.getServer());
        if (controller == null) { source.sendError(Text.literal("Voice Vanish is unavailable in this world. Check latest.log.")); return 0; }
        try { String message = action.run(controller); source.sendFeedback(() -> Text.literal(message), false); return 1; }
        catch (Exception ex) { LOG.warn("Voice Vanish command failed", ex); source.sendError(Text.literal("Voice Vanish: " + ex.getMessage())); return 0; }
    }
}
