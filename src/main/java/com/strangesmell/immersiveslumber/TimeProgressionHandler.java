package com.strangesmell.immersiveslumber;

import net.minecraft.core.BlockPos;
import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerChunkCache;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.ServerPlayerGameMode;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.stats.Stats;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.OwnableEntity;
import net.minecraft.world.entity.ai.targeting.TargetingConditions;
import net.minecraft.world.entity.animal.Cat;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.TallFlowerBlock;
import net.minecraft.world.level.block.entity.BedBlockEntity;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.common.Mod;

import java.util.*;

import static com.strangesmell.immersiveslumber.ImmersiveSlumber.MODID;
import static com.strangesmell.immersiveslumber.Util.*;

@Mod.EventBusSubscriber(modid = MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class TimeProgressionHandler {
    private static final Map<ServerLevel, Integer> worldSleepTicks = new HashMap<>();
    private static final Map<ServerPlayer, Integer> playerSleepTicks = new HashMap<>();
    private static final Set<ServerPlayer> sleepingPlayers = new HashSet<>();
    public static final Map<UUID, Integer> playerMessageTicks = new HashMap<>();
    public static final List<MessageCondition> playerMessageConditions = new ArrayList<>();
    public static final Map<ResourceLocation,MessageCondition> playerSoundMessageConditions = new HashMap<>();
    public static final Map<UUID,ResourceLocation> playSoundEventMap = new HashMap<>();
    private static Integer originalTickSpeed = null;
    public static int cool_down = 60;
    private static final int SLEEP_ADVANCEMENT_DURATION = 6000; // 5 minutes in ticks (20 ticks * 60 seconds * 5 minutes) = 6000

    public static void addWorld(ServerLevel world) {
        ImmersiveSlumber.LOGGER.info("World being Added");
        if (!worldSleepTicks.containsKey(world)) {
            originalTickSpeed = world.getGameRules().getInt(GameRules.RULE_RANDOMTICKING);
        }
        worldSleepTicks.putIfAbsent(world, 0);
    }

    public static void removeWorld(ServerLevel world) {
        worldSleepTicks.remove(world);
        // Reset the tick speed
        if (originalTickSpeed != null) {
            world.getGameRules().getRule(GameRules.RULE_RANDOMTICKING).set(originalTickSpeed, world.getServer());
        }
    }
    @SubscribeEvent
    public static void onWorldTick(TickEvent.ServerTickEvent event) {
        ServerLevel world = event.getServer().overworld();
        if (worldSleepTicks.containsKey(world)) {
            List<ServerPlayer> players = world.players();
            int playerCount = players.size();

            double playersRequiredToSleepRatio = world.getServer().getGameRules().getInt(GameRules.RULE_PLAYERS_SLEEPING_PERCENTAGE) / 100d;
            int playersRequiredToSleep = (int) Math.ceil(playersRequiredToSleepRatio * playerCount);
            long sleepingPlayerCount = players.stream().filter(ServerPlayer::isSleeping).count();

            for (ServerPlayer player : players) {
                if (player.isSleeping()) {
                    sleepingPlayers.add(player);
                    playerSleepTicks.putIfAbsent(player, 0);
                    //睡觉生命恢复
/*                    if (ModConfigs.DO_REGEN) {
                        if (!player.hasEffect(MobEffects.REGENERATION)) {
                            player.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 100, 0)); // 2 minutes
                            if (player.getHealth() != player.getMaxHealth()) {
                                player.awardStat(ModStats.HEALTH_REGAINED.value(), 1);
                            }
                        }
                    }*/

                    int playerTicksAsleep = playerSleepTicks.get(player);
/*                    if (ModConfigs.GRANT_BUFFS) {//时间够就给好buff，不够就给负面buff
                        if (!player.hasEffect(ModEffects.getWellRestedHolder()) && playerTicksAsleep >= ModConfigs.WELL_RESTED_WAIT && playerTicksAsleep < ModConfigs.TIRED_WAIT) {
                            player.addEffect(new MobEffectInstance(ModEffects.getWellRestedHolder(), ModConfigs.WELL_RESTED_LENGTH, 0), player);
                            playSound(player, ModSounds.WELL_RESTED_SOUND.get());
                        } else if (!player.hasEffect(ModEffects.getTiredHolder()) && playerTicksAsleep >= ModConfigs.TIRED_WAIT) {
                            player.addEffect(new MobEffectInstance(ModEffects.getTiredHolder(), ModConfigs.TIRED_LENGTH, 0), player);
                            playSound(player, ModSounds.TIRED_SOUND.get());
                        }
                    }*/

/*                    // Check for advancement condition
                    //给成就
                    if (playerTicksAsleep >= SLEEP_ADVANCEMENT_DURATION) {
                        ModCriteria.getSleepCriterion().trigger(player); // Trigger the custom criterion
                    }*/

                    playerSleepTicks.put(player, playerTicksAsleep + 1); // Increment player sleep ticks
                    //加入计数
                    //player.awardStat(ModStats.TIME_SLEPT.value(), 1);

                } else {
                    // Reset player's sleep ticks if they are not sleeping
                    //加入疲劳睡觉计数
                    playerSleepTicks.remove(player);
                    sleepingPlayers.remove(player);
                }
            }

            if (sleepingPlayerCount >= playersRequiredToSleep) {
                if (Config.changeTickSpeed && sleepingPlayerCount > 0) {
                    //提升random tick ，需要加入config默认禁止
                    if (originalTickSpeed != null && world.getGameRules().getInt(GameRules.RULE_RANDOMTICKING) == originalTickSpeed) {
                        world.getGameRules().getRule(GameRules.RULE_RANDOMTICKING).set((int) (originalTickSpeed * Config.daySkipSpeed * Config.SLEEP_TICK_MULTIPLIER), world.getServer());

                    }
                }

                int ticksAsleep = worldSleepTicks.get(world);
                long timeIncrement = calculateTimeIncrement(ticksAsleep);

                world.setDayTime(world.getDayTime() + timeIncrement); // Adjust time
/*                for (int i = 0; i < timeIncrement; i++) {

                }*/
                //天色加速
                //Minecraft.getInstance().levelRenderer.tick();
                //区块加速
                if (ticksAsleep % 20 == 0) { // Tick chunks less frequently
                    //tickChunks(world);
                }

                //worldSleepTicks.put(world, ticksAsleep + 1); // Increment sleep ticks
            } else {
                if (originalTickSpeed != null) {
                    world.getGameRules().getRule(GameRules.RULE_RANDOMTICKING).set(originalTickSpeed, world.getServer());
                }
                if (sleepingPlayerCount == 0) {
                    removeWorld(world);
                }
            }
        }
    }


    private static long calculateTimeIncrement(int ticksAsleep) {
        // Calculate the time increment with a wind-up effect
        return (long) (Config.daySkipSpeed * Math.min(1, ticksAsleep / 100.0));
    }

    private static void playSound(ServerPlayer player, SoundEvent soundId) {
        player.level().playSound(
                null, // Player that hears the sound
                player.blockPosition(), // Position of the sound
                soundId,
                SoundSource.NEUTRAL, // This determines which slider affects this sound
                1.0F, // Volume multiplier, 1 is normal, 0.5 is half volume, etc
                1.0F // Pitch multiplier, 1 is normal, 0.5 is half pitch, etc
        );
    }

    private static void tickChunks(ServerLevel world) {
        ServerChunkCache chunkManager = world.getChunkSource();
        chunkManager.tick(() -> true, true);
    }
    @SubscribeEvent
    public static void onPlayerDisconnect(PlayerEvent.PlayerLoggedOutEvent event) {
        Player player = event.getEntity();
        System.out.println("SleepCycle: on player disconnect");

        for (ServerLevel world : player.getServer().getAllLevels()) {
            List<ServerPlayer> players = world.players();
            long sleepingPlayerCount = players.stream().filter(ServerPlayer::isSleeping).count();

            if (sleepingPlayers.contains(player)) {
                playerSleepTicks.remove(player);
                sleepingPlayers.remove(player);

                removeWorld(world);
                System.out.println("Removing the World");
            }

            if (originalTickSpeed != null) {
                world.getGameRules().getRule(GameRules.RULE_RANDOMTICKING).set(originalTickSpeed, player.getServer());
                System.out.println("Resetting world tick speed");
            }
        }
    }



    public static void  initMessage(){
        //1、创造检查
        playerMessageConditions.add(new MessageCondition() {
            @Override
            public Component getComponent(MinecraftServer minecraftServer, Player player) {
                if(player.isCreative()){
                    return Component.translatable("immersiveslumber.message.creative");
                }else return null;
            }
        });
        //2、血量检查,
        playerMessageConditions.add(new MessageCondition() {
            @Override
            public Component getComponent(MinecraftServer minecraftServer, Player player) {
                float factor =player.getHealth()/player.getMaxHealth();
                if(factor>0.9){
                }else if (factor<0.2){
                    return Component.translatable("immersiveslumber.message.health_0.2");
                }else{
                    return Component.translatable("immersiveslumber.message.health_0.9");
                }
                return null;
            }
        });
                //3、遮蔽检查和天气检查
        playerMessageConditions.add(new MessageCondition() {
            @Override
            public Component getComponent(MinecraftServer minecraftServer, Player player) {
                Biome.Precipitation weather = player.level().getBiome(player.getOnPos()).value().getPrecipitationAt(player.getOnPos());
                boolean canSeeSky = player.level().canSeeSky(player.getOnPos());
                if(canSeeSky){
                    if(weather == Biome.Precipitation.RAIN ){
                        return Component.translatable("immersiveslumber.message.weather_rain_sky");
                    }else if(weather == Biome.Precipitation.SNOW){
                        return Component.translatable("immersiveslumber.message.weather_rain_sky");
                    }else{
                        return Component.translatable("immersiveslumber.message.weather_sky");
                    }
                }else{
                    if(weather == Biome.Precipitation.RAIN ){
                        if(Util.getRandomBoolean()){
                            return Component.translatable("immersiveslumber.message.weather_rain_nosky");
                        }else{
                            return Component.translatable("immersiveslumber.message.weather_rain_nosky_2");
                        }
                    }else if(weather == Biome.Precipitation.SNOW){
                        int i = Util.getRandomInt(3);
                        if(i==0){
                            return Component.translatable("immersiveslumber.message.weather_rain_nosky");
                        }else if (i==1){
                            return Component.translatable("immersiveslumber.message.weather_snow_nosky");
                        } else if (i==2) {
                            return Component.translatable("immersiveslumber.message.weather_snow_nosky_2");
                        }
                    }
                }
                return null;
            }
        });
                //4、检查是否是白天
        playerMessageConditions.add(new MessageCondition() {
            @Override
            public Component getComponent(MinecraftServer minecraftServer, Player player) {
                if(player.level().isDay()){
                    return Component.translatable("immersiveslumber.message.day_sleep");
                }
                return null;
            }
        });
                //5、亮度检查
        playerMessageConditions.add(new MessageCondition() {
            @Override
            public Component getComponent(MinecraftServer minecraftServer, Player player) {
                float light =  player.level().getBrightness(LightLayer.BLOCK, player.getOnPos());
                        if(light>10){
                            return Component.translatable("immersiveslumber.message.light_10");
                        }else if (light>5){
                            return Component.translatable("immersiveslumber.message.light_5");
                        }else if(light==0){
                            return Component.translatable("immersiveslumber.message.light_0");
                        }else if(light < 2){
                            return Component.translatable("immersiveslumber.message.light_2");
                        }
                return null;
            }
        });
                //6、饥饿检查
        playerMessageConditions.add(new MessageCondition() {
            @Override
            public Component getComponent(MinecraftServer minecraftServer, Player player) {
                float factor = clamp((float) player.getFoodData().getFoodLevel() /20,0,1);
                if(factor<0.4) {
                    return Component.translatable("immersiveslumber.message.hunger_0.2");
                }else if (factor<0.8){
                    return Component.translatable("immersiveslumber.message.hunger_0.8");
                }else if (factor>=1){
                    return Component.translatable("immersiveslumber.message.hunger_1");
                }
                return null;
            }
        });
                //7、饱食检查
        playerMessageConditions.add(new MessageCondition() {
            @Override
            public Component getComponent(MinecraftServer minecraftServer, Player player) {
                float factor = clamp(player.getFoodData().getSaturationLevel() /10,0,1);
                return null;
            }
        });
                //8、生物检查
        playerMessageConditions.add(new MessageCondition() {
            @Override
            public Component getComponent(MinecraftServer minecraftServer, Player player) {
                List<LivingEntity> livingEntityList = player.level().getNearbyEntities(LivingEntity.class, TargetingConditions.forNonCombat(),player,player.getBoundingBox().inflate(5,3,5));
                if(livingEntityList.isEmpty()){
                    return Component.translatable("immersiveslumber.message.entity_0");
                }else{
                    for(LivingEntity livingEntity : livingEntityList){
                        if(livingEntity instanceof OwnableEntity ownableEntity){
                            if(ownableEntity.getOwner().equals(player)){
                                if(livingEntity instanceof Cat cat){
                                    if(cat.isLying()){
                                        if(Util.getRandomBoolean()){
                                            return Component.translatable("immersiveslumber.message.entity_cat_on_me");
                                        }else{
                                            return Component.translatable("immersiveslumber.message.entity_cat_owner");
                                        }

                                    }else{
                                        return Component.translatable("immersiveslumber.message.entity_cat_off_me");
                                    }
                                }else{
                                    return Component.translatable("immersiveslumber.message.entity_me");
                                }
                            }else{
                                return Component.translatable("immersiveslumber.message.entity_other");
                            }
                        }else{
                            if(livingEntity.canAttack(player)){
                                return Component.translatable("immersiveslumber.message.entity_enemy");
                            }else{
                                return Component.translatable("immersiveslumber.message.entity_no_enemy");
                            }
                        }
                    }
                }
                return null;
            }
        });
        //9、检查buff
        playerMessageConditions.add(new MessageCondition() {
            @Override
            public Component getComponent(MinecraftServer minecraftServer, Player player) {
                HashSet<MobEffect> effects = new HashSet<>();
                boolean haveNoBenificialEffect = false;
                for(MobEffectInstance mobEffectInstance :player.getActiveEffects()){
                    if(!mobEffectInstance.getEffect().isBeneficial()){
                        haveNoBenificialEffect=true;
                    }else {
                        effects.add(mobEffectInstance.getEffect());
                    }
                }
                if(haveNoBenificialEffect){
                    return Component.translatable("immersiveslumber.message.no_beneficial_effect");
                }
                return null;
            }
        });
        //10、检查buff
        playerMessageConditions.add(new MessageCondition() {
            @Override
            public Component getComponent(MinecraftServer minecraftServer, Player player) {
                HashSet<MobEffect> effects = new HashSet<>();
                for(MobEffectInstance mobEffectInstance :player.getActiveEffects()){
                    effects.add(mobEffectInstance.getEffect());
                }
                if(effects.contains(MobEffects.DOLPHINS_GRACE)){
                    return Component.translatable("immersiveslumber.message.effect_dolphins_grace");
                }else return Component.translatable("immersiveslumber.message.effect_no_dolphins_grace");
            }
        });
         //11、检查buff
        playerMessageConditions.add(new MessageCondition() {
            @Override
            public Component getComponent(MinecraftServer minecraftServer, Player player) {
                HashSet<MobEffect> effects = new HashSet<>();
                for(MobEffectInstance mobEffectInstance :player.getActiveEffects()){
                    effects.add(mobEffectInstance.getEffect());
                }
                if(effects.contains(MobEffects.LUCK)){
                    return Component.translatable("immersiveslumber.message.effect_luck");
                }else return Component.translatable("immersiveslumber.message.effect_no_luck");

            }
        });
         //12、检查buff
        playerMessageConditions.add(new MessageCondition() {
            @Override
            public Component getComponent(MinecraftServer minecraftServer, Player player) {
                HashSet<MobEffect> effects = new HashSet<>();
                for(MobEffectInstance mobEffectInstance :player.getActiveEffects()){
                    effects.add(mobEffectInstance.getEffect());
                }
                if(effects.contains(MobEffects.HERO_OF_THE_VILLAGE)){
                    return Component.translatable("immersiveslumber.message.effect_hero_of_the_village");
                }else return Component.translatable("immersiveslumber.message.effect_no_hero_of_the_village");

            }
        });
         //13、检查buff
        playerMessageConditions.add(new MessageCondition() {
            @Override
            public Component getComponent(MinecraftServer minecraftServer, Player player) {
                HashSet<MobEffect> effects = new HashSet<>();
                for(MobEffectInstance mobEffectInstance :player.getActiveEffects()){
                    effects.add(mobEffectInstance.getEffect());
                }
                if(effects.contains(MobEffects.SATURATION)){
                    return Component.translatable("immersiveslumber.message.effect_saturation");
                }else return Component.translatable("immersiveslumber.message.effect_no_saturation");

            }
        });
         //14、检查buff
        playerMessageConditions.add(new MessageCondition() {
            @Override
            public Component getComponent(MinecraftServer minecraftServer, Player player) {
                HashSet<MobEffect> effects = new HashSet<>();
                for(MobEffectInstance mobEffectInstance :player.getActiveEffects()){
                    effects.add(mobEffectInstance.getEffect());
                }
                if(effects.contains(MobEffects.FIRE_RESISTANCE)){
                    return Component.translatable("immersiveslumber.message.effect_fire_resistance");
                }else return Component.translatable("immersiveslumber.message.effect_no_fire_resistance");

            }
        });
         //15、检查buff
        playerMessageConditions.add(new MessageCondition() {
            @Override
            public Component getComponent(MinecraftServer minecraftServer, Player player) {
                HashSet<MobEffect> effects = new HashSet<>();
                for(MobEffectInstance mobEffectInstance :player.getActiveEffects()){
                    effects.add(mobEffectInstance.getEffect());
                }
                if(effects.contains(MobEffects.ABSORPTION)){
                    return Component.translatable("immersiveslumber.message.effect_absorption");
                }else return Component.translatable("immersiveslumber.message.effect_no_absorption");

            }
        });
         //16、检查buff
        playerMessageConditions.add(new MessageCondition() {
            @Override
            public Component getComponent(MinecraftServer minecraftServer, Player player) {
                HashSet<MobEffect> effects = new HashSet<>();
                for(MobEffectInstance mobEffectInstance :player.getActiveEffects()){
                    effects.add(mobEffectInstance.getEffect());
                }
                if(effects.contains(MobEffects.REGENERATION)){
                    return Component.translatable("immersiveslumber.message.effect_regeneration");
                }else return null;

            }
        });
         //17、检查buff
        playerMessageConditions.add(new MessageCondition() {
            @Override
            public Component getComponent(MinecraftServer minecraftServer, Player player) {
                HashSet<MobEffect> effects = new HashSet<>();
                for(MobEffectInstance mobEffectInstance :player.getActiveEffects()){
                    effects.add(mobEffectInstance.getEffect());
                }
                if(effects.contains(MobEffects.HEALTH_BOOST)){
                    return Component.translatable("immersiveslumber.message.effect_health_boost");
                }else return null;

            }
        });
         //18、检查buff
        playerMessageConditions.add(new MessageCondition() {
            @Override
            public Component getComponent(MinecraftServer minecraftServer, Player player) {
                HashSet<MobEffect> effects = new HashSet<>();
                for(MobEffectInstance mobEffectInstance :player.getActiveEffects()){
                    effects.add(mobEffectInstance.getEffect());
                }
                if(effects.contains(MobEffects.WATER_BREATHING)){
                    return Component.translatable("immersiveslumber.message.effect_water_breathing");
                }else return Component.translatable("immersiveslumber.message.effect_no_water_breathing");

            }
        });
         //19、检查buff
        playerMessageConditions.add(new MessageCondition() {
            @Override
            public Component getComponent(MinecraftServer minecraftServer, Player player) {
                HashSet<MobEffect> effects = new HashSet<>();
                for(MobEffectInstance mobEffectInstance :player.getActiveEffects()){
                    effects.add(mobEffectInstance.getEffect());
                }
                if(effects.contains(MobEffects.INVISIBILITY)){
                    return Component.translatable("immersiveslumber.message.effect_invisibility");
                }else return Component.translatable("immersiveslumber.message.effect_no_invisibility");

            }
        });
         //20、检查buff
        playerMessageConditions.add(new MessageCondition() {
            @Override
            public Component getComponent(MinecraftServer minecraftServer, Player player) {
                HashSet<MobEffect> effects = new HashSet<>();
                for(MobEffectInstance mobEffectInstance :player.getActiveEffects()){
                    effects.add(mobEffectInstance.getEffect());
                }
                if(effects.contains(MobEffects.NIGHT_VISION)){
                    return Component.translatable("immersiveslumber.message.effect_night_vision");
                }else return Component.translatable("immersiveslumber.message.effect_no_night_vision");

            }
        });
         //21、检查buff
        playerMessageConditions.add(new MessageCondition() {
            @Override
            public Component getComponent(MinecraftServer minecraftServer, Player player) {
                HashSet<MobEffect> effects = new HashSet<>();
                for(MobEffectInstance mobEffectInstance :player.getActiveEffects()){
                    effects.add(mobEffectInstance.getEffect());
                }
                if(effects.contains(MobEffects.SLOW_FALLING)){
                    return Component.translatable("immersiveslumber.message.effect_slow_falling");
                }else return Component.translatable("immersiveslumber.message.effect_no_slow_falling");

            }
        });
          //22、检查buff
        playerMessageConditions.add(new MessageCondition() {
            @Override
            public Component getComponent(MinecraftServer minecraftServer, Player player) {
                HashSet<MobEffect> effects = new HashSet<>();
                for(MobEffectInstance mobEffectInstance :player.getActiveEffects()){
                    effects.add(mobEffectInstance.getEffect());
                }
                if(effects.contains(MobEffects.JUMP)){
                    return Component.translatable("immersiveslumber.message.effect_jump");
                }else return Component.translatable("immersiveslumber.message.effect_no_jump");

            }
        });
          //23、检查buff
        playerMessageConditions.add(new MessageCondition() {
            @Override
            public Component getComponent(MinecraftServer minecraftServer, Player player) {
                HashSet<MobEffect> effects = new HashSet<>();
                for(MobEffectInstance mobEffectInstance :player.getActiveEffects()){
                    effects.add(mobEffectInstance.getEffect());
                }
                if(effects.contains(MobEffects.MOVEMENT_SPEED)){
                    return Component.translatable("immersiveslumber.message.effect_movement");
                }else return Component.translatable("immersiveslumber.message.effect_no_movement");

            }
        });
          //24、检查buff
        playerMessageConditions.add(new MessageCondition() {
            @Override
            public Component getComponent(MinecraftServer minecraftServer, Player player) {
                HashSet<MobEffect> effects = new HashSet<>();
                for(MobEffectInstance mobEffectInstance :player.getActiveEffects()){
                    effects.add(mobEffectInstance.getEffect());
                }
                if(effects.contains(MobEffects.DAMAGE_BOOST)){
                    return Component.translatable("immersiveslumber.message.effect_damage");
                }else return Component.translatable("immersiveslumber.message.effect_no_damage");

            }
        });
          //25、检查buff
        playerMessageConditions.add(new MessageCondition() {
            @Override
            public Component getComponent(MinecraftServer minecraftServer, Player player) {
                HashSet<MobEffect> effects = new HashSet<>();
                for(MobEffectInstance mobEffectInstance :player.getActiveEffects()){
                    effects.add(mobEffectInstance.getEffect());
                }
                if(effects.contains(MobEffects.SLOW_FALLING)){
                    return Component.translatable("immersiveslumber.message.effect_slow_falling");
                }else return Component.translatable("immersiveslumber.message.effect_no_slow_falling");

            }
        });
       
        //26、检查是否着火
        playerMessageConditions.add(new MessageCondition() {
            @Override
            public Component getComponent(MinecraftServer minecraftServer, Player player) {
                if(player.isOnFire()){
                    return Component.translatable("immersiveslumber.message.on_fire");
                }else return null;
            }
        });
        //27

        playerMessageConditions.add(new MessageCondition() {
            @Override
            public Component getComponent(MinecraftServer minecraftServer, Player player) {
                return Component.translatable("immersiveslumber.message.herobrine_eyes");
            }
        });
        playerMessageConditions.add(new MessageCondition() {
            @Override
            public Component getComponent(MinecraftServer minecraftServer, Player player) {
                return Component.translatable("immersiveslumber.message.herobrine");
            }
        });
        playerMessageConditions.add(new MessageCondition() {
            @Override
            public Component getComponent(MinecraftServer minecraftServer, Player player) {
                return Component.translatable("immersiveslumber.message.glitch_teleport");
            }
        });
        playerMessageConditions.add(new MessageCondition() {
            @Override
            public Component getComponent(MinecraftServer minecraftServer, Player player) {
                return Component.translatable("immersiveslumber.message.debug_menu");
            }
        });
        playerMessageConditions.add(new MessageCondition() {
            @Override
            public Component getComponent(MinecraftServer minecraftServer, Player player) {
                NonNullList<ItemStack> stacks = player.getInventory().items;
                int size = stacks.size();
                int full_size=0;
                for(ItemStack itemStack : stacks){
                    if(itemStack.is(Items.AIR)||itemStack.isEmpty()){
                        continue;
                    }
                    full_size++;
                }
                if(full_size==size&&full_size!=0) return Component.translatable("immersiveslumber.message.inventory_full");
                else if(full_size!=size && full_size==0){
                    return Component.translatable("immersiveslumber.message.inventory_empty");
                }else return null;
            }
        });
        playerMessageConditions.add(new MessageCondition() {
            @Override
            public Component getComponent(MinecraftServer minecraftServer, Player player) {
                if(player.level().getBlockEntity(player.getSleepingPos().get()) instanceof BedBlockEntity bedBlockEntity){
                    switch (bedBlockEntity.getColor()) {
                        case WHITE -> {
                            return Component.translatable("immersiveslumber.message.bed_white");
                        }
                        case ORANGE -> {
                            return Component.translatable("immersiveslumber.message.bed_orange");
                        }
                        case MAGENTA -> {
                            return Component.translatable("immersiveslumber.message.bed_magenta");
                        }
                        case LIGHT_BLUE -> {
                            return Component.translatable("immersiveslumber.message.bed_light_blue");
                        }
                        case YELLOW -> {
                            return Component.translatable("immersiveslumber.message.bed_yellow");
                        }
                        case LIME -> {
                            return Component.translatable("immersiveslumber.message.bed_lime");
                        }
                        case PINK -> {
                            return Component.translatable("immersiveslumber.message.bed_pink");
                        }
                        case GRAY -> {
                            return Component.translatable("immersiveslumber.message.bed_gray");
                        }
                        case LIGHT_GRAY -> {
                            return Component.translatable("immersiveslumber.message.bed_light_gray");
                        }
                        case CYAN -> {
                            return Component.translatable("immersiveslumber.message.bed_cyan");
                        }
                        case PURPLE -> {
                            return Component.translatable("immersiveslumber.message.bed_purple");
                        }
                        case BLUE -> {
                            return Component.translatable("immersiveslumber.message.bed_blue");
                        }
                        case BROWN -> {
                            return Component.translatable("immersiveslumber.message.bed_brown");
                        }
                        case GREEN -> {
                            return Component.translatable("immersiveslumber.message.bed_green");
                        }
                        case RED -> {
                            return Component.translatable("immersiveslumber.message.bed_red");
                        }
                        case BLACK -> {
                            return Component.translatable("immersiveslumber.message.bed_black");
                        }

                    }
                }
                return null;
            }
        });
        playerMessageConditions.add(new MessageCondition() {
            @Override
            public Component getComponent(MinecraftServer minecraftServer, Player player) {
                return Component.translatable("immersiveslumber.message.also_lonely");
            }
        });
        playerMessageConditions.add(new MessageCondition() {
            @Override
            public Component getComponent(MinecraftServer minecraftServer, Player player) {
                return Component.translatable("immersiveslumber.message.lonely");
            }
        });
        playerMessageConditions.add(new MessageCondition() {
            @Override
            public Component getComponent(MinecraftServer minecraftServer, Player player) {
                return Component.translatable("immersiveslumber.message.peep");
            }
        });
        playerMessageConditions.add(new MessageCondition() {
            @Override
            public Component getComponent(MinecraftServer minecraftServer, Player player) {
                return Component.translatable("immersiveslumber.message.offline");
            }
        });
        playerMessageConditions.add(new MessageCondition() {
            @Override
            public Component getComponent(MinecraftServer minecraftServer, Player player) {
                return Component.translatable("immersiveslumber.message.offline");
            }
        });
        playerMessageConditions.add(new MessageCondition() {
            @Override
            public Component getComponent(MinecraftServer minecraftServer, Player player) {
                return Component.translatable("immersiveslumber.message.empty_village");
            }
        });
        playerMessageConditions.add(new MessageCondition() {
            @Override
            public Component getComponent(MinecraftServer minecraftServer, Player player) {
                return Component.translatable("immersiveslumber.message.old_house");
            }
        });
        playerMessageConditions.add(new MessageCondition() {
            @Override
            public Component getComponent(MinecraftServer minecraftServer, Player player) {
                return Component.translatable("immersiveslumber.message.broken_tool");
            }
        });
        playerMessageConditions.add(new MessageCondition() {
            @Override
            public Component getComponent(MinecraftServer minecraftServer, Player player) {
                return Component.translatable("immersiveslumber.message.broken_tool");
            }
        });
        playerMessageConditions.add(new MessageCondition() {
            @Override
            public Component getComponent(MinecraftServer minecraftServer, Player player) {
                return Component.translatable("immersiveslumber.message.silent_zombie");
            }
        });
        playerMessageConditions.add(new MessageCondition() {
            @Override
            public Component getComponent(MinecraftServer minecraftServer, Player player) {
                return Component.translatable("immersiveslumber.message.rich");
            }
        });
        playerMessageConditions.add(new MessageCondition() {
            @Override
            public Component getComponent(MinecraftServer minecraftServer, Player player) {
                return Component.translatable("immersiveslumber.message.block_tree");
            }
        });
        playerMessageConditions.add(new MessageCondition() {
            @Override
            public Component getComponent(MinecraftServer minecraftServer, Player player) {
                return Component.translatable("immersiveslumber.message.rainy_chest");
            }
        });
        playerMessageConditions.add(new MessageCondition() {
            @Override
            public Component getComponent(MinecraftServer minecraftServer, Player player) {
                return Component.translatable("immersiveslumber.message.broken_pickaxe");
            }
        });
        playerMessageConditions.add(new MessageCondition() {
            @Override
            public Component getComponent(MinecraftServer minecraftServer, Player player) {
                return Component.translatable("immersiveslumber.message.ender_chest_secret");
            }
        });
        playerMessageConditions.add(new MessageCondition() {
            @Override
            public Component getComponent(MinecraftServer minecraftServer, Player player) {
                return Component.translatable("immersiveslumber.message.herobrine_selfie");
            }
        });
        playerMessageConditions.add(new MessageCondition() {
            @Override
            public Component getComponent(MinecraftServer minecraftServer, Player player) {
                return Component.translatable("immersiveslumber.message.bucket_lonely");
            }
        });
        playerMessageConditions.add(new MessageCondition() {
            @Override
            public Component getComponent(MinecraftServer minecraftServer, Player player) {
                return Component.translatable("immersiveslumber.message.fishing_rod_lie");
            }
        });
        playerMessageConditions.add(new MessageCondition() {
            @Override
            public Component getComponent(MinecraftServer minecraftServer, Player player) {
                return Component.translatable("immersiveslumber.message.dirt_house");
            }
        });
        playerMessageConditions.add(new MessageCondition() {
            @Override
            public Component getComponent(MinecraftServer minecraftServer, Player player) {
                return Component.translatable("immersiveslumber.message.ghast_tear");
            }
        });
        playerMessageConditions.add(new MessageCondition() {
            @Override
            public Component getComponent(MinecraftServer minecraftServer, Player player) {
                return Component.translatable("immersiveslumber.message.brewing_fail");
            }
        });
        playerMessageConditions.add(new MessageCondition() {
            @Override
            public Component getComponent(MinecraftServer minecraftServer, Player player) {
                return Component.translatable("immersiveslumber.message.villager_hmm");
            }
        });
        playerMessageConditions.add(new MessageCondition() {
            @Override
            public Component getComponent(MinecraftServer minecraftServer, Player player) {
                return Component.translatable("immersiveslumber.message.spyglass_zoom");
            }
        });
        playerMessageConditions.add(new MessageCondition() {
            @Override
            public Component getComponent(MinecraftServer minecraftServer, Player player) {
                return Component.translatable("immersiveslumber.message.fox_steal");
            }
        });
        playerMessageConditions.add(new MessageCondition() {
            @Override
            public Component getComponent(MinecraftServer minecraftServer, Player player) {
                return Component.translatable("immersiveslumber.message.end_dragon_respawn");
            }
        });
        playerMessageConditions.add(new MessageCondition() {
            @Override
            public Component getComponent(MinecraftServer minecraftServer, Player player) {
                return Component.translatable("immersiveslumber.message.waterflow_disaster");
            }
        });
        playerMessageConditions.add(new MessageCondition() {
            @Override
            public Component getComponent(MinecraftServer minecraftServer, Player player) {
                return Component.translatable("immersiveslumber.message.file_number");
            }
        });
        playerMessageConditions.add(new MessageCondition() {
            @Override
            public Component getComponent(MinecraftServer minecraftServer, Player player) {
                return Component.translatable("immersiveslumber.message.yuzu");
            }
        });
        playerMessageConditions.add(new MessageCondition() {
            @Override
            public Component getComponent(MinecraftServer minecraftServer, Player player) {
                return Component.translatable("immersiveslumber.message.ddl");
            }
        });
        playerMessageConditions.add(new MessageCondition() {
            @Override
            public Component getComponent(MinecraftServer minecraftServer, Player player) {
                return Component.translatable("immersiveslumber.message.strange_smell");
            }
        });
        playerMessageConditions.add(new MessageCondition() {
            @Override
            public Component getComponent(MinecraftServer minecraftServer, Player player) {
                return Component.translatable("immersiveslumber.message.mining_fatigue");
            }
        });
        playerMessageConditions.add(new MessageCondition() {
            @Override
            public Component getComponent(MinecraftServer minecraftServer, Player player) {
                return Component.translatable("immersiveslumber.message.redstone_insomnia");
            }
        });
        playerMessageConditions.add(new MessageCondition() {
            @Override
            public Component getComponent(MinecraftServer minecraftServer, Player player) {
                return Component.translatable("immersiveslumber.message.head_sharp");
            }
        });
        playerMessageConditions.add(new MessageCondition() {
            @Override
            public Component getComponent(MinecraftServer minecraftServer, Player player) {
                return Component.translatable("immersiveslumber.message.reopen");
            }
        });
        playerMessageConditions.add(new MessageCondition() {
            @Override
            public Component getComponent(MinecraftServer minecraftServer, Player player) {
                return Component.translatable("immersiveslumber.message.respawn_identity");
            }
        });
        playerMessageConditions.add(new MessageCondition() {
            @Override
            public Component getComponent(MinecraftServer minecraftServer, Player player) {
                return Component.translatable("immersiveslumber.message.first_steve");
            }
        });
        playerMessageConditions.add(new MessageCondition() {
            @Override
            public Component getComponent(MinecraftServer minecraftServer, Player player) {
                return Component.translatable("immersiveslumber.message.creeper_nightmare");
            }
        });
        playerMessageConditions.add(new MessageCondition() {
            @Override
            public Component getComponent(MinecraftServer minecraftServer, Player player) {
                return Component.translatable("immersiveslumber.message.chicken_question");
            }
        });
        playerMessageConditions.add(new MessageCondition() {
            @Override
            public Component getComponent(MinecraftServer minecraftServer, Player player) {
                return Component.translatable("immersiveslumber.message.redstone_fate");
            }
        });
        playerMessageConditions.add(new MessageCondition() {
            @Override
            public Component getComponent(MinecraftServer minecraftServer, Player player) {
                return Component.translatable("immersiveslumber.message.ender_physics");
            }
        });
        playerMessageConditions.add(new MessageCondition() {
            @Override
            public Component getComponent(MinecraftServer minecraftServer, Player player) {
                return Component.translatable("immersiveslumber.message.villager_soul");
            }
        });
        playerMessageConditions.add(new MessageCondition() {
            @Override
            public Component getComponent(MinecraftServer minecraftServer, Player player) {
                return Component.translatable("immersiveslumber.message.herobrine_myth");
            }
        });
        playerMessageConditions.add(new MessageCondition() {
            @Override
            public Component getComponent(MinecraftServer minecraftServer, Player player) {
                return Component.translatable("immersiveslumber.message.mining_loop");
            }
        });
        playerMessageConditions.add(new MessageCondition() {
            @Override
            public Component getComponent(MinecraftServer minecraftServer, Player player) {
                return Component.translatable("immersiveslumber.message.cow_factory");
            }
        });
        playerMessageConditions.add(new MessageCondition() {
            @Override
            public Component getComponent(MinecraftServer minecraftServer, Player player) {
                return Component.translatable("immersiveslumber.message.chest_clutter");
            }
        });
        playerMessageConditions.add(new MessageCondition() {
            @Override
            public Component getComponent(MinecraftServer minecraftServer, Player player) {
                return Component.translatable("immersiveslumber.message.ender_chest_world");
            }
        });
        playerMessageConditions.add(new MessageCondition() {
            @Override
            public Component getComponent(MinecraftServer minecraftServer, Player player) {
                return Component.translatable("immersiveslumber.message.villager_farm");
            }
        });
        playerMessageConditions.add(new MessageCondition() {
            @Override
            public Component getComponent(MinecraftServer minecraftServer, Player player) {
                ServerPlayerGameMode serverPlayerGameMode = ((ServerPlayer) player).gameMode;
                if(serverPlayerGameMode.getGameModeForPlayer() == GameType.SURVIVAL){
                    return Component.translatable("immersiveslumber.message.creative_service_steve");
                }else if (serverPlayerGameMode.getGameModeForPlayer() == GameType.ADVENTURE){
                    return Component.translatable("immersiveslumber.message.service_adventure_steve");
                }
                return null;
            }
        });
        playerMessageConditions.add(new MessageCondition() {
            @Override
            public Component getComponent(MinecraftServer minecraftServer, Player player) {
                return Component.translatable("immersiveslumber.message.hardcore_ghost");
            }
        });
        playerMessageConditions.add(new MessageCondition() {
            @Override
            public Component getComponent(MinecraftServer minecraftServer, Player player) {
                return Component.translatable("immersiveslumber.message.creeper_math");
            }
        });
        playerMessageConditions.add(new MessageCondition() {
            @Override
            public Component getComponent(MinecraftServer minecraftServer, Player player) {
                return Component.translatable("immersiveslumber.message.how_many_mods");
            }
        });
        playerMessageConditions.add(new MessageCondition() {
            @Override
            public Component getComponent(MinecraftServer minecraftServer, Player player) {
                return Component.translatable("immersiveslumber.message.chorus_fruit");
            }
        });
        playerMessageConditions.add(new MessageCondition() {
            @Override
            public Component getComponent(MinecraftServer minecraftServer, Player player) {
                return Component.translatable("immersiveslumber.message.bucket_lava");
            }
        });
        playerMessageConditions.add(new MessageCondition() {
            @Override
            public Component getComponent(MinecraftServer minecraftServer, Player player) {
                return Component.translatable("immersiveslumber.message.fishing_loot");
            }
        });
        playerMessageConditions.add(new MessageCondition() {
            @Override
            public Component getComponent(MinecraftServer minecraftServer, Player player) {
                return Component.translatable("immersiveslumber.message.portal_identity");
            }
        });
        playerMessageConditions.add(new MessageCondition() {
            @Override
            public Component getComponent(MinecraftServer minecraftServer, Player player) {
                return Component.translatable("immersiveslumber.message.loot_desire");
            }
        });
        playerMessageConditions.add(new MessageCondition() {
            @Override
            public Component getComponent(MinecraftServer minecraftServer, Player player) {
                return Component.translatable("immersiveslumber.message.final_question");
            }
        });
        playerMessageConditions.add(new MessageCondition() {
            @Override
            public Component getComponent(MinecraftServer minecraftServer, Player player) {
                return Component.translatable("immersiveslumber.message.quit_reality");
            }
        });
        playerMessageConditions.add(new MessageCondition() {
            @Override
            public Component getComponent(MinecraftServer minecraftServer, Player player) {
                return Component.translatable("immersiveslumber.message.backup_soul");
            }
        });
        playerMessageConditions.add(new MessageCondition() {
            @Override
            public Component getComponent(MinecraftServer minecraftServer, Player player) {
                return Component.translatable("immersiveslumber.message.code_god");
            }
        });
        playerMessageConditions.add(new MessageCondition() {
            @Override
            public Component getComponent(MinecraftServer minecraftServer, Player player) {
                return Component.translatable("immersiveslumber.message.sunset_save");
            }
        });
        playerMessageConditions.add(new MessageCondition() {
            @Override
            public Component getComponent(MinecraftServer minecraftServer, Player player) {
                return Component.translatable("immersiveslumber.message.slime_split");
            }
        });
        playerMessageConditions.add(new MessageCondition() {
            @Override
            public Component getComponent(MinecraftServer minecraftServer, Player player) {
                return Component.translatable("immersiveslumber.message.enderman_etiquette");
            }
        });
        playerMessageConditions.add(new MessageCondition() {
            @Override
            public Component getComponent(MinecraftServer minecraftServer, Player player) {
                return Component.translatable("immersiveslumber.message.command_block");
            }
        });
        playerMessageConditions.add(new MessageCondition() {
            @Override
            public Component getComponent(MinecraftServer minecraftServer, Player player) {
                return Component.translatable("immersiveslumber.message.wither_rose");
            }
        });
        playerMessageConditions.add(new MessageCondition() {
            @Override
            public Component getComponent(MinecraftServer minecraftServer, Player player) {
                return Component.translatable("immersiveslumber.message.skin_identity");
            }
        });
        playerMessageConditions.add(new MessageCondition() {
            @Override
            public Component getComponent(MinecraftServer minecraftServer, Player player) {
                return Component.translatable("immersiveslumber.message.void_fall");
            }
        });
        playerMessageConditions.add(new MessageCondition() {
            @Override
            public Component getComponent(MinecraftServer minecraftServer, Player player) {
                return Component.translatable("immersiveslumber.message.soul_sand");
            }
        });
        playerMessageConditions.add(new MessageCondition() {
            @Override
            public Component getComponent(MinecraftServer minecraftServer, Player player) {
                return Component.translatable("immersiveslumber.message.banner_pattern");
            }
        });
        playerMessageConditions.add(new MessageCondition() {
            @Override
            public Component getComponent(MinecraftServer minecraftServer, Player player) {
                return Component.translatable("immersiveslumber.message.raid_farm");
            }
        });
        playerMessageConditions.add(new MessageCondition() {
            @Override
            public Component getComponent(MinecraftServer minecraftServer, Player player) {
                return Component.translatable("immersiveslumber.message.ancient_debris");
            }
        });
        playerMessageConditions.add(new MessageCondition() {
            @Override
            public Component getComponent(MinecraftServer minecraftServer, Player player) {
                return Component.translatable("immersiveslumber.message.final_chest");
            }
        });
        playerMessageConditions.add(new MessageCondition() {
            @Override
            public Component getComponent(MinecraftServer minecraftServer, Player player) {
                return Component.translatable("immersiveslumber.message.horseshoe_repair");
            }
        });
        playerMessageConditions.add(new MessageCondition() {
            @Override
            public Component getComponent(MinecraftServer minecraftServer, Player player) {
                return Component.translatable("immersiveslumber.message.tomorrow_eat");
            }
        });
         playerMessageConditions.add(new MessageCondition() {
            @Override
            public Component getComponent(MinecraftServer minecraftServer, Player player) {
                return Component.translatable("immersiveslumber.message.tomorrow_morning_eat");
            }
        });
         playerMessageConditions.add(new MessageCondition() {
            @Override
            public Component getComponent(MinecraftServer minecraftServer, Player player) {
                return Component.translatable("immersiveslumber.message.noon_tomorrow_eat");
            }
        });
         playerMessageConditions.add(new MessageCondition() {
            @Override
            public Component getComponent(MinecraftServer minecraftServer, Player player) {
                return Component.translatable("immersiveslumber.message.tomorrow_night_eat");
            }
        });
        playerMessageConditions.add(new MessageCondition() {
            @Override
            public Component getComponent(MinecraftServer minecraftServer, Player player) {
                if(ModList.get().isLoaded("everyxhotpot")){
                    return Component.translatable("immersiveslumber.message.have_hotpot");
                }else {
                    return Component.translatable("immersiveslumber.message.no_hotpot");
                }
            }
        });
        playerMessageConditions.add(new MessageCondition() {
            @Override
            public Component getComponent(MinecraftServer minecraftServer, Player player) {
                return Component.translatable("immersiveslumber.message.name_tag");
            }
        });
        playerMessageConditions.add(new MessageCondition() {
            @Override
            public Component getComponent(MinecraftServer minecraftServer, Player player) {
                return Component.translatable("immersiveslumber.message.chest_boat");
            }
        });
        playerMessageConditions.add(new MessageCondition() {
            @Override
            public Component getComponent(MinecraftServer minecraftServer, Player player) {
                return Component.translatable("immersiveslumber.message.fisher_lost");
            }
        });
        playerMessageConditions.add(new MessageCondition() {
            @Override
            public Component getComponent(MinecraftServer minecraftServer, Player player) {
                if(player.level().isClientSide()) return null;
                ServerPlayer serverPlayer = ((ServerPlayer) player);
                int sleepCount = serverPlayer.getStats().getValue(Stats.CUSTOM.get(ModStats.SLEEP_COUNT.get()));
                return Component.translatable("immersiveslumber.message.sleep_count",sleepCount) ;
            }
        });
        playerMessageConditions.add(new MessageCondition() {
            @Override
            public Component getComponent(MinecraftServer minecraftServer, Player player) {
                return Component.translatable("immersiveslumber.message.character_awake");
            }
        });
        playerMessageConditions.add(new MessageCondition() {
            @Override
            public Component getComponent(MinecraftServer minecraftServer, Player player) {
                return Component.translatable("immersiveslumber.message.loot_rng");
            }
        });
        playerMessageConditions.add(new MessageCondition() {
            @Override
            public Component getComponent(MinecraftServer minecraftServer, Player player) {
                return Component.translatable("immersiveslumber.message.sleep_be_block");
            }
        });
        playerMessageConditions.add(new MessageCondition() {
            @Override
            public Component getComponent(MinecraftServer minecraftServer, Player player) {
                return Component.translatable("immersiveslumber.message.sun_quilt");
            }
        });
        playerMessageConditions.add(new MessageCondition() {
            @Override
            public Component getComponent(MinecraftServer minecraftServer, Player player) {
                return Component.translatable("immersiveslumber.message.pillow_bread");
            }
        });
        playerMessageConditions.add(new MessageCondition() {
            @Override
            public Component getComponent(MinecraftServer minecraftServer, Player player) {
                return Component.translatable("immersiveslumber.message.nap_strategy");
            }
        });
        playerMessageConditions.add(new MessageCondition() {
            @Override
            public Component getComponent(MinecraftServer minecraftServer, Player player) {
                return Component.translatable("immersiveslumber.message.eternal_day");
            }
        });
        playerMessageConditions.add(new MessageCondition() {
            @Override
            public Component getComponent(MinecraftServer minecraftServer, Player player) {
                return Component.translatable("immersiveslumber.message.slime_mattress");
            }
        });
        playerMessageConditions.add(new MessageCondition() {
            @Override
            public Component getComponent(MinecraftServer minecraftServer, Player player) {
                return Component.translatable("immersiveslumber.message.villager_nightmare");
            }
        });
        playerMessageConditions.add(new MessageCondition() {
            @Override
            public Component getComponent(MinecraftServer minecraftServer, Player player) {
                return Component.translatable("immersiveslumber.message.schrodinger_bed");
            }
        });
        playerMessageConditions.add(new MessageCondition() {
            @Override
            public Component getComponent(MinecraftServer minecraftServer, Player player) {
                return Component.translatable("immersiveslumber.message.dream_economy");
            }
        });
        playerMessageConditions.add(new MessageCondition() {
            @Override
            public Component getComponent(MinecraftServer minecraftServer, Player player) {
                return Component.translatable("immersiveslumber.message.nap_tax");
            }
        });
        playerMessageConditions.add(new MessageCondition() {
            @Override
            public Component getComponent(MinecraftServer minecraftServer, Player player) {
                return Component.translatable("immersiveslumber.message.smart_pillow");
            }
        });
        playerMessageConditions.add(new MessageCondition() {
            @Override
            public Component getComponent(MinecraftServer minecraftServer, Player player) {
                return Component.translatable("immersiveslumber.message.dream_buffet");
            }
        });
        playerMessageConditions.add(new MessageCondition() {
            @Override
            public Component getComponent(MinecraftServer minecraftServer, Player player) {
                return Component.translatable("immersiveslumber.message.block_consciousness");
            }
        });
        playerMessageConditions.add(new MessageCondition() {
            @Override
            public Component getComponent(MinecraftServer minecraftServer, Player player) {
                return Component.translatable("immersiveslumber.message.dream_monopoly");
            }
        });
        playerMessageConditions.add(new MessageCondition() {
            @Override
            public Component getComponent(MinecraftServer minecraftServer, Player player) {
                return Component.translatable("immersiveslumber.message.nether_sacrifice");
            }
        });
        playerMessageConditions.add(new MessageCondition() {
            @Override
            public Component getComponent(MinecraftServer minecraftServer, Player player) {
                List<ServerPlayer> players = minecraftServer.getPlayerList().getPlayers();
                if(minecraftServer.getPlayerList().getPlayers().size() == (int) players.stream().filter(ServerPlayer::isSleeping).count()){
                    return Component.translatable("immersiveslumber.message.quantum_snore");
                }
                return null;
            }
        });
        playerMessageConditions.add(new MessageCondition() {
            @Override
            public Component getComponent(MinecraftServer minecraftServer, Player player) {
                return Component.translatable("immersiveslumber.message.stack_overflow");
            }
        });
        playerMessageConditions.add(new MessageCondition() {
            @Override
            public Component getComponent(MinecraftServer minecraftServer, Player player) {
                return Component.translatable("immersiveslumber.message.ranked_nap");
            }
        });
        playerMessageConditions.add(new MessageCondition() {
            @Override
            public Component getComponent(MinecraftServer minecraftServer, Player player) {
                return Component.translatable("immersiveslumber.message.cthulhu_lullaby");
            }
        });
        playerMessageConditions.add(new MessageCondition() {
            @Override
            public Component getComponent(MinecraftServer minecraftServer, Player player) {
                return Component.translatable("immersiveslumber.message.elder_blanket");
            }
        });
        playerMessageConditions.add(new MessageCondition() {
            @Override
            public Component getComponent(MinecraftServer minecraftServer, Player player) {
                return Component.translatable("immersiveslumber.message.tictok_pillow");
            }
        });
        playerMessageConditions.add(new MessageCondition() {
            @Override
            public Component getComponent(MinecraftServer minecraftServer, Player player) {
                return Component.translatable("immersiveslumber.message.fibonacci_sleep");
            }
        });
        playerMessageConditions.add(new MessageCondition() {
            @Override
            public Component getComponent(MinecraftServer minecraftServer, Player player) {
                return Component.translatable("immersiveslumber.message.quantum_clock");
            }
        });
        playerMessageConditions.add(new MessageCondition() {
            @Override
            public Component getComponent(MinecraftServer minecraftServer, Player player) {
                return Component.translatable("immersiveslumber.message.performance_art");
            }
        });
        playerMessageConditions.add(new MessageCondition() {
            @Override
            public Component getComponent(MinecraftServer minecraftServer, Player player) {
                return Component.translatable("immersiveslumber.message.5g_pillow");
            }
        });
        playerMessageConditions.add(new MessageCondition() {
            @Override
            public Component getComponent(MinecraftServer minecraftServer, Player player) {
                return Component.translatable("immersiveslumber.message.blockchain_blanket");
            }
        });
        playerMessageConditions.add(new MessageCondition() {
            @Override
            public Component getComponent(MinecraftServer minecraftServer, Player player) {
                return Component.translatable("immersiveslumber.message.hololive_dream");
            }
        });
        playerMessageConditions.add(new MessageCondition() {
            @Override
            public Component getComponent(MinecraftServer minecraftServer, Player player) {
                return Component.translatable("immersiveslumber.message.netherite_pillow");
            }
        });
        playerMessageConditions.add(new MessageCondition() {
            @Override
            public Component getComponent(MinecraftServer minecraftServer, Player player) {
                return Component.translatable("immersiveslumber.message.chorus_fruit_tea");
            }
        });
        playerMessageConditions.add(new MessageCondition() {
            @Override
            public Component getComponent(MinecraftServer minecraftServer, Player player) {
                return Component.translatable("immersiveslumber.message.solipsism_sleep");
            }
        });
        playerMessageConditions.add(new MessageCondition() {
            @Override
            public Component getComponent(MinecraftServer minecraftServer, Player player) {
                return Component.translatable("immersiveslumber.message.nft_pillow");
            }
        });
        playerMessageConditions.add(new MessageCondition() {
            @Override
            public Component getComponent(MinecraftServer minecraftServer, Player player) {
                return Component.translatable("immersiveslumber.message.entropy_bed");
            }
        });
        playerMessageConditions.add(new MessageCondition() {
            @Override
            public Component getComponent(MinecraftServer minecraftServer, Player player) {
                return Component.translatable("immersiveslumber.message.kafka_dream");
            }
        });
        playerMessageConditions.add(new MessageCondition() {
            @Override
            public Component getComponent(MinecraftServer minecraftServer, Player player) {
                return Component.translatable("immersiveslumber.message.existential_duvet");
            }
        });
        playerMessageConditions.add(new MessageCondition() {
            @Override
            public Component getComponent(MinecraftServer minecraftServer, Player player) {
                return Component.translatable("immersiveslumber.message.marvel_bed");
            }
        });
        playerMessageConditions.add(new MessageCondition() {
            @Override
            public Component getComponent(MinecraftServer minecraftServer, Player player) {
                return Component.translatable("immersiveslumber.message.blue_screen_sleep");
            }
        });
        playerMessageConditions.add(new MessageCondition() {
            @Override
            public Component getComponent(MinecraftServer minecraftServer, Player player) {
                return Component.translatable("immersiveslumber.message.thought_police");
            }
        });
        playerMessageConditions.add(new MessageCondition() {
            @Override
            public Component getComponent(MinecraftServer minecraftServer, Player player) {
                return Component.translatable("immersiveslumber.message.panopticon");
            }
        });
        playerMessageConditions.add(new MessageCondition() {
            @Override
            public Component getComponent(MinecraftServer minecraftServer, Player player) {
                return Component.translatable("immersiveslumber.message.tarot_bed");
            }
        });
        playerMessageConditions.add(new MessageCondition() {
            @Override
            public Component getComponent(MinecraftServer minecraftServer, Player player) {
                return Component.translatable("immersiveslumber.message.un_sleep");
            }
        });
        playerMessageConditions.add(new MessageCondition() {
            @Override
            public Component getComponent(MinecraftServer minecraftServer, Player player) {
                return Component.translatable("immersiveslumber.message.algorithm_god");
            }
        });
        playerMessageConditions.add(new MessageCondition() {
            @Override
            public Component getComponent(MinecraftServer minecraftServer, Player player) {
                return Component.translatable("immersiveslumber.message.mind_cloud");
            }
        });
        playerMessageConditions.add(new MessageCondition() {
            @Override
            public Component getComponent(MinecraftServer minecraftServer, Player player) {
                return Component.translatable("immersiveslumber.message.schrodinger_cat");
            }
        });
        playerMessageConditions.add(new MessageCondition() {
            @Override
            public Component getComponent(MinecraftServer minecraftServer, Player player) {
                return Component.translatable("immersiveslumber.message.strange_smell_sign");
            }
        });
        playerMessageConditions.add(new MessageCondition() {
            @Override
            public Component getComponent(MinecraftServer minecraftServer, Player player) {
                return Component.translatable("immersiveslumber.message.doge_sleep");
            }
        });
        playerMessageConditions.add(new MessageCondition() {
            @Override
            public Component getComponent(MinecraftServer minecraftServer, Player player) {
                return Component.translatable("immersiveslumber.message.breaking_fourth_wall");
            }
        });
        playerMessageConditions.add(new MessageCondition() {
            @Override
            public Component getComponent(MinecraftServer minecraftServer, Player player) {
                return Component.translatable("immersiveslumber.message.quantum_leap");
            }
        });
        playerMessageConditions.add(new MessageCondition() {
            @Override
            public Component getComponent(MinecraftServer minecraftServer, Player player) {
                return Component.translatable("immersiveslumber.message.steam_dream");
            }
        });
        playerMessageConditions.add(new MessageCondition() {
            @Override
            public Component getComponent(MinecraftServer minecraftServer, Player player) {
                return Component.translatable("immersiveslumber.message.mobius_sleep");
            }
        });
        playerMessageConditions.add(new MessageCondition() {
            @Override
            public Component getComponent(MinecraftServer minecraftServer, Player player) {
                return Component.translatable("immersiveslumber.message.topology_curse");
            }
        });
        playerMessageConditions.add(new MessageCondition() {
            @Override
            public Component getComponent(MinecraftServer minecraftServer, Player player) {
                return Component.translatable("immersiveslumber.message.dig_sleep");
            }
        });
        playerMessageConditions.add(new MessageCondition() {
            @Override
            public Component getComponent(MinecraftServer minecraftServer, Player player) {
                return Component.translatable("immersiveslumber.message.time_capsule");
            }
        });
        playerMessageConditions.add(new MessageCondition() {
            @Override
            public Component getComponent(MinecraftServer minecraftServer, Player player) {
                return Component.translatable("immersiveslumber.message.chekhov_gun");
            }
        });
        playerMessageConditions.add(new MessageCondition() {
            @Override
            public Component getComponent(MinecraftServer minecraftServer, Player player) {
                return Component.translatable("immersiveslumber.message.shakespeare");
            }
        });
        playerMessageConditions.add(new MessageCondition() {
            @Override
            public Component getComponent(MinecraftServer minecraftServer, Player player) {
                return Component.translatable("immersiveslumber.message.infinite_loop");
            }
        });
        playerMessageConditions.add(new MessageCondition() {
            @Override
            public Component getComponent(MinecraftServer minecraftServer, Player player) {
                return Component.translatable("immersiveslumber.message.stack_collapse");
            }
        });
        playerMessageConditions.add(new MessageCondition() {
            @Override
            public Component getComponent(MinecraftServer minecraftServer, Player player) {
                return Component.translatable("immersiveslumber.message.rickroll_bed");
            }
        });
        playerMessageConditions.add(new MessageCondition() {
            @Override
            public Component getComponent(MinecraftServer minecraftServer, Player player) {
                return Component.translatable("immersiveslumber.message.uwu_pillow");
            }
        });
        playerMessageConditions.add(new MessageCondition() {
            @Override
            public Component getComponent(MinecraftServer minecraftServer, Player player) {
                return Component.translatable("immersiveslumber.message.null_pointer");
            }
        });
        playerMessageConditions.add(new MessageCondition() {
            @Override
            public Component getComponent(MinecraftServer minecraftServer, Player player) {
                return Component.translatable("immersiveslumber.message.tinder_bed");
            }
        });
        playerMessageConditions.add(new MessageCondition() {
            @Override
            public Component getComponent(MinecraftServer minecraftServer, Player player) {
                return Component.translatable("immersiveslumber.message.van_gogh");
            }
        });
        playerMessageConditions.add(new MessageCondition() {
            @Override
            public Component getComponent(MinecraftServer minecraftServer, Player player) {
                return Component.translatable("immersiveslumber.message.konami_code");
            }
        });
        playerMessageConditions.add(new MessageCondition() {
            @Override
            public Component getComponent(MinecraftServer minecraftServer, Player player) {
                return Component.translatable("immersiveslumber.message.hidden_achievement");
            }
        });
        playerMessageConditions.add(new MessageCondition() {
            @Override
            public Component getComponent(MinecraftServer minecraftServer, Player player) {
                return Component.translatable("immersiveslumber.message.sanity_pillow");
            }
        });
        playerMessageConditions.add(new MessageCondition() {
            @Override
            public Component getComponent(MinecraftServer minecraftServer, Player player) {
                return Component.translatable("immersiveslumber.message.turing_test");
            }
        });
        playerMessageConditions.add(new MessageCondition() {
            @Override
            public Component getComponent(MinecraftServer minecraftServer, Player player) {
                return Component.translatable("immersiveslumber.message.ramen_storm");
            }
        });
        playerMessageConditions.add(new MessageCondition() {
            @Override
            public Component getComponent(MinecraftServer minecraftServer, Player player) {
                return Component.translatable("immersiveslumber.message.404_dream");
            }
        });
        playerMessageConditions.add(new MessageCondition() {
            @Override
            public Component getComponent(MinecraftServer minecraftServer, Player player) {
                return Component.translatable("immersiveslumber.message.kuroko_bed");
            }
        });
        playerMessageConditions.add(new MessageCondition() {
            @Override
            public Component getComponent(MinecraftServer minecraftServer, Player player) {
                return Component.translatable("immersiveslumber.message.superchat");
            }
        });
        playerMessageConditions.add(new MessageCondition() {
            @Override
            public Component getComponent(MinecraftServer minecraftServer, Player player) {
                return Component.translatable("immersiveslumber.message.you_died");
            }
        });
        playerMessageConditions.add(new MessageCondition() {
            @Override
            public Component getComponent(MinecraftServer minecraftServer, Player player) {
                return Component.translatable("immersiveslumber.message.stardew_valley_1");
            }
        });
        playerMessageConditions.add(new MessageCondition() {
            @Override
            public Component getComponent(MinecraftServer minecraftServer, Player player) {
                return Component.translatable("immersiveslumber.message.shakespeare_1");
            }
        });
        playerMessageConditions.add(new MessageCondition() {
            @Override
            public Component getComponent(MinecraftServer minecraftServer, Player player) {
                return Component.translatable("immersiveslumber.message.pokemon_1");
            }
        });
        playerMessageConditions.add(new MessageCondition() {
            @Override
            public Component getComponent(MinecraftServer minecraftServer, Player player) {
                return Component.translatable("immersiveslumber.message.sherlock_1");
            }
        });
        playerMessageConditions.add(new MessageCondition() {
            @Override
            public Component getComponent(MinecraftServer minecraftServer, Player player) {
                return Component.translatable("immersiveslumber.message.buddhism_1");
            }
        });
        playerMessageConditions.add(new MessageCondition() {
            @Override
            public Component getComponent(MinecraftServer minecraftServer, Player player) {
                return Component.translatable("immersiveslumber.message.jazz_1");
            }
        });
        playerMessageConditions.add(new MessageCondition() {
            @Override
            public Component getComponent(MinecraftServer minecraftServer, Player player) {
                return Component.translatable("immersiveslumber.message.hp_1");
            }
        });
        playerMessageConditions.add(new MessageCondition() {
            @Override
            public Component getComponent(MinecraftServer minecraftServer, Player player) {
                return Component.translatable("immersiveslumber.message.lotr_1");
            }
        });
        playerMessageConditions.add(new MessageCondition() {
            @Override
            public Component getComponent(MinecraftServer minecraftServer, Player player) {
                return Component.translatable("immersiveslumber.message.dragon_1");
            }
        });
        playerMessageConditions.add(new MessageCondition() {
            @Override
            public Component getComponent(MinecraftServer minecraftServer, Player player) {
                return Component.translatable("immersiveslumber.message.enderman_tea");
            }
        });
        playerMessageConditions.add(new MessageCondition() {
            @Override
            public Component getComponent(MinecraftServer minecraftServer, Player player) {
                return Component.translatable("immersiveslumber.message.zombie_diet");
            }
        });
        playerMessageConditions.add(new MessageCondition() {
            @Override
            public Component getComponent(MinecraftServer minecraftServer, Player player) {
                return Component.translatable("immersiveslumber.message.mod_launcher");
            }
        });
        playerMessageConditions.add(new MessageCondition() {
            @Override
            public Component getComponent(MinecraftServer minecraftServer, Player player) {
                return Component.translatable("immersiveslumber.message.sheep_cloud");
            }
        });
        playerMessageConditions.add(new MessageCondition() {
            @Override
            public Component getComponent(MinecraftServer minecraftServer, Player player) {
                return Component.translatable("immersiveslumber.message.cat_gravity");
            }
        });
        playerMessageConditions.add(new MessageCondition() {
            @Override
            public Component getComponent(MinecraftServer minecraftServer, Player player) {
                return Component.translatable("immersiveslumber.message.bee_economics");
            }
        });
        playerMessageConditions.add(new MessageCondition() {
            @Override
            public Component getComponent(MinecraftServer minecraftServer, Player player) {
                return Component.translatable("immersiveslumber.message.chunk_error");
            }
        });
        playerMessageConditions.add(new MessageCondition() {
            @Override
            public Component getComponent(MinecraftServer minecraftServer, Player player) {
                return Component.translatable("immersiveslumber.message.mob_spawn");
            }
        });
        playerMessageConditions.add(new MessageCondition() {
            @Override
            public Component getComponent(MinecraftServer minecraftServer, Player player) {
                if(ModList.get().isLoaded("create")) return Component.translatable("immersiveslumber.message.have_create");
                return Component.translatable("immersiveslumber.message.no_create");
            }
        });
        playerMessageConditions.add(new MessageCondition() {
            @Override
            public Component getComponent(MinecraftServer minecraftServer, Player player) {
                return Component.translatable("immersiveslumber.message.melody_magic");
            }
        });
        playerMessageConditions.add(new MessageCondition() {
            @Override
            public Component getComponent(MinecraftServer minecraftServer, Player player) {
                return Component.translatable("immersiveslumber.message.thaumcraft");
            }
        });
        playerMessageConditions.add(new MessageCondition() {
            @Override
            public Component getComponent(MinecraftServer minecraftServer, Player player) {
                return Component.translatable("immersiveslumber.message.sleep_thaumcraft");
            }
        });
        playerMessageConditions.add(new MessageCondition() {
            @Override
            public Component getComponent(MinecraftServer minecraftServer, Player player) {
                return Component.translatable("immersiveslumber.message.thermal_mana");
            }
        });
        playerMessageConditions.add(new MessageCondition() {
            @Override
            public Component getComponent(MinecraftServer minecraftServer, Player player) {
                return Component.translatable("immersiveslumber.message.ic2_wand");
            }
        });
        playerMessageConditions.add(new MessageCondition() {
            @Override
            public Component getComponent(MinecraftServer minecraftServer, Player player) {
                return Component.translatable("immersiveslumber.message.tinkers_blood");
            }
        });
        playerMessageConditions.add(new MessageCondition() {
            @Override
            public Component getComponent(MinecraftServer minecraftServer, Player player) {
                return Component.translatable("immersiveslumber.message.thaumcraft_zombie");
            }
        });
        playerMessageConditions.add(new MessageCondition() {
            @Override
            public Component getComponent(MinecraftServer minecraftServer, Player player) {
                return Component.translatable("immersiveslumber.message.ae2_essentia");
            }
        });
        playerMessageConditions.add(new MessageCondition() {
            @Override
            public Component getComponent(MinecraftServer minecraftServer, Player player) {
                return Component.translatable("immersiveslumber.message.java_best");
            }
        });
        playerMessageConditions.add(new MessageCondition() {
            @Override
            public Component getComponent(MinecraftServer minecraftServer, Player player) {
                return Component.translatable("immersiveslumber.message.optifine_shader");
            }
        });
        playerMessageConditions.add(new MessageCondition() {
            @Override
            public Component getComponent(MinecraftServer minecraftServer, Player player) {
                return Component.translatable("immersiveslumber.message.spaghetti_42");
            }
        });
        playerMessageConditions.add(new MessageCondition() {
            @Override
            public Component getComponent(MinecraftServer minecraftServer, Player player) {
                return Component.translatable("immersiveslumber.message.sleep_master");
            }
        });
        playerMessageConditions.add(new MessageCondition() {
            @Override
            public Component getComponent(MinecraftServer minecraftServer, Player player) {
                return Component.translatable("immersiveslumber.message.nethier_microwave");
            }
        });
        playerMessageConditions.add(new MessageCondition() {
            @Override
            public Component getComponent(MinecraftServer minecraftServer, Player player) {
                return Component.translatable("immersiveslumber.message.netherstar_dousheng");
            }
        });
        playerMessageConditions.add(new MessageCondition() {
            @Override
            public Component getComponent(MinecraftServer minecraftServer, Player player) {
                return Component.translatable("immersiveslumber.message.green_flame");
            }
        });
        playerMessageConditions.add(new MessageCondition() {
            @Override
            public Component getComponent(MinecraftServer minecraftServer, Player player) {
                return Component.translatable("immersiveslumber.message.lava_birth");
            }
        });
        playerMessageConditions.add(new MessageCondition() {
            @Override
            public Component getComponent(MinecraftServer minecraftServer, Player player) {
                return Component.translatable("immersiveslumber.message.firework_skill");
            }
        });
        playerMessageConditions.add(new MessageCondition() {
            @Override
            public Component getComponent(MinecraftServer minecraftServer, Player player) {
                return Component.translatable("immersiveslumber.message.wedding_chaos");
            }
        });
        playerMessageConditions.add(new MessageCondition() {
            @Override
            public Component getComponent(MinecraftServer minecraftServer, Player player) {
                return Component.translatable("immersiveslumber.message.ring_laoyao");
            }
        });
        playerMessageConditions.add(new MessageCondition() {
            @Override
            public Component getComponent(MinecraftServer minecraftServer, Player player) {
                return Component.translatable("immersiveslumber.message.tavern_quest");
            }
        });
        playerMessageConditions.add(new MessageCondition() {
            @Override
            public Component getComponent(MinecraftServer minecraftServer, Player player) {
                return Component.translatable("immersiveslumber.message.isekai_truck");
            }
        });
        playerMessageConditions.add(new MessageCondition() {
            @Override
            public Component getComponent(MinecraftServer minecraftServer, Player player) {
                return Component.translatable("immersiveslumber.message.necronomicon");
            }
        });
        playerMessageConditions.add(new MessageCondition() {
            @Override
            public Component getComponent(MinecraftServer minecraftServer, Player player) {
                return Component.translatable("immersiveslumber.message.enders_library");
            }
        });
        playerMessageConditions.add(new MessageCondition() {
            @Override
            public Component getComponent(MinecraftServer minecraftServer, Player player) {
                return Component.translatable("immersiveslumber.message.charging_portal");
            }
        });
        playerMessageConditions.add(new MessageCondition() {
            @Override
            public Component getComponent(MinecraftServer minecraftServer, Player player) {
                return Component.translatable("immersiveslumber.message.band_groupchat");
            }
        });
        playerMessageConditions.add(new MessageCondition() {
            @Override
            public Component getComponent(MinecraftServer minecraftServer, Player player) {
                return Component.translatable("immersiveslumber.message.hero_network");
            }
        });
        playerMessageConditions.add(new MessageCondition() {
            @Override
            public Component getComponent(MinecraftServer minecraftServer, Player player) {
                return Component.translatable("immersiveslumber.message.vampire_dating");
            }
        });
        playerMessageConditions.add(new MessageCondition() {
            @Override
            public Component getComponent(MinecraftServer minecraftServer, Player player) {
                return Component.translatable("immersiveslumber.message.robot_confession");
            }
        });
        playerMessageConditions.add(new MessageCondition() {
            @Override
            public Component getComponent(MinecraftServer minecraftServer, Player player) {
                return Component.translatable("immersiveslumber.message.rainy_day");
            }
        });
        playerMessageConditions.add(new MessageCondition() {
            @Override
            public Component getComponent(MinecraftServer minecraftServer, Player player) {
                return Component.translatable("immersiveslumber.message.bedrock_dream");
            }
        });
        playerMessageConditions.add(new MessageCondition() {
            @Override
            public Component getComponent(MinecraftServer minecraftServer, Player player) {
                return Component.translatable("immersiveslumber.message.armor_stand");
            }
        });
        playerMessageConditions.add(new MessageCondition() {
            @Override
            public Component getComponent(MinecraftServer minecraftServer, Player player) {
                return Component.translatable("immersiveslumber.message.phantom_origin");
            }
        });
        playerMessageConditions.add(new MessageCondition() {
            @Override
            public Component getComponent(MinecraftServer minecraftServer, Player player) {
                return Component.translatable("immersiveslumber.message.dark_forest");
            }
        });
        playerMessageConditions.add(new MessageCondition() {
            @Override
            public Component getComponent(MinecraftServer minecraftServer, Player player) {
                return Component.translatable("immersiveslumber.message.hogwarts_portal");
            }
        });
        playerMessageConditions.add(new MessageCondition() {
            @Override
            public Component getComponent(MinecraftServer minecraftServer, Player player) {
                return Component.translatable("immersiveslumber.message.dune_worm");
            }
        });
        playerMessageConditions.add(new MessageCondition() {
            @Override
            public Component getComponent(MinecraftServer minecraftServer, Player player) {
                return Component.translatable("immersiveslumber.message.1984_bigbrother");
            }
        });
        playerMessageConditions.add(new MessageCondition() {
            @Override
            public Component getComponent(MinecraftServer minecraftServer, Player player) {
                return Component.translatable("immersiveslumber.message.paradise_lost");
            }
        });
        playerMessageConditions.add(new MessageCondition() {
            @Override
            public Component getComponent(MinecraftServer minecraftServer, Player player) {
                return Component.translatable("immersiveslumber.message.madame_bovary");
            }
        });
        playerMessageConditions.add(new MessageCondition() {
            @Override
            public Component getComponent(MinecraftServer minecraftServer, Player player) {
                return Component.translatable("immersiveslumber.message.madame_bovary");
            }
        });
        playerMessageConditions.add(new MessageCondition() {
            @Override
            public Component getComponent(MinecraftServer minecraftServer, Player player) {
                return Component.translatable("immersiveslumber.message.witch_repent");
            }
        });
        playerMessageConditions.add(new MessageCondition() {
            @Override
            public Component getComponent(MinecraftServer minecraftServer, Player player) {
                return Component.translatable("immersiveslumber.message.cultivation_bedrock");
            }
        });
        playerMessageConditions.add(new MessageCondition() {
            @Override
            public Component getComponent(MinecraftServer minecraftServer, Player player) {
                return Component.translatable("immersiveslumber.message.infinite_copies");
            }
        });
        playerMessageConditions.add(new MessageCondition() {
            @Override
            public Component getComponent(MinecraftServer minecraftServer, Player player) {
                return Component.translatable("immersiveslumber.message.necromancer_note");
            }
        });
        playerMessageConditions.add(new MessageCondition() {
            @Override
            public Component getComponent(MinecraftServer minecraftServer, Player player) {
                return Component.translatable("immersiveslumber.message.cthulhu_fish");
            }
        });
        playerMessageConditions.add(new MessageCondition() {
            @Override
            public Component getComponent(MinecraftServer minecraftServer, Player player) {
                return Component.translatable("immersiveslumber.message.ai_villager");
            }
        });
        playerMessageConditions.add(new MessageCondition() {
            @Override
            public Component getComponent(MinecraftServer minecraftServer, Player player) {
                return Component.translatable("immersiveslumber.message.isekai_achievement");
            }
        });
        playerMessageConditions.add(new MessageCondition() {
            @Override
            public Component getComponent(MinecraftServer minecraftServer, Player player) {
                return Component.translatable("immersiveslumber.message.xianxia_exp");
            }
        });
        playerMessageConditions.add(new MessageCondition() {
            @Override
            public Component getComponent(MinecraftServer minecraftServer, Player player) {
                return Component.translatable("immersiveslumber.message.isekai_quest");
            }
        });
        playerMessageConditions.add(new MessageCondition() {
            @Override
            public Component getComponent(MinecraftServer minecraftServer, Player player) {
                return Component.translatable("immersiveslumber.message.dark_forest_detector");
            }
        });
        playerMessageConditions.add(new MessageCondition() {
            @Override
            public Component getComponent(MinecraftServer minecraftServer, Player player) {
                return Component.translatable("immersiveslumber.message.guild_war");
            }
        });
        playerMessageConditions.add(new MessageCondition() {
            @Override
            public Component getComponent(MinecraftServer minecraftServer, Player player) {
                return Component.translatable("immersiveslumber.message.quantum_tunnel");
            }
        });
        playerMessageConditions.add(new MessageCondition() {
            @Override
            public Component getComponent(MinecraftServer minecraftServer, Player player) {
                return Component.translatable("immersiveslumber.message.cthulhu_83");
            }
        });
        playerMessageConditions.add(new MessageCondition() {
            @Override
            public Component getComponent(MinecraftServer minecraftServer, Player player) {
                return Component.translatable("immersiveslumber.message.moonlight_metabolism");
            }
        });
        playerMessageConditions.add(new MessageCondition() {
            @Override
            public Component getComponent(MinecraftServer minecraftServer, Player player) {
                return Component.translatable("immersiveslumber.message.dopamine_farm");
            }
        });
        playerMessageConditions.add(new MessageCondition() {
            @Override
            public Component getComponent(MinecraftServer minecraftServer, Player player) {
                return Component.translatable("immersiveslumber.message.time_thief");
            }
        });
        playerMessageConditions.add(new MessageCondition() {
            @Override
            public Component getComponent(MinecraftServer minecraftServer, Player player) {
                return Component.translatable("immersiveslumber.message.neural_massage");
            }
        });
        playerMessageConditions.add(new MessageCondition() {
            @Override
            public Component getComponent(MinecraftServer minecraftServer, Player player) {
                return Component.translatable("immersiveslumber.message.rem_reboot");
            }
        });
        playerMessageConditions.add(new MessageCondition() {
            @Override
            public Component getComponent(MinecraftServer minecraftServer, Player player) {
                return Component.translatable("immersiveslumber.message.dream_encrypt");
            }
        });
        playerMessageConditions.add(new MessageCondition() {
            @Override
            public Component getComponent(MinecraftServer minecraftServer, Player player) {
                return Component.translatable("immersiveslumber.message.dreamcoin_mining");
            }
        });
        playerMessageConditions.add(new MessageCondition() {
            @Override
            public Component getComponent(MinecraftServer minecraftServer, Player player) {
                return Component.translatable("immersiveslumber.message.dream_social");
            }
        });
        playerMessageConditions.add(new MessageCondition() {
            @Override
            public Component getComponent(MinecraftServer minecraftServer, Player player) {
                return Component.translatable("immersiveslumber.message.dream_eater");
            }
        });
        playerMessageConditions.add(new MessageCondition() {
            @Override
            public Component getComponent(MinecraftServer minecraftServer, Player player) {
                return Component.translatable("immersiveslumber.message.dopamine_wave");
            }
        });
        playerMessageConditions.add(new MessageCondition() {
            @Override
            public Component getComponent(MinecraftServer minecraftServer, Player player) {
                return Component.translatable("immersiveslumber.message.acceleration_factor",accelerationFactor(minecraftServer));
            }
        });
        playerMessageConditions.add(new MessageCondition() {
            @Override
            public Component getComponent(MinecraftServer minecraftServer, Player player) {
                return Component.translatable("immersiveslumber.message.amygdala_fortress");
            }
        });
        playerMessageConditions.add(new MessageCondition() {
            @Override
            public Component getComponent(MinecraftServer minecraftServer, Player player) {
                return Component.translatable("immersiveslumber.message.world_leave");
            }
        });
        playerMessageConditions.add(new MessageCondition() {
            @Override
            public Component getComponent(MinecraftServer minecraftServer, Player player) {
                return Component.translatable("immersiveslumber.message.world_end");
            }
        });
        playerMessageConditions.add(new MessageCondition() {
            @Override
            public Component getComponent(MinecraftServer minecraftServer, Player player) {
                return Component.translatable("immersiveslumber.message.dreamcoin_mining");
            }
        });
        playerMessageConditions.add(new MessageCondition() {
            @Override
            public Component getComponent(MinecraftServer minecraftServer, Player player) {
                return Component.translatable("immersiveslumber.message.dreamcoin_mining");
            }
        });
        playerMessageConditions.add(new MessageCondition() {
            @Override
            public Component getComponent(MinecraftServer minecraftServer, Player player) {
                return Component.translatable("immersiveslumber.message.dreamcoin_mining");
            }
        });
        playerMessageConditions.add(new MessageCondition() {
            @Override
            public Component getComponent(MinecraftServer minecraftServer, Player player) {
                return Component.translatable("immersiveslumber.message.dreamcoin_mining");
            }
        });
        playerMessageConditions.add(new MessageCondition() {
            @Override
            public Component getComponent(MinecraftServer minecraftServer, Player player) {
                return Component.translatable("immersiveslumber.message.dreamcoin_mining");
            }
        });
        playerMessageConditions.add(new MessageCondition() {
            @Override
            public Component getComponent(MinecraftServer minecraftServer, Player player) {
                return Component.translatable("immersiveslumber.message.dreamcoin_mining");
            }
        });
        playerMessageConditions.add(new MessageCondition() {
            @Override
            public Component getComponent(MinecraftServer minecraftServer, Player player) {
                return Component.translatable("immersiveslumber.message.dreamland_specialities");
            }
        });
        playerMessageConditions.add(new MessageCondition() {
            @Override
            public Component getComponent(MinecraftServer minecraftServer, Player player) {
                return Component.translatable("immersiveslumber.message.skip_work");
            }
        });
        playerMessageConditions.add(new MessageCondition() {
            @Override
            public Component getComponent(MinecraftServer minecraftServer, Player player) {
                return Component.translatable("immersiveslumber.message.climb_mountain");
            }
        });
        playerMessageConditions.add(new MessageCondition() {
            @Override
            public Component getComponent(MinecraftServer minecraftServer, Player player) {
                return Component.translatable("immersiveslumber.message.read_book");
            }
        });
        playerMessageConditions.add(new MessageCondition() {
            @Override
            public Component getComponent(MinecraftServer minecraftServer, Player player) {
                return Component.translatable("immersiveslumber.message.eat_mushroom");
            }
        });
        playerMessageConditions.add(new MessageCondition() {
            @Override
            public Component getComponent(MinecraftServer minecraftServer, Player player) {
                return Component.translatable("immersiveslumber.message.ride_enderdragon");
            }
        });
        playerMessageConditions.add(new MessageCondition() {
            @Override
            public Component getComponent(MinecraftServer minecraftServer, Player player) {
                return Component.translatable("immersiveslumber.message.play_pokemom");
            }
        });
        playerMessageConditions.add(new MessageCondition() {
            @Override
            public Component getComponent(MinecraftServer minecraftServer, Player player) {
                return Component.translatable("immersiveslumber.message.sheep_counter");
            }
        });
        playerMessageConditions.add(new MessageCondition() {
            @Override
            public Component getComponent(MinecraftServer minecraftServer, Player player) {
                return Component.translatable("immersiveslumber.message.golem_thin");
            }
        });
        playerMessageConditions.add(new MessageCondition() {
            @Override
            public Component getComponent(MinecraftServer minecraftServer, Player player) {
                return Component.translatable("immersiveslumber.message.block_heart");
            }
        });
        playerMessageConditions.add(new MessageCondition() {
            @Override
            public Component getComponent(MinecraftServer minecraftServer, Player player) {
                return Component.translatable("immersiveslumber.message.steve_safe");
            }
        });
        playerMessageConditions.add(new MessageCondition() {
            @Override
            public Component getComponent(MinecraftServer minecraftServer, Player player) {
                return Component.translatable("immersiveslumber.message.not_lover");
            }
        });
        playerMessageConditions.add(new MessageCondition() {
            @Override
            public Component getComponent(MinecraftServer minecraftServer, Player player) {
                return Component.translatable("immersiveslumber.message.false_sexual_entrapment");
            }
        });
        playerMessageConditions.add(new MessageCondition() {
            @Override
            public Component getComponent(MinecraftServer minecraftServer, Player player) {
                return Component.translatable("immersiveslumber.message.true_sexual_entrapment");
            }
        });
        playerMessageConditions.add(new MessageCondition() {
            @Override
            public Component getComponent(MinecraftServer minecraftServer, Player player) {
                return Component.translatable("immersiveslumber.message.mc_speet");
            }
        });
        playerMessageConditions.add(new MessageCondition() {
            @Override
            public Component getComponent(MinecraftServer minecraftServer, Player player) {
                return Component.translatable("immersiveslumber.message.buckshot_roulette");
            }
        });
        playerMessageConditions.add(new MessageCondition() {
            @Override
            public Component getComponent(MinecraftServer minecraftServer, Player player) {
                return Component.translatable("immersiveslumber.message.livor_mortis");
            }
        });
        playerMessageConditions.add(new MessageCondition() {
            @Override
            public Component getComponent(MinecraftServer minecraftServer, Player player) {
                return Component.translatable("immersiveslumber.message.land_water");
            }
        });
        playerMessageConditions.add(new MessageCondition() {
            @Override
            public Component getComponent(MinecraftServer minecraftServer, Player player) {
                return Component.translatable("immersiveslumber.message.redstone_nightmare");
            }
        });
        playerMessageConditions.add(new MessageCondition() {
            @Override
            public Component getComponent(MinecraftServer minecraftServer, Player player) {
                return Component.translatable("immersiveslumber.message.village_gossip");
            }
        });
        playerMessageConditions.add(new MessageCondition() {
            @Override
            public Component getComponent(MinecraftServer minecraftServer, Player player) {
                return Component.translatable("immersiveslumber.message.crafting_nap");
            }
        });
        playerMessageConditions.add(new MessageCondition() {
            @Override
            public Component getComponent(MinecraftServer minecraftServer, Player player) {
                return Component.translatable("immersiveslumber.message.enchant_pyjamas");
            }
        });
        playerMessageConditions.add(new MessageCondition() {
            @Override
            public Component getComponent(MinecraftServer minecraftServer, Player player) {
                return Component.translatable("immersiveslumber.message.glow_squid_nightlight");
            }
        });
        playerMessageConditions.add(new MessageCondition() {
            @Override
            public Component getComponent(MinecraftServer minecraftServer, Player player) {
                return Component.translatable("immersiveslumber.message.pillow_fortress");
            }
        });
        playerMessageConditions.add(new MessageCondition() {
            @Override
            public Component getComponent(MinecraftServer minecraftServer, Player player) {
                return Component.translatable("immersiveslumber.message.fishing_snooze");
            }
        });
        playerMessageConditions.add(new MessageCondition() {
            @Override
            public Component getComponent(MinecraftServer minecraftServer, Player player) {
                return Component.translatable("immersiveslumber.message.ender_chest_snack");
            }
        });
        playerMessageConditions.add(new MessageCondition() {
            @Override
            public Component getComponent(MinecraftServer minecraftServer, Player player) {
                return Component.translatable("immersiveslumber.message.quilt_comfortable");
            }
        });
        playerMessageConditions.add(new MessageCondition() {
            @Override
            public Component getComponent(MinecraftServer minecraftServer, Player player) {
                return Component.translatable("immersiveslumber.message.anvil_nightmare");
            }
        });
        playerMessageConditions.add(new MessageCondition() {
            @Override
            public Component getComponent(MinecraftServer minecraftServer, Player player) {
                return Component.translatable("immersiveslumber.message.slime_bed");
            }
        });
        playerMessageConditions.add(new MessageCondition() {
            @Override
            public Component getComponent(MinecraftServer minecraftServer, Player player) {
                return Component.translatable("immersiveslumber.message.obsidian_blanket");
            }
        });
        playerMessageConditions.add(new MessageCondition() {
            @Override
            public Component getComponent(MinecraftServer minecraftServer, Player player) {
                return Component.translatable("immersiveslumber.message.axolotl_lullaby");
            }
        });
        playerMessageConditions.add(new MessageCondition() {
            @Override
            public Component getComponent(MinecraftServer minecraftServer, Player player) {
                return Component.translatable("immersiveslumber.message.shulker_blanket");
            }
        });
        playerMessageConditions.add(new MessageCondition() {
            @Override
            public Component getComponent(MinecraftServer minecraftServer, Player player) {
                return Component.translatable("immersiveslumber.message.conduit_pillow");
            }
        });
        playerMessageConditions.add(new MessageCondition() {
            @Override
            public Component getComponent(MinecraftServer minecraftServer, Player player) {
                return Component.translatable("immersiveslumber.message.glow_ink_dream");
            }
        });
        playerMessageConditions.add(new MessageCondition() {
            @Override
            public Component getComponent(MinecraftServer minecraftServer, Player player) {
                return Component.translatable("immersiveslumber.message.phantom_blanket");
            }
        });
        playerMessageConditions.add(new MessageCondition() {
            @Override
            public Component getComponent(MinecraftServer minecraftServer, Player player) {
                return Component.translatable("immersiveslumber.message.ender_pearl_blanket");
            }
        });
        playerMessageConditions.add(new MessageCondition() {
            @Override
            public Component getComponent(MinecraftServer minecraftServer, Player player) {
                return Component.translatable("immersiveslumber.message.ender_chest_nightmare");
            }
        });
        playerMessageConditions.add(new MessageCondition() {
            @Override
            public Component getComponent(MinecraftServer minecraftServer, Player player) {
                return Component.translatable("immersiveslumber.message.phantom_negotiation");
            }
        });
        playerMessageConditions.add(new MessageCondition() {
            @Override
            public Component getComponent(MinecraftServer minecraftServer, Player player) {
                return Component.translatable("immersiveslumber.message.warden_whisper");
            }
        });
        playerMessageConditions.add(new MessageCondition() {
            @Override
            public Component getComponent(MinecraftServer minecraftServer, Player player) {
                return Component.translatable("immersiveslumber.message.dye_machine");
            }
        });
        playerMessageConditions.add(new MessageCondition() {
            @Override
            public Component getComponent(MinecraftServer minecraftServer, Player player) {
                return Component.translatable("immersiveslumber.message.weather_clean");
            }
        });
        playerMessageConditions.add(new MessageCondition() {
            @Override
            public Component getComponent(MinecraftServer minecraftServer, Player player) {
                return Component.translatable("immersiveslumber.message.baby_breath");
            }
        });
        playerMessageConditions.add(new MessageCondition() {
            @Override
            public Component getComponent(MinecraftServer minecraftServer, Player player) {
                return Component.translatable("immersiveslumber.message.strawberry_harvest");
            }
        });
        playerMessageConditions.add(new MessageCondition() {
            @Override
            public Component getComponent(MinecraftServer minecraftServer, Player player) {
                return Component.translatable("immersiveslumber.message.how_to_describe");
            }
        });
        playerMessageConditions.add(new MessageCondition() {
            @Override
            public Component getComponent(MinecraftServer minecraftServer, Player player) {
                return Component.translatable("immersiveslumber.message.man_bo");
            }
        });
        playerMessageConditions.add(new MessageCondition() {
            @Override
            public Component getComponent(MinecraftServer minecraftServer, Player player) {
                return Component.translatable("immersiveslumber.message.milk_cure");
            }
        });
        playerMessageConditions.add(new MessageCondition() {
            @Override
            public Component getComponent(MinecraftServer minecraftServer, Player player) {
                return Component.translatable("immersiveslumber.message.born_steve");
            }
        });
        playerMessageConditions.add(new MessageCondition() {
            @Override
            public Component getComponent(MinecraftServer minecraftServer, Player player) {
                return Component.translatable("immersiveslumber.message.more_lave");
            }
        });
        playerMessageConditions.add(new MessageCondition() {
            @Override
            public Component getComponent(MinecraftServer minecraftServer, Player player) {
                return Component.translatable("immersiveslumber.message.hermitcraft_envy");
            }
        });
        playerMessageConditions.add(new MessageCondition() {
            @Override
            public Component getComponent(MinecraftServer minecraftServer, Player player) {
                return Component.translatable("immersiveslumber.message.lava_logic");
            }
        });
        playerMessageConditions.add(new MessageCondition() {
            @Override
            public Component getComponent(MinecraftServer minecraftServer, Player player) {
                return Component.translatable("immersiveslumber.message.ender_eyes");
            }
        });
        playerMessageConditions.add(new MessageCondition() {
            @Override
            public Component getComponent(MinecraftServer minecraftServer, Player player) {
                return Component.translatable("immersiveslumber.message.bedrock_philosophy");
            }
        });
        playerMessageConditions.add(new MessageCondition() {
            @Override
            public Component getComponent(MinecraftServer minecraftServer, Player player) {
                return Component.translatable("immersiveslumber.message.chicken_origin");
            }
        });
        playerMessageConditions.add(new MessageCondition() {
            @Override
            public Component getComponent(MinecraftServer minecraftServer, Player player) {
                return Component.translatable("immersiveslumber.message.phantom_insomnia");
            }
        });
        playerMessageConditions.add(new MessageCondition() {
            @Override
            public Component getComponent(MinecraftServer minecraftServer, Player player) {
                return Component.translatable("immersiveslumber.message.anvil_gravity");
            }
        });
        playerMessageConditions.add(new MessageCondition() {
            @Override
            public Component getComponent(MinecraftServer minecraftServer, Player player) {
                return Component.translatable("immersiveslumber.message.bee_algorithm");
            }
        });
        playerMessageConditions.add(new MessageCondition() {
            @Override
            public Component getComponent(MinecraftServer minecraftServer, Player player) {
                return Component.translatable("immersiveslumber.message.shulker_relativity");
            }
        });
        playerMessageConditions.add(new MessageCondition() {
            @Override
            public Component getComponent(MinecraftServer minecraftServer, Player player) {
                return Component.translatable("immersiveslumber.message.concrete_art");
            }
        });
        playerMessageConditions.add(new MessageCondition() {
            @Override
            public Component getComponent(MinecraftServer minecraftServer, Player player) {
                return Component.translatable("immersiveslumber.message.spawn_chunk");
            }
        });
        playerMessageConditions.add(new MessageCondition() {
            @Override
            public Component getComponent(MinecraftServer minecraftServer, Player player) {
                return Component.translatable("immersiveslumber.message.anvil_inflation");
            }
        });
        playerMessageConditions.add(new MessageCondition() {
            @Override
            public Component getComponent(MinecraftServer minecraftServer, Player player) {
                return Component.translatable("immersiveslumber.message.bartering_gacha");
            }
        });
        playerMessageConditions.add(new MessageCondition() {
            @Override
            public Component getComponent(MinecraftServer minecraftServer, Player player) {
                return Component.translatable("immersiveslumber.message.raid_bounty");
            }
        });
        playerMessageConditions.add(new MessageCondition() {
            @Override
            public Component getComponent(MinecraftServer minecraftServer, Player player) {
                return Component.translatable("immersiveslumber.message.mushroom_conspiracy");
            }
        });
        playerMessageConditions.add(new MessageCondition() {
            @Override
            public Component getComponent(MinecraftServer minecraftServer, Player player) {
                return Component.translatable("immersiveslumber.message.azalea_paradox");
            }
        });
        playerMessageConditions.add(new MessageCondition() {
            @Override
            public Component getComponent(MinecraftServer minecraftServer, Player player) {
                return Component.translatable("immersiveslumber.message.ender_pearl");
            }
        });
        playerMessageConditions.add(new MessageCondition() {
            @Override
            public Component getComponent(MinecraftServer minecraftServer, Player player) {
                return Component.translatable("immersiveslumber.message.iron_golem");
            }
        });
        playerMessageConditions.add(new MessageCondition() {
            @Override
            public Component getComponent(MinecraftServer minecraftServer, Player player) {
                return Component.translatable("immersiveslumber.message.suspicious_stew");
            }
        });
        playerMessageConditions.add(new MessageCondition() {
            @Override
            public Component getComponent(MinecraftServer minecraftServer, Player player) {
                return Component.translatable("immersiveslumber.message.trading_algorithm");
            }
        });
        playerMessageConditions.add(new MessageCondition() {
            @Override
            public Component getComponent(MinecraftServer minecraftServer, Player player) {
                return Component.translatable("immersiveslumber.message.snow_golem_union");
            }
        });
        playerMessageConditions.add(new MessageCondition() {
            @Override
            public Component getComponent(MinecraftServer minecraftServer, Player player) {
                return Component.translatable("immersiveslumber.message.not_up");
            }
        });
        playerMessageConditions.add(new MessageCondition() {
            @Override
            public Component getComponent(MinecraftServer minecraftServer, Player player) {
                return Component.translatable("immersiveslumber.message.portal_face");
            }
        });
        playerMessageConditions.add(new MessageCondition() {
            @Override
            public Component getComponent(MinecraftServer minecraftServer, Player player) {
                return Component.translatable("immersiveslumber.message.chicken_coop");
            }
        });
        playerMessageConditions.add(new MessageCondition() {
            @Override
            public Component getComponent(MinecraftServer minecraftServer, Player player) {
                return Component.translatable("immersiveslumber.message.diamond_vtuber");
            }
        });
        playerMessageConditions.add(new MessageCondition() {
            @Override
            public Component getComponent(MinecraftServer minecraftServer, Player player) {
                return Component.translatable("immersiveslumber.message.tree_planting");
            }
        });
        playerMessageConditions.add(new MessageCondition() {
            @Override
            public Component getComponent(MinecraftServer minecraftServer, Player player) {
                return Component.translatable("immersiveslumber.message.villager_35");
            }
        });
        playerMessageConditions.add(new MessageCondition() {
            @Override
            public Component getComponent(MinecraftServer minecraftServer, Player player) {
                return Component.translatable("immersiveslumber.message.fishing_thesis");
            }
        });
        playerMessageConditions.add(new MessageCondition() {
            @Override
            public Component getComponent(MinecraftServer minecraftServer, Player player) {
                return Component.translatable("immersiveslumber.message.zombie_tsun");
            }
        });
        playerMessageConditions.add(new MessageCondition() {
            @Override
            public Component getComponent(MinecraftServer minecraftServer, Player player) {
                return Component.translatable("immersiveslumber.message.nether_choi");
            }
        });
        playerMessageConditions.add(new MessageCondition() {
            @Override
            public Component getComponent(MinecraftServer minecraftServer, Player player) {
                return Component.translatable("immersiveslumber.message.skeleton_shot");
            }
        });
        playerMessageConditions.add(new MessageCondition() {
            @Override
            public Component getComponent(MinecraftServer minecraftServer, Player player) {
                return Component.translatable("immersiveslumber.message.fishing_ritual");
            }
        });
        playerMessageConditions.add(new MessageCondition() {
            @Override
            public Component getComponent(MinecraftServer minecraftServer, Player player) {
                return Component.translatable("immersiveslumber.message.enchant_energy");
            }
        });
        playerMessageConditions.add(new MessageCondition() {
            @Override
            public Component getComponent(MinecraftServer minecraftServer, Player player) {
                return Component.translatable("immersiveslumber.message.ender_ai");
            }
        });
        playerMessageConditions.add(new MessageCondition() {
            @Override
            public Component getComponent(MinecraftServer minecraftServer, Player player) {
                return Component.translatable("immersiveslumber.message.village_love");
            }
        });
        playerMessageConditions.add(new MessageCondition() {
            @Override
            public Component getComponent(MinecraftServer minecraftServer, Player player) {
                return Component.translatable("immersiveslumber.message.lava_proposal");
            }
        });
        playerMessageConditions.add(new MessageCondition() {
            @Override
            public Component getComponent(MinecraftServer minecraftServer, Player player) {
                ServerPlayer serverPlayer = (ServerPlayer) player;
                if(serverPlayer.serverLevel().getBlockEntity(player.getOnPos().west()) instanceof BedBlockEntity){
                    if(serverPlayer.serverLevel().getBlockState(player.getOnPos().west().west()).getBlock().equals(Blocks.AIR)&&serverPlayer.serverLevel().getBlockState(player.getOnPos().west().west().above()).getBlock().equals(Blocks.AIR)){
                        TallFlowerBlock.placeAt(serverPlayer.serverLevel(),Blocks.SUNFLOWER.defaultBlockState(),player.getOnPos().west().west(),3);
                    }
                }else if(serverPlayer.serverLevel().getBlockState(player.getOnPos().west()).getBlock().equals(Blocks.AIR)&&serverPlayer.serverLevel().getBlockState(player.getOnPos().west().above()).getBlock().equals(Blocks.AIR)){
                    TallFlowerBlock.placeAt(serverPlayer.serverLevel(),Blocks.SUNFLOWER.defaultBlockState(),player.getOnPos().west(),3);

                }
                return Component.translatable("immersiveslumber.message.marry_sunflower");

            }
        });
        playerMessageConditions.add(new MessageCondition() {
            @Override
            public Component getComponent(MinecraftServer minecraftServer, Player player) {
                return Component.translatable("immersiveslumber.message.bag_rose");
            }
        });
        playerMessageConditions.add(new MessageCondition() {
            @Override
            public Component getComponent(MinecraftServer minecraftServer, Player player) {
                return Component.translatable("immersiveslumber.message.recover_fast");
            }
        });
        playerMessageConditions.add(new MessageCondition() {
            @Override
            public Component getComponent(MinecraftServer minecraftServer, Player player) {
                return Component.translatable("immersiveslumber.message.creative_existence");
            }
        });
        playerMessageConditions.add(new MessageCondition() {
            @Override
            public Component getComponent(MinecraftServer minecraftServer, Player player) {
                return Component.translatable("immersiveslumber.message.peer_sleep");
            }
        });
        playerMessageConditions.add(new MessageCondition() {
            @Override
            public Component getComponent(MinecraftServer minecraftServer, Player player) {
                return Component.translatable("immersiveslumber.message.gold_other_marry");
            }
        });
        playerMessageConditions.add(new MessageCondition() {
            @Override
            public Component getComponent(MinecraftServer minecraftServer, Player player) {
                return Component.translatable("immersiveslumber.message.death_recovery");
            }
        });
        playerMessageConditions.add(new MessageCondition() {
            @Override
            public Component getComponent(MinecraftServer minecraftServer, Player player) {
                return Component.translatable("immersiveslumber.message.sakura_romance");
            }
        });
        playerMessageConditions.add(new MessageCondition() {
            @Override
            public Component getComponent(MinecraftServer minecraftServer, Player player) {
                return Component.translatable("immersiveslumber.message.creeper_confession");
            }
        });
        playerMessageConditions.add(new MessageCondition() {
            @Override
            public Component getComponent(MinecraftServer minecraftServer, Player player) {
                return Component.translatable("immersiveslumber.message.ender_pearl_regret");
            }
        });
        playerMessageConditions.add(new MessageCondition() {
            @Override
            public Component getComponent(MinecraftServer minecraftServer, Player player) {
                return Component.translatable("immersiveslumber.message.emo_wyy");
            }
        });
        playerMessageConditions.add(new MessageCondition() {
            @Override
            public Component getComponent(MinecraftServer minecraftServer, Player player) {
                return Component.translatable("immersiveslumber.message.today_sleep_too");
            }
        });
        playerMessageConditions.add(new MessageCondition() {
            @Override
            public Component getComponent(MinecraftServer minecraftServer, Player player) {
                return Component.translatable("immersiveslumber.message.today_sleep_too");
            }
        });
        playerMessageConditions.add(new MessageCondition() {
            @Override
            public Component getComponent(MinecraftServer minecraftServer, Player player) {
                return Component.translatable("immersiveslumber.message.speed_sound");
            }
        });
        playerMessageConditions.add(new MessageCondition() {
            @Override
            public Component getComponent(MinecraftServer minecraftServer, Player player) {
                return Component.translatable("immersiveslumber.message.exp_sound");
            }
        });
        playerMessageConditions.add(new MessageCondition() {
            @Override
            public Component getComponent(MinecraftServer minecraftServer, Player player) {
                return Component.translatable("immersiveslumber.message.adv_1");
            }
        });
        playerMessageConditions.add(new MessageCondition() {
            @Override
            public Component getComponent(MinecraftServer minecraftServer, Player player) {
                return Component.translatable("immersiveslumber.message.adv_2");
            }
        });
        playerMessageConditions.add(new MessageCondition() {
            @Override
            public Component getComponent(MinecraftServer minecraftServer, Player player) {
                return Component.translatable("immersiveslumber.message.adv_3");
            }
        });

        playerMessageConditions.add(new MessageCondition() {
            @Override
            public Component getComponent(MinecraftServer minecraftServer, Player player) {
                return Component.translatable("immersiveslumber.message.cactus_onlyfans");
            }
        });

        playerMessageConditions.add(new MessageCondition() {
            @Override
            public Component getComponent(MinecraftServer minecraftServer, Player player) {
                return Component.translatable("immersiveslumber.message.eat_burp");
            }
        });

        playerMessageConditions.add(new MessageCondition() {
            @Override
            public Component getComponent(MinecraftServer minecraftServer, Player player) {
                return Component.translatable("immersiveslumber.message.spyglass_vertigo");
            }
        });

        playerMessageConditions.add(new MessageCondition() {
            @Override
            public Component getComponent(MinecraftServer minecraftServer, Player player) {
                return Component.translatable("immersiveslumber.message.bed_respawn_contract");
            }
        });
        playerMessageConditions.add(new MessageCondition() {
            @Override
            public Component getComponent(MinecraftServer minecraftServer, Player player) {
                return Component.translatable("immersiveslumber.message.dragon_eggs");
            }
        });
        playerMessageConditions.add(new MessageCondition() {
            @Override
            public Component getComponent(MinecraftServer minecraftServer, Player player) {
                return Component.translatable("immersiveslumber.message.death_brain");
            }
        });
        playerMessageConditions.add(new MessageCondition() {
            @Override
            public Component getComponent(MinecraftServer minecraftServer, Player player) {
                return Component.translatable("immersiveslumber.message.nightlight_cookie");
            }
        });
        playerMessageConditions.add(new MessageCondition() {
            @Override
            public Component getComponent(MinecraftServer minecraftServer, Player player) {
                return Component.translatable("immersiveslumber.message.consciousness_quit");
            }
        });
        playerMessageConditions.add(new MessageCondition() {
            @Override
            public Component getComponent(MinecraftServer minecraftServer, Player player) {
                return Component.translatable("immersiveslumber.message.ground_water");
            }
        });
        playerMessageConditions.add(new MessageCondition() {
            @Override
            public Component getComponent(MinecraftServer minecraftServer, Player player) {
                return Component.translatable("immersiveslumber.message.ground_water_2");
            }
        });
        playerMessageConditions.add(new MessageCondition() {
            @Override
            public Component getComponent(MinecraftServer minecraftServer, Player player) {
                return Component.translatable("immersiveslumber.message.sleep_level_down");
            }
        });
        playerMessageConditions.add(new MessageCondition() {
            @Override
            public Component getComponent(MinecraftServer minecraftServer, Player player) {
                return Component.translatable("immersiveslumber.message.sleep_level_down_2");
            }
        });
        playerMessageConditions.add(new MessageCondition() {
            @Override
            public Component getComponent(MinecraftServer minecraftServer, Player player) {
                return Component.translatable("immersiveslumber.message.chest_again");
            }
        });
        playerMessageConditions.add(new MessageCondition() {
            @Override
            public Component getComponent(MinecraftServer minecraftServer, Player player) {
                return Component.translatable("immersiveslumber.message.bilibili_economy");
            }
        });
        playerMessageConditions.add(new MessageCondition() {
            @Override
            public Component getComponent(MinecraftServer minecraftServer, Player player) {
                return Component.translatable("immersiveslumber.message.zombie_idol");
            }
        });
        playerMessageConditions.add(new MessageCondition() {
            @Override
            public Component getComponent(MinecraftServer minecraftServer, Player player) {
                return Component.translatable("immersiveslumber.message.pig_gentrification");
            }
        });
        playerMessageConditions.add(new MessageCondition() {
            @Override
            public Component getComponent(MinecraftServer minecraftServer, Player player) {
                return Component.translatable("immersiveslumber.message.under_voice");
            }
        });
        playerMessageConditions.add(new MessageCondition() {
            @Override
            public Component getComponent(MinecraftServer minecraftServer, Player player) {
                return Component.translatable("immersiveslumber.message.drowned_homework");
            }
        });
        playerMessageConditions.add(new MessageCondition() {
            @Override
            public Component getComponent(MinecraftServer minecraftServer, Player player) {
                return Component.translatable("immersiveslumber.message.shulker_ocd");
            }
        });
        playerMessageConditions.add(new MessageCondition() {
            @Override
            public Component getComponent(MinecraftServer minecraftServer, Player player) {
                return Component.translatable("immersiveslumber.message.witch_tiktok");
            }
        });
        playerMessageConditions.add(new MessageCondition() {
            @Override
            public Component getComponent(MinecraftServer minecraftServer, Player player) {
                return Component.translatable("immersiveslumber.message.pillager_union");
            }
        });
        playerMessageConditions.add(new MessageCondition() {
            @Override
            public Component getComponent(MinecraftServer minecraftServer, Player player) {
                return Component.translatable("immersiveslumber.message.netherrack_bbq");
            }
        });
        playerMessageConditions.add(new MessageCondition() {
            @Override
            public Component getComponent(MinecraftServer minecraftServer, Player player) {
                return Component.translatable("immersiveslumber.message.enderman_drama");
            }
        });
        playerMessageConditions.add(new MessageCondition() {
            @Override
            public Component getComponent(MinecraftServer minecraftServer, Player player) {
                return Component.translatable("immersiveslumber.message.drawback");
            }
        });
        playerMessageConditions.add(new MessageCondition() {
            @Override
            public Component getComponent(MinecraftServer minecraftServer, Player player) {
                return Component.translatable("immersiveslumber.message.home_chicken");
            }
        });
        playerMessageConditions.add(new MessageCondition() {
            @Override
            public Component getComponent(MinecraftServer minecraftServer, Player player) {
                return Component.translatable("immersiveslumber.message.pillager_emo");
            }
        });
        playerMessageConditions.add(new MessageCondition() {
            @Override
            public Component getComponent(MinecraftServer minecraftServer, Player player) {
                return Component.translatable("immersiveslumber.message.witch_fangirl");
            }
        });
        playerMessageConditions.add(new MessageCondition() {
            @Override
            public Component getComponent(MinecraftServer minecraftServer, Player player) {
                return Component.translatable("immersiveslumber.message.enderchest_scam");
            }
        });
        playerMessageConditions.add(new MessageCondition() {
            @Override
            public Component getComponent(MinecraftServer minecraftServer, Player player) {
                CompoundTag compoundTag = new CompoundTag();
                ImmersiveSlumber.playerNbt.putIfAbsent(player.getUUID(),compoundTag);
                ImmersiveSlumber.playerNbt.get(player.getUUID()).putBoolean("after_creeper",true);
                return Component.translatable("immersiveslumber.message.creeper_emoji");
            }
        });
        playerMessageConditions.add(new MessageCondition() {
            @Override
            public Component getComponent(MinecraftServer minecraftServer, Player player) {
                return Component.translatable("immersiveslumber.message.witch_livestream");
            }
        });
        playerMessageConditions.add(new MessageCondition() {
            @Override
            public Component getComponent(MinecraftServer minecraftServer, Player player) {
                return Component.translatable("immersiveslumber.message.phantom_uc");
            }
        });
        playerMessageConditions.add(new MessageCondition() {
            @Override
            public Component getComponent(MinecraftServer minecraftServer, Player player) {
                return Component.translatable("immersiveslumber.message.steve_compass");
            }
        });
        playerMessageConditions.add(new MessageCondition() {
            @Override
            public Component getComponent(MinecraftServer minecraftServer, Player player) {
                return Component.translatable("immersiveslumber.message.steve_fishing");
            }
        });
        playerMessageConditions.add(new MessageCondition() {
            @Override
            public Component getComponent(MinecraftServer minecraftServer, Player player) {
                return Component.translatable("immersiveslumber.message.steve_bed");
            }
        });
        playerMessageConditions.add(new MessageCondition() {
            @Override
            public Component getComponent(MinecraftServer minecraftServer, Player player) {
                return Component.translatable("immersiveslumber.message.steve_enderchest");
            }
        });
        playerMessageConditions.add(new MessageCondition() {
            @Override
            public Component getComponent(MinecraftServer minecraftServer, Player player) {
                return Component.translatable("immersiveslumber.message.steve_potion");
            }
        });
        playerMessageConditions.add(new MessageCondition() {
            @Override
            public Component getComponent(MinecraftServer minecraftServer, Player player) {
                ServerPlayer serverPlayer = (ServerPlayer) player;
                BlockPos pos = findRandomPlantablePos(serverPlayer,10,(TallFlowerBlock)Blocks.ROSE_BUSH);
                TallFlowerBlock.placeAt(serverPlayer.serverLevel(),Blocks.ROSE_BUSH.defaultBlockState(),pos,3);
                return Component.translatable("immersiveslumber.message.rose_miss");
            }
        });
        playerMessageConditions.add(new MessageCondition() {
            @Override
            public Component getComponent(MinecraftServer minecraftServer, Player player) {
                return Component.translatable("immersiveslumber.message.steve_nether");
            }
        });
        playerMessageConditions.add(new MessageCondition() {
            @Override
            public Component getComponent(MinecraftServer minecraftServer, Player player) {
                return Component.translatable("immersiveslumber.message.steve_glass");
            }
        });
        playerMessageConditions.add(new MessageCondition() {
            @Override
            public Component getComponent(MinecraftServer minecraftServer, Player player) {
                return Component.translatable("immersiveslumber.message.steve_arrow");
            }
        });
        playerMessageConditions.add(new MessageCondition() {
            @Override
            public Component getComponent(MinecraftServer minecraftServer, Player player) {
                return Component.translatable("immersiveslumber.message.steve_warden");
            }
        });
        playerMessageConditions.add(new MessageCondition() {
            @Override
            public Component getComponent(MinecraftServer minecraftServer, Player player) {
                return Component.translatable("immersiveslumber.message.steve_allay");
            }
        });
        playerMessageConditions.add(new MessageCondition() {
            @Override
            public Component getComponent(MinecraftServer minecraftServer, Player player) {
                return Component.translatable("immersiveslumber.message.steve_mod");
            }
        });
        playerMessageConditions.add(new MessageCondition() {
            @Override
            public Component getComponent(MinecraftServer minecraftServer, Player player) {
                return Component.translatable("immersiveslumber.message.steve_spotify");
            }
        });
        playerMessageConditions.add(new MessageCondition() {
            @Override
            public Component getComponent(MinecraftServer minecraftServer, Player player) {
                return Component.translatable("immersiveslumber.message.steve_brush");
            }
        });
        playerMessageConditions.add(new MessageCondition() {
            @Override
            public Component getComponent(MinecraftServer minecraftServer, Player player) {
                return Component.translatable("immersiveslumber.message.steve_solipsism");
            }
        });
        playerMessageConditions.add(new MessageCondition() {
            @Override
            public Component getComponent(MinecraftServer minecraftServer, Player player) {
                return Component.translatable("immersiveslumber.message.steve_daemon");
            }
        });
        playerMessageConditions.add(new MessageCondition() {
            @Override
            public Component getComponent(MinecraftServer minecraftServer, Player player) {
                return Component.translatable("immersiveslumber.message.steve_gravity");
            }
        });
        playerMessageConditions.add(new MessageCondition() {
            @Override
            public Component getComponent(MinecraftServer minecraftServer, Player player) {
                return Component.translatable("immersiveslumber.message.stay_night_2");
            }
        });
        playerMessageConditions.add(new MessageCondition() {
            @Override
            public Component getComponent(MinecraftServer minecraftServer, Player player) {
                return Component.translatable("immersiveslumber.message.no_gui");
            }
        });
        playerMessageConditions.add(new MessageCondition() {
            @Override
            public Component getComponent(MinecraftServer minecraftServer, Player player) {
                return Component.translatable("immersiveslumber.message.before_death");
            }
        });


    }

    public static void initSoundMessage() {
        //敌对生物
        playerSoundMessageConditions.put(SoundEvents.ENDERMAN_TELEPORT.getLocation(), new MessageCondition() {
            @Override
            public Component getComponent(MinecraftServer minecraftServer, Player player) {
                if(player.getRandom().nextInt(0,2)==0){
                    return Component.translatable("immersiveslumber.message.enderman_teleport_1");
                }else{
                    return Component.translatable("immersiveslumber.message.enderman_teleport_2");
                }
            }
        });
        playerSoundMessageConditions.put(SoundEvents.CREEPER_PRIMED.getLocation(), new MessageCondition() {
            @Override
            public Component getComponent(MinecraftServer minecraftServer, Player player) {
                return Component.translatable("immersiveslumber.message.creeper_primed");
            }
        });
        playerSoundMessageConditions.put(SoundEvents.ENDERMAN_TELEPORT.getLocation(), new MessageCondition() {
            @Override
            public Component getComponent(MinecraftServer minecraftServer, Player player) {
                return Component.translatable("immersiveslumber.message.enderman_teleport");
            }
        });

        playerSoundMessageConditions.put(SoundEvents.ZOMBIE_ATTACK_WOODEN_DOOR.getLocation(), new MessageCondition() {
            @Override
            public Component getComponent(MinecraftServer minecraftServer, Player player) {
                return Component.translatable("immersiveslumber.message.zombie_attack_door");
            }
        });
         playerSoundMessageConditions.put(SoundEvents.ZOMBIE_AMBIENT.getLocation(), new MessageCondition() {
            @Override
            public Component getComponent(MinecraftServer minecraftServer, Player player) {
                return Component.translatable("immersiveslumber.message.zombie_ambient");
            }
        });
         playerSoundMessageConditions.put(SoundEvents.ZOMBIE_ATTACK_IRON_DOOR.getLocation(), new MessageCondition() {
            @Override
            public Component getComponent(MinecraftServer minecraftServer, Player player) {
                return Component.translatable("immersiveslumber.message.zombie_attack_door");
            }
        });
        playerSoundMessageConditions.put(SoundEvents.SKELETON_STEP.getLocation(), new MessageCondition() {
            @Override
            public Component getComponent(MinecraftServer minecraftServer, Player player) {
                return Component.translatable("immersiveslumber.message.skeleton_step");
            }
        });
        playerSoundMessageConditions.put(SoundEvents.GHAST_SCREAM.getLocation(), new MessageCondition() {
            @Override
            public Component getComponent(MinecraftServer minecraftServer, Player player) {
                return Component.translatable("immersiveslumber.message.ghast_scream");
            }
        });
        playerSoundMessageConditions.put(SoundEvents.WITCH_AMBIENT.getLocation(), new MessageCondition() {
            @Override
            public Component getComponent(MinecraftServer minecraftServer, Player player) {
                return Component.translatable("immersiveslumber.message.witch_ambient");
            }
        });
        playerSoundMessageConditions.put(SoundEvents.PILLAGER_AMBIENT.getLocation(), new MessageCondition() {
            @Override
            public Component getComponent(MinecraftServer minecraftServer, Player player) {
                return Component.translatable("immersiveslumber.message.pillager_ambient");
            }
        });
        playerSoundMessageConditions.put(SoundEvents.WARDEN_ROAR.getLocation(), new MessageCondition() {
            @Override
            public Component getComponent(MinecraftServer minecraftServer, Player player) {
                return Component.translatable("immersiveslumber.message.warden_roar");
            }
        });
        playerSoundMessageConditions.put(SoundEvents.SILVERFISH_AMBIENT.getLocation(), new MessageCondition() {
            @Override
            public Component getComponent(MinecraftServer minecraftServer, Player player) {
                return Component.translatable("immersiveslumber.message.silverfish_ambient");
            }
        });
        playerSoundMessageConditions.put(SoundEvents.ENDER_DRAGON_AMBIENT.getLocation(), new MessageCondition() {
            @Override
            public Component getComponent(MinecraftServer minecraftServer, Player player) {
                return Component.translatable("immersiveslumber.message.ender_dragon_ambient");
            }
        });
        playerSoundMessageConditions.put(SoundEvents.PHANTOM_AMBIENT.getLocation(), new MessageCondition() {
            @Override
            public Component getComponent(MinecraftServer minecraftServer, Player player) {
                return Component.translatable("immersiveslumber.message.phantom_ambient");
            }
        });

        //自然现象
        playerSoundMessageConditions.put(SoundEvents.LIGHTNING_BOLT_THUNDER.getLocation(), new MessageCondition() {
            @Override
            public Component getComponent(MinecraftServer minecraftServer, Player player) {
                return Component.translatable("immersiveslumber.message.lightning_bolt_thunder");
            }
        });
        playerSoundMessageConditions.put(SoundEvents.LAVA_AMBIENT.getLocation(), new MessageCondition() {
            @Override
            public Component getComponent(MinecraftServer minecraftServer, Player player) {
                return Component.translatable("immersiveslumber.message.lava_ambient");
            }
        });
        playerSoundMessageConditions.put(SoundEvents.FIRE_AMBIENT.getLocation(), new MessageCondition() {
            @Override
            public Component getComponent(MinecraftServer minecraftServer, Player player) {
                return Component.translatable("immersiveslumber.message.fire_ambient");
            }
        });
        playerSoundMessageConditions.put(SoundEvents.WATER_AMBIENT.getLocation(), new MessageCondition() {
            @Override
            public Component getComponent(MinecraftServer minecraftServer, Player player) {
                return Component.translatable("immersiveslumber.message.water_ambient");
            }
        });
        playerSoundMessageConditions.put(SoundEvents.PORTAL_AMBIENT.getLocation(), new MessageCondition() {
            @Override
            public Component getComponent(MinecraftServer minecraftServer, Player player) {
                return Component.translatable("immersiveslumber.message.portal_ambient");
            }
        });


        //工具类
        playerSoundMessageConditions.put(SoundEvents.TNT_PRIMED.getLocation(), new MessageCondition() {
            @Override
            public Component getComponent(MinecraftServer minecraftServer, Player player) {
                int i = Util.getRandomInt(3);
                if(i==0){
                    return Component.translatable("immersiveslumber.message.tnt_primed");
                }else if (i==1){
                    return Component.translatable("immersiveslumber.message.tnt_primed_2");
                }else if (i==2){
                    return Component.translatable("immersiveslumber.message.tnt_primed_3");
                }
                return null;
            }
        });
        playerSoundMessageConditions.put(SoundEvents.ANVIL_FALL.getLocation(), new MessageCondition() {
            @Override
            public Component getComponent(MinecraftServer minecraftServer, Player player) {
                return Component.translatable("immersiveslumber.message.anvil_fall");
            }
        });
        playerSoundMessageConditions.put(SoundEvents.PISTON_EXTEND.getLocation(), new MessageCondition() {
            @Override
            public Component getComponent(MinecraftServer minecraftServer, Player player) {
                return Component.translatable("immersiveslumber.message.piston_extend");
            }
        });
        playerSoundMessageConditions.put(SoundEvents.DISPENSER_DISPENSE.getLocation(), new MessageCondition() {
            @Override
            public Component getComponent(MinecraftServer minecraftServer, Player player) {
                return Component.translatable("immersiveslumber.message.dispenser_dispense");
            }
        });
        playerSoundMessageConditions.put(SoundEvents.BOAT_PADDLE_WATER.getLocation(), new MessageCondition() {
            @Override
            public Component getComponent(MinecraftServer minecraftServer, Player player) {
                return Component.translatable("immersiveslumber.message.boat_paddle_water");
            }
        });
        playerSoundMessageConditions.put(SoundEvents.BOAT_PADDLE_LAND.getLocation(), new MessageCondition() {
            @Override
            public Component getComponent(MinecraftServer minecraftServer, Player player) {
                return Component.translatable("immersiveslumber.message.boat_paddle_land");
            }
        });
        playerSoundMessageConditions.put(SoundEvents.IRON_DOOR_OPEN.getLocation(), new MessageCondition() {
            @Override
            public Component getComponent(MinecraftServer minecraftServer, Player player) {
                return Component.translatable("immersiveslumber.message.door_open");
            }
        });
        playerSoundMessageConditions.put(SoundEvents.WOODEN_DOOR_OPEN.getLocation(), new MessageCondition() {
            @Override
            public Component getComponent(MinecraftServer minecraftServer, Player player) {
                return Component.translatable("immersiveslumber.message.door_open");
            }
        });
        playerSoundMessageConditions.put(SoundEvents.FURNACE_FIRE_CRACKLE.getLocation(), new MessageCondition() {
            @Override
            public Component getComponent(MinecraftServer minecraftServer, Player player) {
                return Component.translatable("immersiveslumber.message.furnace_fire_crackle");
            }
        });
        playerSoundMessageConditions.put(SoundEvents.FISHING_BOBBER_SPLASH.getLocation(), new MessageCondition() {
            @Override
            public Component getComponent(MinecraftServer minecraftServer, Player player) {
                if(Util.getRandomBoolean()){
                    return Component.translatable("immersiveslumber.message.fishing_air");
                }else {
                    return Component.translatable("immersiveslumber.message.fishing_bobber_splash");
                }
            }
        });

        //动物
        playerSoundMessageConditions.put(SoundEvents.CAT_HISS.getLocation(), new MessageCondition() {
            @Override
            public Component getComponent(MinecraftServer minecraftServer, Player player) {
                return Component.translatable("immersiveslumber.message.cat_hiss");
            }
        });
         playerSoundMessageConditions.put(SoundEvents.CAT_PURR.getLocation(), new MessageCondition() {
            @Override
            public Component getComponent(MinecraftServer minecraftServer, Player player) {
                if(Util.getRandomBoolean()){
                    return Component.translatable("immersiveslumber.message.cat_purr");
                }else{
                    return Component.translatable("immersiveslumber.message.cat_regime");

                }

            }
        });
        playerSoundMessageConditions.put(SoundEvents.BEE_LOOP_AGGRESSIVE.getLocation(), new MessageCondition() {
            @Override
            public Component getComponent(MinecraftServer minecraftServer, Player player) {
                return Component.translatable("immersiveslumber.message.bee_loop_aggressive");
            }
        });
        playerSoundMessageConditions.put(SoundEvents.SHEEP_AMBIENT.getLocation(), new MessageCondition() {
            @Override
            public Component getComponent(MinecraftServer minecraftServer, Player player) {
                return Component.translatable("immersiveslumber.message.sheep_ambient");
            }
        });
        playerSoundMessageConditions.put(SoundEvents.DOLPHIN_AMBIENT.getLocation(), new MessageCondition() {
            @Override
            public Component getComponent(MinecraftServer minecraftServer, Player player) {
                return Component.translatable("immersiveslumber.message.dolphin_ambient");
            }
        });
        playerSoundMessageConditions.put(SoundEvents.SNOW_GOLEM_AMBIENT.getLocation(), new MessageCondition() {
            @Override
            public Component getComponent(MinecraftServer minecraftServer, Player player) {
                return Component.translatable("immersiveslumber.message.snow_golem_ambient");
            }
        });
        playerSoundMessageConditions.put(SoundEvents.COW_AMBIENT.getLocation(), new MessageCondition() {
            @Override
            public Component getComponent(MinecraftServer minecraftServer, Player player) {
                return Component.translatable("immersiveslumber.message.cow_ambient");
            }
        });
        playerSoundMessageConditions.put(SoundEvents.PANDA_AMBIENT.getLocation(), new MessageCondition() {
            @Override
            public Component getComponent(MinecraftServer minecraftServer, Player player) {
                return Component.translatable("immersiveslumber.message.panda_ambient");
            }
        });
        playerSoundMessageConditions.put(SoundEvents.CHEST_OPEN.getLocation(), new MessageCondition() {
            @Override
            public Component getComponent(MinecraftServer minecraftServer, Player player) {
                return Component.translatable("immersiveslumber.message.chest_open");
            }
        });
        playerSoundMessageConditions.put(SoundEvents.CAMPFIRE_CRACKLE.getLocation(), new MessageCondition() {
            @Override
            public Component getComponent(MinecraftServer minecraftServer, Player player) {
                return Component.translatable("immersiveslumber.message.campfire_crackle");
            }
        });
        playerSoundMessageConditions.put(SoundEvents.FIRE_EXTINGUISH.getLocation(), new MessageCondition() {
            @Override
            public Component getComponent(MinecraftServer minecraftServer, Player player) {
                return Component.translatable("immersiveslumber.message.fire_extinguish");
            }
        });
        playerSoundMessageConditions.put(SoundEvents.ZOMBIE_STEP.getLocation(), new MessageCondition() {
            @Override
            public Component getComponent(MinecraftServer minecraftServer, Player player) {
                return Component.translatable("immersiveslumber.message.zombie_step");
            }
        });
        playerSoundMessageConditions.put(SoundEvents.CHICKEN_AMBIENT.getLocation(), new MessageCondition() {
            @Override
            public Component getComponent(MinecraftServer minecraftServer, Player player) {
                return Component.translatable("immersiveslumber.message.roast_chicken");
            }
        });
        playerSoundMessageConditions.put(SoundEvents.SHULKER_BOX_OPEN.getLocation(), new MessageCondition() {
            @Override
            public Component getComponent(MinecraftServer minecraftServer, Player player) {
                return Component.translatable("immersiveslumber.message.shulker_box_open");
            }
        });
        playerSoundMessageConditions.put(SoundEvents.CAT_BEG_FOR_FOOD.getLocation(), new MessageCondition() {
            @Override
            public Component getComponent(MinecraftServer minecraftServer, Player player) {
                return Component.translatable("immersiveslumber.message.cat_beg_for_food");
            }
        });
        playerSoundMessageConditions.put(SoundEvents.PLAYER_BURP.getLocation(), new MessageCondition() {
            @Override
            public Component getComponent(MinecraftServer minecraftServer, Player player) {
                return Component.translatable("immersiveslumber.message.player_burp");
            }
        });
        playerSoundMessageConditions.put(SoundEvents.DROWNED_AMBIENT.getLocation(), new MessageCondition() {
            @Override
            public Component getComponent(MinecraftServer minecraftServer, Player player) {
                return Component.translatable("immersiveslumber.message.drowned_ambient");
            }
        });
        playerSoundMessageConditions.put(SoundEvents.GENERIC_DRINK.getLocation(), new MessageCondition() {
            @Override
            public Component getComponent(MinecraftServer minecraftServer, Player player) {
                return Component.translatable("immersiveslumber.message.steve_potion");
            }
        });





    }



}