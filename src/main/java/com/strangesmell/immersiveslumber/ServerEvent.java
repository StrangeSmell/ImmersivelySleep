package com.strangesmell.immersiveslumber;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.animal.Cat;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.event.entity.player.SleepingLocationCheckEvent;
import net.minecraftforge.event.entity.player.SleepingTimeCheckEvent;
import net.minecraftforge.event.level.ExplosionEvent;
import net.minecraftforge.eventbus.api.Event;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import static com.strangesmell.immersiveslumber.ImmersiveSlumber.MODID;

@Mod.EventBusSubscriber(modid = MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class ServerEvent {

    @SubscribeEvent
    public static void canContinueSleepingEvent(SleepingLocationCheckEvent event) {
        if(event.getEntity() instanceof ServerPlayer serverPlayer){
            event.setResult(Event.Result.ALLOW);
        }
    }
    //白天也可以睡觉
    @SubscribeEvent
    public static void canPlayerSleepEvent(SleepingTimeCheckEvent event) {
        event.setResult(Event.Result.ALLOW);

    }
    @SubscribeEvent
    public static void explosionEvent(ExplosionEvent.Detonate event) {
        if(event.getExplosion().getDirectSourceEntity() instanceof Creeper creeper){
            for(Entity entity:event.getAffectedEntities()){
                if(entity instanceof Player player){
                    if(ImmersiveSlumber.playerNbt.get(player.getUUID()) !=null){
                        if(ImmersiveSlumber.playerNbt.get(player.getUUID()).getBoolean("after_creeper")){
                            player.sendSystemMessage(Component.literal("(✿◕‿◕) bang!!!"));
                        }
                    }
                }
            }

        }


    }
    @SubscribeEvent
    public static void rightClickEmpty(PlayerInteractEvent.EntityInteract event) {
        if(event.getTarget() instanceof Cat cat){
            if(event.getEntity().isCreative()){
                if(cat.isLying()){
                    cat.setLying(false);
                }else{
                    cat.setLying(true);
                }
            }
        }
    }
}
