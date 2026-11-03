package dev.leo.voicevanish.core;

import com.google.gson.*;
import java.io.*;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

/** Append-before-delete, per-event/per-chunk snapshots. Never loads a world chunk. */
public final class JournalStore {
    private static final Gson GSON = new Gson();
    private final Path root;
    public record Snapshot(long pos, JsonObject state, String blockEntity) {}
    public JournalStore(Path root) { this.root = root.resolve("journal"); }
    private Path file(String eventId, String dimension, int x, int z) {
        UUID.fromString(eventId);
        String dim = Base64.getUrlEncoder().withoutPadding().encodeToString(dimension.getBytes(StandardCharsets.UTF_8));
        return root.resolve(eventId).resolve(dim).resolve(x + "_" + z + ".jsonl");
    }
    public Path pending(String eventId, String dimension, int x, int z) {
        Path file = file(eventId, dimension, x, z);
        return Files.exists(file) && !Files.exists(done(file)) ? file : null;
    }
    private static Path done(Path file) { return file.resolveSibling(file.getFileName() + ".restored"); }
    public void append(String eventId, String dimension, int x, int z, List<Snapshot> snapshots) throws IOException {
        if (snapshots.isEmpty()) return;
        Path file = file(eventId, dimension, x, z);
        Files.createDirectories(file.getParent());
        StringBuilder lines = new StringBuilder();
        for (Snapshot snapshot : snapshots) lines.append(GSON.toJson(snapshot)).append('\n');
        try (FileChannel channel = FileChannel.open(file, StandardOpenOption.CREATE, StandardOpenOption.WRITE, StandardOpenOption.APPEND)) {
            ByteBuffer bytes = StandardCharsets.UTF_8.encode(lines.toString());
            while (bytes.hasRemaining()) channel.write(bytes);
            channel.force(false);
        }
    }
    public Cursor open(Path file) throws IOException { return new Cursor(file); }
    public final class Cursor implements AutoCloseable {
        private final Path file;
        private final BufferedReader reader;
        private boolean finished;
        Cursor(Path file) throws IOException { this.file = file; reader = Files.newBufferedReader(file, StandardCharsets.UTF_8); }
        public Snapshot next() throws IOException {
            String line = reader.readLine();
            if (line == null) return null;
            try { return GSON.fromJson(line, Snapshot.class); }
            catch (RuntimeException ex) { throw new IOException("Damaged undo journal: " + file, ex); }
        }
        public void complete() throws IOException { if (!finished) { Files.writeString(done(file), "restored\n"); finished = true; } }
        @Override public void close() throws IOException { reader.close(); }
    }
}
