package com.strangesmell.immersiveslumber;

import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.entity.player.Player;

public abstract class MessageCondition {
    public abstract  Component getComponent(MinecraftServer minecraftServer,Player player);

}
