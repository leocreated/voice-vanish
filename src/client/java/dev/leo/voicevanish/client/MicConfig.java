package dev.leo.voicevanish.client;

import com.google.gson.*;
import net.fabricmc.loader.api.FabricLoader;
import java.nio.file.*;
import java.io.IOException;

final class MicConfig {
    String modelPath = "config/voicevanish/models/vosk-model-en-us-0.22-lgraph";
    String streamingModelPath = "config/voicevanish/models/zipformer-en-2023-06-26";
    String verificationModelPath = "config/voicevanish/models/whisper-small.en/ggml-small.en-q5_1.bin";
    String deviceName = "";
    double minimumConfidence = 0.8;
    private static Path path() { return FabricLoader.getInstance().getConfigDir().resolve("voicevanish/client.json"); }
    static MicConfig load() throws IOException {
        Path path = path();
        MicConfig config = Files.exists(path) ? new Gson().fromJson(Files.readString(path), MicConfig.class) : new MicConfig();
        if (config == null || config.modelPath == null || config.streamingModelPath == null || config.verificationModelPath == null || config.deviceName == null
                || !Double.isFinite(config.minimumConfidence) || config.minimumConfidence < 0 || config.minimumConfidence > 1)
            throw new IOException("Invalid voicevanish/client.json");
        if (config.verificationModelPath.equals("config/voicevanish/models/whisper-base.en/ggml-base.en.bin"))
            config.verificationModelPath = "config/voicevanish/models/whisper-small.en/ggml-small.en-q5_1.bin";
        config.save(); return config;
    }
    void save() throws IOException {
        Files.createDirectories(path().getParent());
        Files.writeString(path(), new GsonBuilder().setPrettyPrinting().create().toJson(this));
    }
    Path model() { return FabricLoader.getInstance().getGameDir().resolve(streamingModelPath).normalize(); }
    Path checkModel() { return FabricLoader.getInstance().getGameDir().resolve(verificationModelPath).normalize(); }
}
