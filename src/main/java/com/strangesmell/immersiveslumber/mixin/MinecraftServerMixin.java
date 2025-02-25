package com.strangesmell.immersiveslumber.mixin;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.PlayerList;
import net.minecraft.world.level.GameRules;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;
import java.util.function.BooleanSupplier;

import static com.strangesmell.immersiveslumber.Util.accelerationFactor;
import static com.strangesmell.immersiveslumber.Util.clamp;

@Mixin(MinecraftServer.class)
public abstract class MinecraftServerMixin {
    @Shadow public abstract void tickServer(BooleanSupplier booleanSupplier);
    @Shadow protected abstract boolean haveTime();

    @Shadow private PlayerList playerList;
    @Unique
    private boolean sleepcycle$isAcceleratingTime = false;

    @Inject(method = "tickServer", at = @At("HEAD"))
    private void onTickServerStart(BooleanSupplier hasTimeLeft, CallbackInfo ci) {
        if (!sleepcycle$isAcceleratingTime && sleepcycle$areEnoughPlayersSleeping()) {
            sleepcycle$isAcceleratingTime = true;
            float accelerationFactor = accelerationFactor((MinecraftServer)(Object)this);
            accelerationFactor = clamp(accelerationFactor,1,10);
            for (int i = 1; i < accelerationFactor && haveTime(); i++) {
                tickServer(hasTimeLeft);
                //System.out.println(i);
            }
            sleepcycle$isAcceleratingTime = false;
        }
    }

    @Unique
    private boolean sleepcycle$areEnoughPlayersSleeping() {
        MinecraftServer server = (MinecraftServer)(Object)this;
        for (ServerLevel level : server.getAllLevels()) {
            List<ServerPlayer> players = level.players();
            if (players.isEmpty()) continue;

            // Get the sleeping percentage from the game rules
            int percentageRequired = level.getGameRules().getInt(GameRules.RULE_PLAYERS_SLEEPING_PERCENTAGE);

            int sleepingCount = (int) players.stream().filter(ServerPlayer::isSleeping).count();
            if (sleepingCount > 0 && sleepingCount >= (players.size() * percentageRequired / 100)) {
                return true;
            }
        }
        return false;
    }
}
