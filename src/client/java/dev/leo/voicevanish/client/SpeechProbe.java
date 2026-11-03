package dev.leo.voicevanish.client;

import com.google.gson.*;
import dev.leo.voicevanish.core.*;
import javax.sound.sampled.*;
import java.io.*;
import java.nio.file.*;
import java.util.*;

/** Runs the production streaming path on synthetic PCM, without capturing a microphone. */
public final class SpeechProbe {
    public static void main(String[] args) throws Exception {
        Map<String,String> aliases = new LinkedHashMap<>();
        try (var reader = new InputStreamReader(SpeechProbe.class.getResourceAsStream("/voicevanish-names-en.json"))) {
            JsonParser.parseReader(reader).getAsJsonObject().asMap().forEach((name,id) -> aliases.put(name,id.getAsString()));
        }
        SpokenAliases.addTo(aliases); BlockNames names = new BlockNames(aliases);
        List<String> matched = Collections.synchronizedList(new ArrayList<>());
        var failure = new java.util.concurrent.atomic.AtomicReference<Throwable>();
        long start = System.nanoTime();
        try (var wav = AudioSystem.getAudioInputStream(new File(args[1]));
             var speech = new HybridSpeech(Path.of(args[0]), Path.of(args[3]), names, update -> {
                 System.out.println("SOURCE=" + update.source() + " SPEECH=" + update.text() + " MATCHED=" + update.matches() + " FINAL=" + update.endpoint());
                 for (String phrase : update.matches()) matched.addAll(names.find(phrase).stream().map(BlockNames.Hit::blockId).toList());
             }, failure::set)) {
            if (wav.getFormat().getChannels() != 1 || wav.getFormat().getSampleSizeInBits() != 16 || wav.getFormat().isBigEndian())
                throw new IllegalArgumentException("Use mono little-endian 16-bit PCM WAV");
            int rate = (int) wav.getFormat().getSampleRate(); byte[] bytes = new byte[rate / 5]; int count;
            while ((count = wav.read(bytes)) != -1) speech.accept(bytes, count, rate);
            Arrays.fill(bytes, (byte) 0);
            for (int i=0; i<25; i++) speech.accept(bytes, bytes.length, rate);
            speech.awaitIdle();
            if (failure.get() != null) throw new IllegalStateException("Speech worker failed", failure.get());
        }
        System.out.println("MATCHED_IDS=" + matched + " ELAPSED_SECONDS=" + (System.nanoTime()-start)/1e9);
        List<String> expected = args.length <= 2 || args[2].isBlank() ? List.of() : Arrays.asList(args[2].split(","));
        if (!matched.equals(expected)) throw new IllegalStateException("Expected exact block sequence " + expected + "; got " + matched);
    }
}
