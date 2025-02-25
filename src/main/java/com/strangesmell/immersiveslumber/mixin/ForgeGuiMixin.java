package com.strangesmell.immersiveslumber.mixin;

import com.mojang.blaze3d.systems.RenderSystem;
import com.strangesmell.immersiveslumber.ImmersiveSlumber;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraftforge.client.gui.overlay.ForgeGui;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Random;

import static com.strangesmell.immersiveslumber.ImmersiveSlumber.haveRandom;
import static com.strangesmell.immersiveslumber.ImmersiveSlumber.timeAfterUp;

@Mixin(ForgeGui.class)
public abstract class ForgeGuiMixin  extends Gui {
    public ForgeGuiMixin(Minecraft pMinecraft, ItemRenderer pItemRenderer) {
        super(pMinecraft, pItemRenderer);
    }
    @Shadow(remap=false) public int rightHeight;
    @Shadow(remap=false) private Font font;
    @Shadow(remap=false) public int leftHeight;
    @Inject(
            method = "renderRecordOverlay(IIFLnet/minecraft/client/gui/GuiGraphics;)V",
            at = @At("HEAD"),
            cancellable = true,
            remap = false
    )
    public void redirectDrawString(int width, int height, float partialTick, GuiGraphics guiGraphics, CallbackInfo ci) {
        if(Minecraft.getInstance().player.isSleeping()){
            timeAfterUp=this.overlayMessageTime;
        }else if(Minecraft.getInstance().player.isDeadOrDying()){
            this.overlayMessageString = Component.translatable("immersiveslumber.message.before_death");
        }
        if (this.overlayMessageTime > 0) {
            if(this.overlayMessageTime==59) haveRandom = false;
            minecraft.getProfiler().push("overlayMessage");
            float hue = (float) overlayMessageTime - partialTick;
            int opacity = (int) (hue * 255.0F / 20.0F);
            if (opacity > 255) opacity = 255;
            if (opacity > 8) {
                //Include a shift based on the bar height plus the difference between the height that renderSelectedItemName
                // renders at (59) and the height that the overlay/status bar renders at (68) by default
                int yShift = Math.max(this.leftHeight, this.rightHeight) + (68 - 59);
                guiGraphics.pose().pushPose();
                //If y shift is smaller less than the default y level, just render it at the base y level
                guiGraphics.pose().translate(width / 2D, height - Math.max(yShift, 68), 0.0D);
                RenderSystem.enableBlend();
                RenderSystem.defaultBlendFunc();
                int color = (animateOverlayMessageColor ? Mth.hsvToRgb(hue / 50.0F, 0.7F, 0.6F) & 0xFFFFFF : 0xFFFFFF);
                int messageWidth = font.width(overlayMessageString);
                drawBackdrop(guiGraphics, this.font, -4, messageWidth, 16777215 | (opacity << 24));

                if (Minecraft.getInstance().player != null &&(Minecraft.getInstance().player.isSleeping()||timeAfterUp>0)) {
                    if(this.overlayMessageTime ==60 && !haveRandom){
                        Random random = new Random();
                        ImmersiveSlumber.y = random.nextInt(-guiGraphics.guiHeight()+80,-4);
                        ImmersiveSlumber.x = random.nextInt(-guiGraphics.guiWidth()/2,guiGraphics.guiWidth()/2-messageWidth);
                        ImmersiveSlumber.haveRandom = true;
                    }
                    guiGraphics.drawString(font, overlayMessageString.getVisualOrderText(), ImmersiveSlumber.x, ImmersiveSlumber.y, color | (opacity << 24));
                }else{
                    guiGraphics.drawString(font, overlayMessageString.getVisualOrderText(), -messageWidth / 2, -4, color | (opacity << 24));
                }
                RenderSystem.disableBlend();
                guiGraphics.pose().popPose();
            }
            minecraft.getProfiler().pop();
        }
        ci.cancel();
    }





}
