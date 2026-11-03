package dev.leo.voicevanish;

import com.google.gson.*;
import com.mojang.serialization.JsonOps;
import dev.leo.voicevanish.core.*;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.block.*;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.nbt.StringNbtReader;
import net.minecraft.registry.Registries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;
import net.minecraft.util.WorldSavePath;
import net.minecraft.util.math.*;
import net.minecraft.world.chunk.*;
import java.io.*;
import java.nio.file.Path;
import java.util.*;

/** Only the real host server owns mutations. Chunk jobs never force-load terrain. */
public final class WorldBans implements AutoCloseable {
    private final MinecraftServer server;
    private final BanLedger ledger;
    private final JournalStore journal;
    private final BlockNames names;
    private final Map<Block, BanLedger.Event> active = new HashMap<>();
    private final Map<Key, WorldChunk> loaded = new LinkedHashMap<>();
    private final ArrayDeque<Scan> scans = new ArrayDeque<>();
    private final Set<Key> queued = new HashSet<>();
    private final LinkedHashSet<Dirty> dirty = new LinkedHashSet<>();
    private final ArrayDeque<Restore> restores = new ArrayDeque<>();
    private final Set<String> restoringFiles = new HashSet<>();
    private final SpeechRateLimit lastSpeech = new SpeechRateLimit();
    private final Set<Dirty> collectedThisTick = new HashSet<>();
    private boolean armed, failed, mutating;
    private int revision, ticks;
    private long restored, occupied;
    private record Key(ServerWorld world, long pos) {}
    private record Dirty(Key key, long pos) {}
    private static final class Scan {
        final Key key; final WorldChunk chunk; int revision, section, offset;
        Scan(Key key, WorldChunk chunk, int revision) { this.key = key; this.chunk = chunk; this.revision = revision; }
    }
    private static final class Restore {
        final Key key; final WorldChunk chunk; final BanLedger.Event event;
        final Path path; JournalStore.Cursor cursor;
        Restore(Key key, WorldChunk chunk, BanLedger.Event event, Path path) { this.key = key; this.chunk = chunk; this.event = event; this.path = path; }
    }
    private record Removal(Key key, WorldChunk chunk, BanLedger.Event event, BlockPos pos, BlockState state, JournalStore.Snapshot snapshot) {}

    public WorldBans(MinecraftServer server) throws IOException {
        this.server = server;
        Path root = server.getSavePath(WorldSavePath.ROOT).resolve("voicevanish");
        ledger = new BanLedger(root); journal = new JournalStore(root);
        names = BlockCatalog.create(); refresh();
    }
    private void refresh() {
        active.clear();
        ledger.active().forEach((id, event) -> {
            Identifier identifier = Identifier.of(id);
            if (!Registries.BLOCK.containsId(identifier)) throw new IllegalStateException("Unknown block in ban ledger: " + id);
            active.put(Registries.BLOCK.get(identifier), event);
        });
        revision++;
    }
    public void loaded(ServerWorld world, WorldChunk chunk) {
        Key key = new Key(world, chunk.getPos().toLong());
        loaded.put(key, chunk); scheduleRestore(key, chunk); enqueue(key, chunk);
    }
    public void unloaded(ServerWorld world, WorldChunk chunk) { loaded.remove(new Key(world, chunk.getPos().toLong()), chunk); }
    private void enqueue(Key key, WorldChunk chunk) { if (!active.isEmpty() && queued.add(key)) scans.add(new Scan(key, chunk, revision)); }
    public void changed(ServerWorld world, WorldChunk chunk, BlockPos pos, BlockState state) {
        if (mutating || failed || !active.containsKey(state.getBlock())) return;
        Key key = new Key(world, chunk.getPos().toLong());
        // A newly loaded chunk can begin changing before CHUNK_LOAD is delivered.
        if (!loaded.containsKey(key)) return;
        if (dirty.size() < 20000) dirty.add(new Dirty(key, pos.asLong()));
        else { revision++; enqueue(key, chunk); }
    }
    public void speech(ServerPlayerEntity player, String text) {
        if (!armed || failed || text.length() > 512) return;
        if (!lastSpeech.allow(player.getUuid(), System.nanoTime())) return;
        for (var hit : names.find(text)) {
            try { ban(hit.blockId(), player.getGameProfile().getName()); }
            catch (Exception ex) { fail(ex); return; }
        }
    }
    public boolean ban(String id, String actor) throws IOException {
        Identifier identifier = Identifier.tryParse(id);
        if (identifier == null || !identifier.getNamespace().equals("minecraft") || !Registries.BLOCK.containsId(identifier))
            throw new IllegalArgumentException("Use a vanilla block ID such as minecraft:dirt");
        BanLedger.Event event = ledger.ban(identifier.toString(), actor);
        if (event == null) return false;
        refresh(); scans.clear(); queued.clear();
        loaded.entrySet().stream().sorted(Comparator.comparingDouble(entry -> distanceToPlayers(entry.getKey())))
                .forEach(entry -> enqueue(entry.getKey(), entry.getValue()));
        VoiceVanish.LOG.info("Ban {}: {} by {}", event.id, event.blockId, event.actor);
        return true;
    }
    public String undo() throws IOException {
        setArmed(false);
        BanLedger.Event event = ledger.undoLast();
        if (event == null) return "No active bans to undo.";
        refresh(); loaded.forEach(this::scheduleRestore);
        return "Unbanned " + event.blockId + ". Restoring loaded chunks; other chunks restore when visited. Speech paused.";
    }
    public String reset() throws IOException {
        setArmed(false);
        while (!ledger.active().isEmpty()) ledger.undoLast();
        refresh(); loaded.forEach(this::scheduleRestore);
        return "All bans cleared. Restoration queued; speech paused.";
    }
    private void scheduleRestore(Key key, WorldChunk chunk) {
        for (var event : ledger.events()) if (event.undone && !ledger.active().containsKey(event.blockId)) {
            Path path = journal.pending(event.id, dimension(key), chunk.getPos().x, chunk.getPos().z);
            if (path != null && restoringFiles.add(path.toString())) restores.add(new Restore(key, chunk, event, path));
        }
    }
    public void setArmed(boolean value) {
        if (value && failed) throw new IllegalStateException("World processor stopped after an error. Inspect latest.log and reload the world.");
        armed = value;
        for (var player : server.getPlayerManager().getPlayerList()) sendState(player);
    }
    public void sendState(ServerPlayerEntity player) {
        if (ServerPlayNetworking.canSend(player, Packets.State.ID)) ServerPlayNetworking.send(player, new Packets.State(armed && !failed));
    }
    public String status() {
        long removed = ledger.events().stream().mapToLong(e -> e.removed).sum();
        return "Speech " + (armed ? "ARMED" : "PAUSED") + (failed ? " [PROCESSOR ERROR]" : "")
                + "; bans=" + ledger.active().keySet() + "; loaded chunks=" + loaded.size()
                + "; scan queue=" + scans.size() + "; restore queue=" + restores.size()
                + "; removed=" + removed + "; restored=" + restored + "; occupied/skipped=" + occupied;
    }
    private static String dimension(Key key) { return key.world.getRegistryKey().getValue().toString(); }
    private double distanceToPlayers(Key key) {
        ChunkPos chunk = new ChunkPos(key.pos);
        return server.getPlayerManager().getPlayerList().stream().filter(player -> player.getServerWorld() == key.world)
                .mapToDouble(player -> { double dx = player.getX() - chunk.getCenterX(), dz = player.getZ() - chunk.getCenterZ(); return dx * dx + dz * dz; })
                .min().orElse(Double.MAX_VALUE);
    }
    public void tick() {
        if (failed) return;
        long deadline = System.nanoTime() + 8_000_000L;
        try {
            restoreTick(deadline);
            collectedThisTick.clear();
            List<Removal> batch = new ArrayList<>();
            int handled = 0;
            int initialDirty = dirty.size();
            while (!dirty.isEmpty() && handled++ < Math.min(512, initialDirty) && System.nanoTime() < deadline) {
                Iterator<Dirty> changed = dirty.iterator(); Dirty item = changed.next(); changed.remove();
                WorldChunk chunk = loaded.get(item.key);
                if (chunk != null) collect(item.key, chunk, BlockPos.fromLong(item.pos), batch);
            }
            int scanned = 0;
            while (!scans.isEmpty() && batch.size() < 4096 && scanned < 262144 && System.nanoTime() < deadline) {
                Scan job = scans.peek();
                if (loaded.get(job.key) != job.chunk) { scans.remove(); queued.remove(job.key); continue; }
                if (job.revision != revision) { job.section = 0; job.offset = 0; job.revision = revision; }
                ChunkSection[] sections = job.chunk.getSectionArray();
                if (job.section >= sections.length) { scans.remove(); queued.remove(job.key); continue; }
                ChunkSection section = sections[job.section];
                if (job.offset == 0 && (section.isEmpty() || !section.hasAny(s -> active.containsKey(s.getBlock())))) {
                    job.section++; scanned += 4096; continue;
                }
                int offset = job.offset++;
                int x = offset & 15, z = (offset >> 4) & 15, y = (offset >> 8) & 15;
                BlockState state = section.getBlockState(x, y, z);
                if (active.containsKey(state.getBlock())) {
                    BlockPos pos = new BlockPos(job.chunk.getPos().getStartX() + x,
                            job.chunk.sectionIndexToCoord(job.section) * 16 + y, job.chunk.getPos().getStartZ() + z);
                    collect(job.key, job.chunk, pos, batch);
                }
                scanned++;
                if (job.offset == 4096) { job.section++; job.offset = 0; }
            }
            apply(batch);
            if (++ticks % 200 == 0) ledger.save();
        } catch (Exception ex) { fail(ex); }
    }
    private void collect(Key key, WorldChunk chunk, BlockPos pos, List<Removal> batch) {
        BlockState state = chunk.getBlockState(pos);
        BanLedger.Event event = active.get(state.getBlock());
        if (event == null) return;
        if (!collectedThisTick.add(new Dirty(key, pos.asLong()))) return;
        // Never evict a container while a player has it open: its ScreenHandler
        // can otherwise retain writable inventory references and duplicate undo data.
        BlockEntity entity = chunk.getBlockEntity(pos);
        if (entity != null && server.getPlayerManager().getPlayerList().stream()
                .anyMatch(p -> p.currentScreenHandler != p.playerScreenHandler)) {
            dirty.add(new Dirty(key, pos.asLong())); return;
        }
        JsonObject encoded = BlockState.CODEC.encodeStart(JsonOps.INSTANCE, state).getOrThrow().getAsJsonObject();
        String nbt = entity == null ? null : entity.createNbtWithIdentifyingData(key.world.getRegistryManager()).toString();
        batch.add(new Removal(key, chunk, event, pos.toImmutable(), state, new JournalStore.Snapshot(pos.asLong(), encoded, nbt)));
    }
    private void apply(List<Removal> batch) throws IOException {
        // Each group is durably written before any of its blocks change.
        record Group(Key key, BanLedger.Event event) {}
        Map<Group, List<Removal>> groups = new LinkedHashMap<>();
        for (Removal removal : batch) groups.computeIfAbsent(new Group(removal.key, removal.event), k -> new ArrayList<>()).add(removal);
        for (var entry : groups.entrySet()) {
            Group group = entry.getKey(); List<Removal> list = entry.getValue();
            ChunkPos pos = list.getFirst().chunk.getPos();
            journal.append(group.event.id, dimension(group.key), pos.x, pos.z, list.stream().map(Removal::snapshot).toList());
            mutating = true;
            try {
                for (Removal removal : list) {
                    if (!removal.chunk.getBlockState(removal.pos).equals(removal.state)) continue;
                    // Preserve container data in the journal; suppress replacement scattering.
                    group.key.world.removeBlockEntity(removal.pos);
                    if (group.key.world.setBlockState(removal.pos, Blocks.AIR.getDefaultState(),
                            Block.NOTIFY_LISTENERS | Block.FORCE_STATE | Block.SKIP_DROPS)) group.event.removed++;
                }
            } finally { mutating = false; }
        }
    }
    private void restoreTick(long deadline) throws Exception {
        int count = 0;
        while (!restores.isEmpty() && count < 1024 && System.nanoTime() < deadline) {
            Restore job = restores.peek();
            if (loaded.get(job.key) != job.chunk || ledger.active().containsKey(job.event.blockId)) {
                finish(job, false); continue;
            }
            if (job.cursor == null) job.cursor = journal.open(job.path);
            JournalStore.Snapshot snapshot = job.cursor.next();
            if (snapshot == null) { finish(job, true); continue; }
            BlockPos pos = BlockPos.fromLong(snapshot.pos());
            if (new ChunkPos(pos).toLong() != job.key.pos) throw new IOException("Undo journal position belongs to another chunk");
            BlockState state = BlockState.CODEC.parse(JsonOps.INSTANCE, snapshot.state()).getOrThrow();
            // A correction never overwrites a player's subsequent build or flowing fluid.
            if (!job.chunk.getBlockState(pos).isAir()) { occupied++; count++; continue; }
            var nbt = snapshot.blockEntity() == null ? null : StringNbtReader.parse(snapshot.blockEntity());
            mutating = true;
            try {
                job.key.world.setBlockState(pos, state, Block.NOTIFY_LISTENERS | Block.FORCE_STATE | Block.SKIP_DROPS);
                if (nbt != null) {
                    BlockEntity entity = job.key.world.getBlockEntity(pos);
                    if (entity == null) throw new IOException("Missing block entity while restoring " + pos);
                    entity.read(nbt, job.key.world.getRegistryManager()); entity.markDirty();
                    job.key.world.updateListeners(pos, state, state, Block.NOTIFY_LISTENERS);
                }
                restored++;
            } finally { mutating = false; }
            count++;
        }
    }
    private void finish(Restore job, boolean complete) throws IOException {
        if (job.cursor != null) { if (complete) job.cursor.complete(); job.cursor.close(); }
        restores.remove(); restoringFiles.remove(job.path.toString());
    }
    private void fail(Exception ex) {
        failed = true; setArmed(false);
        VoiceVanish.LOG.error("Voice Vanish stopped world processing to preserve recovery data", ex);
        server.getPlayerManager().getPlayerList().stream().filter(p -> server.isHost(p.getGameProfile()))
                .forEach(p -> p.sendMessage(net.minecraft.text.Text.literal("Voice Vanish stopped after an error. Inspect latest.log before continuing."), false));
    }
    @Override public void close() throws IOException {
        for (Restore restore : restores) if (restore.cursor != null) restore.cursor.close();
        ledger.save(); loaded.clear(); scans.clear(); dirty.clear(); restores.clear(); lastSpeech.clear();
    }
}
