package com.strangesmell.immersiveslumber.mixin;

import com.strangesmell.immersiveslumber.ModStats;
import com.strangesmell.immersiveslumber.TimeProgressionHandler;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import static net.minecraft.world.level.block.BedBlock.OCCUPIED;

@Mixin(BedBlock.class)
public class BedBlockMixin {
    @Inject(method = "use", at = @At("RETURN"))
    private void onUse(BlockState pState, Level level, BlockPos pPos, Player player, InteractionHand pHand, BlockHitResult pHit, CallbackInfoReturnable<InteractionResult> cir) {
        // Check if the original method returned SUCCESS
        if (cir.getReturnValue() == InteractionResult.SUCCESS && !level.isClientSide) {
            // Ensure the player is sleeping and add custom logic
            if (player instanceof ServerPlayer) {
                if(!pState.getValue(OCCUPIED)){
                    // Register the world for time progression
                    TimeProgressionHandler.addWorld((ServerLevel) level);
                    TimeProgressionHandler.playerMessageTicks.put(player.getUUID(),0);
                    TimeProgressionHandler.playSoundEventMap.clear();
                    player.awardStat(ModStats.SLEEP_COUNT.get());
                }

            }
        }
    }
}