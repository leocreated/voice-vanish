package dev.leo.voicevanish.test;

import dev.leo.voicevanish.*;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.*;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.block.*;
import net.minecraft.block.entity.ChestBlockEntity;
import net.minecraft.entity.ItemEntity;
import net.minecraft.item.*;
import net.minecraft.util.math.*;
import net.minecraft.world.World;
import java.util.function.BooleanSupplier;

/** Real client + integrated server + actual chunk mutation/mixin hooks. */
public final class WorldSmoke implements FabricClientGameTest {
    private static void require(boolean condition, String message) { if (!condition) throw new AssertionError(message); }
    private static void await(ClientGameTestContext context, TestServerContext server, java.util.function.Predicate<net.minecraft.server.MinecraftServer> predicate, String message) {
        for (int i = 0; i < 400; i++) { if (server.computeOnServer(predicate::test)) return; context.waitTick(); }
        throw new AssertionError(message);
    }
    @Override public void runTest(ClientGameTestContext context) {
        try { runWorldSmoke(context); }
        catch (Exception ex) { throw new RuntimeException(ex); }
    }
    private void runWorldSmoke(ClientGameTestContext context) throws Exception {
        var session = context.worldBuilder().create();
        var save = session.getWorldSave();
        try {
            TestServerContext server = session.getServer();
            BlockPos near = new BlockPos(1, 100, 1), far = new BlockPos(513, 100, 1), nether = new BlockPos(1, 150, 1);
            server.runOnServer(s -> {
                require(VoiceVanish.controller(s) != null, "Controller did not initialize");
                var overworld = s.getOverworld(); var underworld = s.getWorld(World.NETHER);
                overworld.setChunkForced(0, 0, true); overworld.setChunkForced(32, 0, true); underworld.setChunkForced(0, 0, true);
                overworld.setBlockState(near, Blocks.OAK_PLANKS.getDefaultState(), 2);
                overworld.setBlockState(far, Blocks.OAK_PLANKS.getDefaultState(), 2);
                underworld.setBlockState(nether, Blocks.OAK_PLANKS.getDefaultState(), 2);
                VoiceVanish.controller(s).setArmed(true);
            });
            context.runOnClient(client -> ClientPlayNetworking.send(new Packets.Speech("Let us collect oak planks")));
            await(context, server, s -> s.getOverworld().getBlockState(near).isAir()
                    && s.getOverworld().getBlockState(far).isAir() && s.getWorld(World.NETHER).getBlockState(nether).isAir(), "Ban did not affect distant chunks and another dimension");
            server.runOnServer(s -> {
                require(s.getOverworld().getEntitiesByClass(ItemEntity.class, new Box(near).expand(2), e -> true).isEmpty(), "Deletion dropped block items");
                s.getOverworld().setBlockState(near.up(), Blocks.OAK_PLANKS.getDefaultState(), 2);
            });
            await(context, server, s -> s.getOverworld().getBlockState(near.up()).isAir(), "Newly placed banned block remained");
            BlockPos future = new BlockPos(1025, 100, 1);
            server.runOnServer(s -> {
                var world = s.getOverworld(); var chunk = world.getWorldChunk(future);
                var c = VoiceVanish.controller(s);
                // Seed an unloaded-chunk scenario without force-loading any chunk in product code.
                c.unloaded(world, chunk);
                world.setBlockState(future, Blocks.OAK_PLANKS.getDefaultState(), 2);
                require(world.getBlockState(future).isOf(Blocks.OAK_PLANKS), "Test seed was not placed");
                c.loaded(world, chunk);
            });
            await(context, server, s -> s.getOverworld().getBlockState(future).isAir(), "Chunk-load scan did not enforce existing ban");
            server.runOnServer(s -> VoiceVanish.controller(s).undo());
            await(context, server, s -> s.getOverworld().getBlockState(near).isOf(Blocks.OAK_PLANKS)
                    && s.getOverworld().getBlockState(far).isOf(Blocks.OAK_PLANKS)
                    && s.getWorld(World.NETHER).getBlockState(nether).isOf(Blocks.OAK_PLANKS), "Undo did not restore removed blocks across dimensions");

            BlockPos spoken = near.add(16, 10, 0);
            server.runOnServer(s -> {
                var world = s.getOverworld();
                world.setBlockState(spoken, Blocks.BIRCH_LOG.getDefaultState(), 2);
                world.setBlockState(spoken.east(), Blocks.BIRCH_LEAVES.getDefaultState().with(LeavesBlock.PERSISTENT, true), 2);
                world.setBlockState(spoken.east(2), Blocks.GRASS_BLOCK.getDefaultState(), 2);
                world.setBlockState(spoken.east(3).down(), Blocks.STONE.getDefaultState(), 2);
                world.setBlockState(spoken.east(3), Blocks.REDSTONE_WIRE.getDefaultState(), 2);
                world.setBlockState(spoken.east(4), Blocks.STONE_BRICKS.getDefaultState(), 2);
                VoiceVanish.controller(s).setArmed(true);
            });
            context.runOnClient(client -> ClientPlayNetworking.send(new Packets.Speech(
                    "do you have redstone and birch leaves I have a birch log over by the grass and stone bricks")));
            await(context, server, s -> {
                for (int x=0; x<5; x++) if (!s.getOverworld().getBlockState(spoken.east(x)).isAir()) return false;
                return true;
            }, "Natural conversational block names did not remove all five types");
            server.runOnServer(s -> require(s.getOverworld().getBlockState(spoken.east(3).down()).isOf(Blocks.STONE),
                    "Saying stone bricks incorrectly banned stone"));
            server.runOnServer(s -> {
                var world=s.getOverworld();
                world.setBlockState(spoken.up(3),Blocks.ANDESITE.getDefaultState(),2);
                world.setBlockState(spoken.up(3).east(),Blocks.GRANITE.getDefaultState(),2);
                world.setBlockState(spoken.up(3).east(2),Blocks.DIORITE.getDefaultState(),2);
            });
            context.waitTicks(5);
            context.runOnClient(client -> {
                ClientPlayNetworking.send(new Packets.Speech("andesite"));
                ClientPlayNetworking.send(new Packets.Speech("granite"));
                ClientPlayNetworking.send(new Packets.Speech("diorite"));
            });
            await(context,server,s -> s.getOverworld().getBlockState(spoken.up(3)).isAir()
                    && s.getOverworld().getBlockState(spoken.up(3).east()).isAir()
                    && s.getOverworld().getBlockState(spoken.up(3).east(2)).isAir(), "Rock-name speech did not remove all three types");

            BlockPos chest = near.add(3, 0, 0);
            server.runOnServer(s -> {
                var world = s.getOverworld(); world.setBlockState(chest, Blocks.CHEST.getDefaultState(), 2);
                ((ChestBlockEntity) world.getBlockEntity(chest)).setStack(0, new ItemStack(Items.DIAMOND, 3));
                VoiceVanish.controller(s).ban("minecraft:chest", "integration-test");
            });
            await(context, server, s -> s.getOverworld().getBlockState(chest).isAir(), "Chest was not removed");
            server.runOnServer(s -> {
                require(s.getOverworld().getEntitiesByClass(ItemEntity.class, new Box(chest).expand(2), e -> true).isEmpty(), "Container inventory scattered despite snapshot");
                VoiceVanish.controller(s).undo();
            });
            await(context, server, s -> {
                var entity = s.getOverworld().getBlockEntity(chest);
                return entity instanceof ChestBlockEntity c && c.getStack(0).isOf(Items.DIAMOND) && c.getStack(0).getCount() == 3;
            }, "Undo lost chest inventory");

            BlockPos conflict = near.add(5, 0, 0);
            server.runOnServer(s -> {
                s.getOverworld().setBlockState(conflict, Blocks.LIME_CONCRETE.getDefaultState(), 2);
                VoiceVanish.controller(s).ban("minecraft:lime_concrete", "integration-test");
            });
            await(context, server, s -> s.getOverworld().getBlockState(conflict).isAir(), "Conflict setup did not remove block");
            server.runOnServer(s -> {
                s.getOverworld().setBlockState(conflict, Blocks.GOLD_BLOCK.getDefaultState(), 2);
                VoiceVanish.controller(s).undo();
            });
            context.waitTicks(20);
            server.runOnServer(s -> {
                require(s.getOverworld().getBlockState(conflict).isOf(Blocks.GOLD_BLOCK), "Undo overwrote a newer build");
                boolean rejected = false;
                try { VoiceVanish.controller(s).ban("minecraft:end_portal_frame", "integration-test"); }
                catch (IllegalArgumentException expected) { rejected = true; }
                require(rejected, "End portal frame protection failed");
                s.getOverworld().setBlockState(near.add(8, 0, 0), Blocks.STONE.getDefaultState(), 2);
                s.getOverworld().setChunkForced(3, 3, true);
                for (int x = 48; x < 64; x++) for (int y = 96; y < 112; y++) for (int z = 48; z < 64; z++)
                    s.getOverworld().setBlockState(new BlockPos(x, y, z), Blocks.STONE.getDefaultState(), 2);
                VoiceVanish.controller(s).ban("minecraft:stone", "integration-test");
            });
            await(context, server, s -> s.getOverworld().getBlockState(near.add(8, 0, 0)).isAir(), "Stone did not disappear");
            await(context, server, s -> {
                for (int x = 48; x < 64; x++) for (int y = 96; y < 112; y++) for (int z = 48; z < 64; z++)
                    if (!s.getOverworld().getBlockState(new BlockPos(x, y, z)).isAir()) return false;
                return true;
            }, "Mass-removal test left stone in the 4096-block section");
        } finally { session.close(); }
        try (var reopened = save.open()) {
            var server = reopened.getServer();
            server.runOnServer(s -> {
                require(VoiceVanish.controller(s).status().contains("minecraft:stone"), "World reload lost active bans");
                s.getOverworld().setBlockState(new BlockPos(1, 101, 1), Blocks.STONE.getDefaultState(), 2);
            });
            await(context, server, s -> s.getOverworld().getBlockState(new BlockPos(1, 101, 1)).isAir(), "Reloaded ban was not enforced");
        }
        VoiceVanish.LOG.info("VOICE_VANISH_WORLD_SMOKE_PASSED: distant chunks, dimensions, chunk load, placement, no drops, undo, chest NBT, conflicts, protection, reload");
    }
}
