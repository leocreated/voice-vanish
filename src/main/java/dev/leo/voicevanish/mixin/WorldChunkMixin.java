package dev.leo.voicevanish.mixin;

import dev.leo.voicevanish.VoiceVanish;
import net.minecraft.block.BlockState;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.chunk.WorldChunk;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(WorldChunk.class)
public abstract class WorldChunkMixin {
    @Inject(method = "setBlockState", at = @At("RETURN"))
    private void voicevanish$changed(BlockPos pos, BlockState state, boolean moved, CallbackInfoReturnable<BlockState> cir) {
        WorldChunk chunk = (WorldChunk) (Object) this;
        if (chunk.getWorld() instanceof ServerWorld world && world.getServer().isOnThread()) {
            var controller = VoiceVanish.controller(world.getServer());
            if (controller != null) controller.changed(world, chunk, pos, state);
        }
    }
}
