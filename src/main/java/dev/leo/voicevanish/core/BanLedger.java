package dev.leo.voicevanish.core;

import com.google.gson.*;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.Instant;
import java.util.*;

/** The ledger is committed before the first deletion. A bad ledger fails closed. */
public final class BanLedger {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private final Path file;
    private final List<Event> events = new ArrayList<>();
    public static final class Event {
        public String id, blockId, actor, time;
        public boolean undone;
        public long removed;
        Event(String blockId, String actor) {
            this.id = UUID.randomUUID().toString(); this.blockId = blockId;
            this.actor = actor; this.time = Instant.now().toString();
        }
    }
    private record Data(int schema, List<Event> events) {}
    public BanLedger(Path root) throws IOException {
        this.file = root.resolve("bans.json");
        if (Files.exists(file)) {
            try {
                Data data = GSON.fromJson(Files.readString(file), Data.class);
                if (data == null || data.schema != 1 || data.events == null) throw new IllegalArgumentException("Unknown ledger format");
                Set<String> ids = new HashSet<>(), active = new HashSet<>();
                for (Event event : data.events) {
                    UUID.fromString(event.id);
                    if (!ids.add(event.id) || event.blockId == null || event.actor == null
                            || !event.blockId.matches("minecraft:[a-z0-9_]+")
                            || BlockNames.PROTECTED.contains(event.blockId)
                            || (!event.undone && !active.add(event.blockId))) throw new IllegalArgumentException("Invalid ban entry");
                }
                events.addAll(data.events);
            } catch (RuntimeException ex) { throw new IOException("Cannot read bans.json. No world changes will be made.", ex); }
        }
    }
    public List<Event> events() { return Collections.unmodifiableList(events); }
    public Map<String, Event> active() {
        Map<String, Event> result = new LinkedHashMap<>();
        for (Event event : events) if (!event.undone) result.put(event.blockId, event);
        return result;
    }
    public Event ban(String id, String actor) throws IOException {
        if (BlockNames.PROTECTED.contains(id)) throw new IllegalArgumentException("That block is protected");
        if (active().containsKey(id)) return null;
        Event event = new Event(id, actor); events.add(event);
        try { save(); } catch (IOException ex) { events.remove(event); throw ex; }
        return event;
    }
    public Event undoLast() throws IOException {
        for (int i = events.size() - 1; i >= 0; i--) {
            Event event = events.get(i);
            if (!event.undone) {
                event.undone = true;
                try { save(); } catch (IOException ex) { event.undone = false; throw ex; }
                return event;
            }
        }
        return null;
    }
    public void save() throws IOException {
        Files.createDirectories(file.getParent());
        Path temp = file.resolveSibling("bans.json.tmp");
        Files.writeString(temp, GSON.toJson(new Data(1, events)), StandardCharsets.UTF_8);
        try { Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE); }
        catch (AtomicMoveNotSupportedException ex) { Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING); }
    }
}
