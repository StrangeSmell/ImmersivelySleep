package com.strangesmell.immersiveslumber.mixin;

import net.minecraft.server.players.SleepStatus;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(SleepStatus.class)
public class SleepStatusMixin {
    // Prevents skipping the night by always returning false for areEnoughSleeping
    @Inject(method = "areEnoughSleeping", at = @At("HEAD"), cancellable = true)
    public void areEnoughSleepingInject(int percentage, CallbackInfoReturnable<Boolean> info) {
        info.setReturnValue(false);
    }
}