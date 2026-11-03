package dev.leo.voicevanish.client;

import com.google.gson.*;
import com.mojang.brigadier.arguments.*;
import dev.leo.voicevanish.*;
import dev.leo.voicevanish.core.BlockNames;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.*;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.networking.v1.*;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;
import java.lang.reflect.Method;
import static net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.*;

public final class VoiceVanishClient implements ClientModInitializer {
    private MicConfig config;
    private Microphone microphone;
    private boolean enabled, test, armed;
    private long generation;
    private Method flashbackIsInReplay;
    private boolean flashbackGuardFailed;
    private BlockNames names;
    private final java.util.LinkedHashSet<String> testMatches = new java.util.LinkedHashSet<>();
    private void reply(String text) { var player = MinecraftClient.getInstance().player; if (player != null) player.sendMessage(Text.literal("Voice Vanish: " + text), false); }
    private void stop() { generation++; testMatches.clear(); if (microphone != null) { microphone.close(); microphone = null; } }
    private boolean replay() {
        if (flashbackGuardFailed) return true;
        try { return flashbackIsInReplay != null && (boolean) flashbackIsInReplay.invoke(null); }
        catch (ReflectiveOperationException ex) { flashbackGuardFailed = true; stop(); VoiceVanish.LOG.error("Flashback replay guard failed; microphone disabled", ex); return true; }
    }
    @Override public void onInitializeClient() {
        names = BlockCatalog.create();
        try { config = MicConfig.load(); } catch (Exception ex) { VoiceVanish.LOG.error("Microphone config invalid", ex); }
        if (FabricLoader.getInstance().isModLoaded("flashback")) {
            try { flashbackIsInReplay = Class.forName("com.moulberry.flashback.Flashback").getMethod("isInReplay"); }
            catch (ReflectiveOperationException ex) { flashbackGuardFailed = true; VoiceVanish.LOG.error("Cannot establish Flashback replay guard; microphone disabled", ex); }
        }
        KeyBinding toggle = KeyBindingHelper.registerKeyBinding(new KeyBinding("key.voicevanish.microphone", InputUtil.Type.KEYSYM,
                GLFW.GLFW_KEY_F8, "category.voicevanish"));
        ClientPlayNetworking.registerGlobalReceiver(Packets.State.ID, (payload, context) -> context.client().execute(() -> {
            armed = payload.armed(); if (!armed && !test) stop();
        }));
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> { armed = false; test = false; enabled = false; stop(); });
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (toggle.wasPressed()) { enabled = !enabled; test = false; stop(); reply(enabled ? "Microphone enabled; host must arm with /vv start." : "Microphone off."); }
            boolean shouldListen = config != null && client.player != null && client.getNetworkHandler() != null
                    && enabled && (test || armed) && !replay() && ClientPlayNetworking.canSend(Packets.Speech.ID);
            if (!shouldListen) { if (microphone != null) stop(); return; }
            if (microphone == null) {
                long epoch = ++generation;
                var connection = client.getNetworkHandler();
                microphone = new Microphone(config, names, update -> client.execute(() -> {
                    if (epoch != generation || client.getNetworkHandler() != connection || !enabled || replay()) return;
                    if (test) {
                        testMatches.addAll(update.matches());
                        reply("TEST " + update.source() + ": [" + update.text() + "] | new: " + update.matches()
                                + " | matched this test: " + testMatches + " (no bans sent)");
                    } else if (armed && !update.matches().isEmpty() && ClientPlayNetworking.canSend(Packets.Speech.ID)) {
                        String text = String.join(" and ", update.matches());
                        if (text.length() <= 512) ClientPlayNetworking.send(new Packets.Speech(text));
                    }
                }));
            }
        });
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, access) -> dispatcher.register(literal("vvmic")
                .then(literal("on").executes(ctx -> { if (config == null) { reply("Config invalid; inspect latest.log."); return 0; } enabled = true; test = false; stop(); reply("Microphone enabled; waiting for host /vv start if paused."); return 1; }))
                .then(literal("off").executes(ctx -> { enabled = false; test = false; stop(); reply("Microphone off."); return 1; }))
                .then(literal("test").executes(ctx -> { enabled = true; test = true; stop(); reply("Microphone test mode. Speak normally. No bans will be sent. Use /vvmic on for live mode."); return 1; }))
                .then(literal("status").executes(ctx -> { reply("enabled=" + enabled + ", mode=" + (test ? "TEST" : "LIVE") + ", host=" + (armed ? "armed" : "paused")
                        + ", " + (microphone == null ? "not capturing" : microphone.status) + (flashbackGuardFailed ? "; replay guard FAILED" : "")); return 1; }))
                .then(literal("devices").executes(ctx -> { var devices = Microphone.devices(); reply("-1: Windows default input"); for (int i = 0; i < devices.size(); i++) reply(i + ": " + devices.get(i).getName()); return 1; }))
                .then(literal("device").then(argument("index", IntegerArgumentType.integer(-1)).executes(ctx -> {
                    if (config == null) return 0;
                    int index = IntegerArgumentType.getInteger(ctx, "index"); var devices = Microphone.devices();
                    if (index >= devices.size()) { reply("Unknown device. Run /vvmic devices."); return 0; }
                    config.deviceName = index == -1 ? "" : devices.get(index).getName();
                    try { config.save(); stop(); reply("Microphone selection saved."); return 1; }
                    catch (Exception ex) { reply(ex.getMessage()); return 0; }
                })))
                .then(literal("confidence").then(argument("threshold", DoubleArgumentType.doubleArg(0.0, 1.0)).executes(ctx -> {
                    reply("The streaming recognizer uses stable whole names. The old Vosk confidence setting no longer applies."); return 1;
                })))
                .executes(ctx -> { reply("/vvmic on | off | test | status | devices | device <index>. F8 toggles mic."); return 1; })
        ));
    }
}
