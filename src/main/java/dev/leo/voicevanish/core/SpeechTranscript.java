package dev.leo.voicevanish.core;

import com.google.gson.*;
import java.util.StringJoiner;
import java.util.ArrayList;
import java.util.List;

/** Preserve low-confidence gaps so unrelated words cannot form a block name. */
public final class SpeechTranscript {
    private SpeechTranscript() {}
    public static String safeText(String json, double minimumConfidence) {
        return safeText(json, minimumConfidence, null);
    }
    public static String safeText(String json, double minimumConfidence, BlockNames names) {
        JsonObject result = JsonParser.parseString(json).getAsJsonObject();
        if (!result.has("result")) return "";
        List<String> tokens = new ArrayList<>(); List<Boolean> confidence = new ArrayList<>();
        for (JsonElement element : result.getAsJsonArray("result")) {
            JsonObject word = element.getAsJsonObject();
            boolean accepted = word.has("conf") && word.get("conf").getAsDouble() >= minimumConfidence;
            for (String token : BlockNames.words(word.has("word") ? word.get("word").getAsString() : "unrecognized")) {
                tokens.add(token); confidence.add(accepted);
            }
        }
        if (names != null) for (var hit : names.find(String.join(" ", tokens))) {
            boolean accepted = true;
            for (int i = hit.firstWord(); i < hit.firstWord() + hit.wordCount(); i++) accepted &= confidence.get(i);
            if (!accepted) for (int i = hit.firstWord(); i < hit.firstWord() + hit.wordCount(); i++) confidence.set(i, false);
        }
        StringJoiner text = new StringJoiner(" ");
        for (int i = 0; i < tokens.size(); i++) text.add(confidence.get(i) ? tokens.get(i) : "unrecognized");
        String value = text.toString();
        if (value.length() <= 512) return value;
        return value.substring(0, value.lastIndexOf(' ', 512));
    }
}
