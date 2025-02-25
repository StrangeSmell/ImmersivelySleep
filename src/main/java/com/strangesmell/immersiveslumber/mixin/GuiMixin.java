package com.strangesmell.immersiveslumber.mixin;

import com.strangesmell.immersiveslumber.ImmersiveSlumber;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.renderer.entity.ItemRenderer;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;


@Mixin(Gui.class)
public abstract class GuiMixin {

    @Shadow @Final protected ItemRenderer itemRenderer;

    @Inject(method = "tick()V", at = @At("HEAD"))
    private void tick(CallbackInfo ci) {
        if (ImmersiveSlumber.timeAfterUp>0) {
            --ImmersiveSlumber.timeAfterUp;
        }

    }

}
