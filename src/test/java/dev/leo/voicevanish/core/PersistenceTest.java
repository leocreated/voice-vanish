package dev.leo.voicevanish.core;

import com.google.gson.JsonObject;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class PersistenceTest {
    @TempDir Path root;
    @Test void bansAndUndoSurviveReload() throws Exception {
        BanLedger ledger = new BanLedger(root);
        ledger.ban("minecraft:dirt", "host"); ledger.ban("minecraft:stone", "guest");
        assertNull(ledger.ban("minecraft:dirt", "guest"));
        assertEquals("minecraft:stone", ledger.undoLast().blockId);
        BanLedger reload = new BanLedger(root);
        assertEquals(Set.of("minecraft:dirt"), reload.active().keySet());
        assertEquals(2, reload.events().size());
    }
    @Test void damagedLedgerNeverSilentlyResetsBans() throws Exception {
        Files.writeString(root.resolve("bans.json"), "broken");
        assertThrows(java.io.IOException.class, () -> new BanLedger(root));
    }
    @Test void portalProtectionIsEnforcedOutsideSpeechMatching() throws Exception {
        BanLedger ledger = new BanLedger(root);
        assertThrows(IllegalArgumentException.class, () -> ledger.ban("minecraft:end_portal_frame", "host"));
    }
    @Test void journalKeepsStateAndContainerDataUntilChunkRestored() throws Exception {
        JournalStore journal = new JournalStore(root);
        String event = UUID.randomUUID().toString();
        JsonObject state = new JsonObject(); state.addProperty("Name", "minecraft:chest");
        var snapshot = new JournalStore.Snapshot(123, state, "{Items:[{Slot:0b,id:\"minecraft:diamond\",count:3}]}");
        journal.append(event, "minecraft:overworld", -17, 20, List.of(snapshot));
        Path pending = journal.pending(event, "minecraft:overworld", -17, 20);
        try (var cursor = journal.open(pending)) {
            assertEquals(snapshot, cursor.next()); assertNull(cursor.next()); cursor.complete();
        }
        assertNull(journal.pending(event, "minecraft:overworld", -17, 20));
        assertNull(journal.pending(event, "minecraft:the_nether", -17, 20));
    }
}
