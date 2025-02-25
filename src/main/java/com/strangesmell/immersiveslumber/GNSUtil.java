package com.strangesmell.immersiveslumber;

import com.legacy.goodnightsleep.capabillity.DreamPlayer;
import com.legacy.goodnightsleep.capabillity.util.IDreamPlayer;
import com.legacy.goodnightsleep.registry.GNSDimensions;
import com.legacy.goodnightsleep.world.GNSTeleporter;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraftforge.common.ForgeHooks;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.server.ServerLifecycleHooks;

import java.util.Optional;
import java.util.function.Consumer;

public class GNSUtil {
    public static boolean isDreamDimension(Player player) {
        return player.level().dimension().equals(GNSDimensions.dreamKey())||player.level().dimension().equals(GNSDimensions.nightmareKey());

    }
    public static ResourceKey<Level> dreamKey() {
        return GNSDimensions.dreamKey();
    }
    public static ResourceKey<Level> nightmareKey() {
        return GNSDimensions.nightmareKey();
    }
    public static void changeDimension(ResourceKey<Level> type, Entity entity, BlockPos pos) {
        GNSTeleporter.changeDimension(type, entity, pos);
    }
    public static <E extends Player> void ifPresent(E player, Consumer<IDreamPlayer> action) {
        DreamPlayer.ifPresent(player, (d) -> {
            d.setEnteredDreamTime(player.level().getGameTime());
            d.syncDataToClient();
        });

    }
}

