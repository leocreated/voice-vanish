package dev.leo.voicevanish;

import com.google.gson.*;
import dev.leo.voicevanish.core.BlockNames;
import dev.leo.voicevanish.core.SpokenAliases;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

public final class BlockCatalog {
    private BlockCatalog() {}
    public static BlockNames create() {
        Map<String, String> aliases = new LinkedHashMap<>();
        for (Identifier id : Registries.BLOCK.getIds()) if (id.getNamespace().equals("minecraft"))
            aliases.put(id.getPath().replace('_', ' '), id.toString());
        try (InputStream in = BlockCatalog.class.getResourceAsStream("/voicevanish-names-en.json")) {
            if (in != null) JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject().asMap()
                    .forEach((name, value) -> {
                        Identifier id = Identifier.tryParse(value.getAsString());
                        if (id != null && Registries.BLOCK.containsId(id)) aliases.put(name, id.toString());
                    });
        } catch (IOException ex) { throw new UncheckedIOException(ex); }
        SpokenAliases.addTo(aliases);
        return new BlockNames(aliases);
    }
}
