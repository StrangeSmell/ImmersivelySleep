package com.strangesmell.immersiveslumber.mixin;

import net.minecraft.client.Minecraft;
import net.minecraft.core.Holder;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import javax.annotation.Nullable;
import java.util.List;

import static com.strangesmell.immersiveslumber.TimeProgressionHandler.playSoundEventMap;

@Mixin(ServerLevel.class)
public abstract class ServerLevelMixin {
    @Shadow @Final private MinecraftServer server;


    @Inject(
            method = "playSeededSound(Lnet/minecraft/world/entity/player/Player;DDDLnet/minecraft/core/Holder;Lnet/minecraft/sounds/SoundSource;FFJ)V",
            at = @At("HEAD")
    )
    private void onPlaySeededSoundCoordinates(
            @Nullable Player player,
            double x, double y, double z,
            Holder<SoundEvent> sound,
            SoundSource category,
            float volume, float pitch,
            long seed,
            CallbackInfo ci
    ) {
        // 坐标版本的自定义逻辑
        List<ServerPlayer> playerList = this.server.getPlayerList().getPlayers();
        for(ServerPlayer serverPlayer : playerList) {
            if(!serverPlayer.isSleeping()) return;
            if(isAudibleFrom(new Vec3(x,y,z),sound.value().getRange(volume))) {
                if(playSoundEventMap.get(serverPlayer.getUUID()) == null)
                    playSoundEventMap.put(serverPlayer.getUUID(), null);
            }
            playSoundEventMap.put(serverPlayer.getUUID(), sound.value().getLocation());

        }

    }
    @Inject(
            method = "playSeededSound(Lnet/minecraft/world/entity/player/Player;Lnet/minecraft/world/entity/Entity;Lnet/minecraft/core/Holder;Lnet/minecraft/sounds/SoundSource;FFJ)V",
            at = @At("HEAD")
    )
    private void onPlaySeededSoundEntity(
            @Nullable Player player,
            Entity entity,
            Holder<SoundEvent> sound,
            SoundSource category,
            float volume, float pitch,
            long seed,
            CallbackInfo ci
    )
    {
        List<ServerPlayer> playerList = this.server.getPlayerList().getPlayers();
        for(ServerPlayer serverPlayer : playerList) {
            if(!serverPlayer.isSleeping()) return;
            if(isAudibleFrom(entity.position(),sound.value().getRange(volume))) {
                if(playSoundEventMap.get(serverPlayer.getUUID()) == null)
                    playSoundEventMap.put(serverPlayer.getUUID(),null);
            }
            playSoundEventMap.put(serverPlayer.getUUID(), sound.value().getLocation());
        }
    }
        // 实体版本的自定义逻辑
    public boolean isAudibleFrom(Vec3 pLocation, float pRange) {
        if (Float.isInfinite(pRange)) {
            return true;
        } else {
            if(Minecraft.getInstance().player==null) return false;
            return !(Minecraft.getInstance().player.position().distanceTo(pLocation) > pRange);
        }
    }
}




