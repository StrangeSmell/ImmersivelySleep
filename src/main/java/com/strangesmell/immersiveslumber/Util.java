package com.strangesmell.immersiveslumber;

import com.legacy.goodnightsleep.registry.GNSDimensions;
import com.legacy.goodnightsleep.world.GNSTeleporter;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.OwnableEntity;
import net.minecraft.world.entity.ai.targeting.TargetingConditions;
import net.minecraft.world.entity.animal.Cat;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.ForgeHooks;
import net.minecraftforge.common.IPlantable;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.server.ServerLifecycleHooks;

import java.util.*;

import static com.strangesmell.immersiveslumber.TimeProgressionHandler.*;

public class Util {
    public static ResourceKey<Level> pasterDream_dyedream_world = ResourceKey.create(Registries.DIMENSION, new ResourceLocation("pasterdream:dyedream_world"));
    public static ResourceKey<Level> pasterDream_aaroncos_arena_world = ResourceKey.create(Registries.DIMENSION, new ResourceLocation("pasterdream:dyedream_world"));
    public static ResourceKey<Level> pasterDream_lamp_shadow_world = ResourceKey.create(Registries.DIMENSION, new ResourceLocation("pasterdream:dyedream_world"));
    public static ResourceKey<Level> pasterDream_wind_journey_world = ResourceKey.create(Registries.DIMENSION, new ResourceLocation("pasterdream:dyedream_world"));

    public static float accelerationFactor(MinecraftServer minecraftServer) {
        int player_size = minecraftServer.getPlayerList().getPlayers().size();
        float sum=3*player_size;
        float comfortable = 0;
        for(Player player : minecraftServer.getPlayerList().getPlayers()) {
            boolean inDream =false;
            if(ModList.get().isLoaded("good_nights_sleep")|| ModList.get().isLoaded("goodnightssleep")){
                inDream = GNSUtil.isDreamDimension(player);
            }
            if(ModList.get().isLoaded("pasterdream")){
                if(!inDream){
                    inDream = player.level().dimension().equals(pasterDream_dyedream_world)||player.level().dimension().equals(pasterDream_aaroncos_arena_world)||player.level().dimension().equals(pasterDream_lamp_shadow_world)||player.level().dimension().equals(pasterDream_wind_journey_world);

                }
            }
            if(player.isSleeping()||inDream){
                UUID uuid = player.getUUID();
                playerMessageTicks.putIfAbsent(uuid,0);
                if(playerMessageTicks.get(uuid)>0){
                    playerMessageTicks.put(uuid,playerMessageTicks.get(uuid)-1);
                }

                //模式检查
                if(player.isCreative()){
                    if(playerMessageTicks.get(uuid)==0){
                        player.displayClientMessage(Component.translatable("immersiveslumber.message.creative"),true);
                        playerMessageTicks.put(uuid, (int) (60*sum/player_size));
                    }

                    sum+=10;
                    comfortable+=10;
                    //fix:occupy bed

                    travelToDream(player,true);
                    continue;
                }

                //血量检查
                float factor =player.getHealth()/player.getMaxHealth();
                if(factor>0.9){
                    sum+=factor;
                    comfortable+=factor;
                }else if (factor<0.2){
                    sum -= 2-factor;
                    comfortable -= 2-factor;
                }else{
                    sum -= 1;
                    comfortable -= 1;
                }

                //遮蔽检查和天气检查
                Biome.Precipitation weather = player.level().getBiome(player.getOnPos()).value().getPrecipitationAt(player.getOnPos());
                boolean canSeeSky = player.level().canSeeSky(player.getOnPos());
                if(canSeeSky){
                    if(weather == Biome.Precipitation.RAIN ||weather == Biome.Precipitation.SNOW){
                        sum -= 2f;
                        comfortable -= 2f;
                    }else{
                        comfortable -= 0.5f;
                    }
                }else{
                    if(weather == Biome.Precipitation.RAIN ||weather == Biome.Precipitation.SNOW){
                        sum +=0.5f;
                        comfortable +=0.5f;
                    }else{
                        sum +=0.5f;
                        comfortable += 0.5f;
                    }

                }

                //检查是否是白天
                if(!player.level().isDay()){
                    sum +=3;
                    comfortable +=3;
                    //playerMessageCountAdd(uuid);
                }

                //亮度检查
                float light =  player.level().getBrightness(LightLayer.BLOCK, player.getOnPos());
                sum += (2-light)/15;
                comfortable += (2-light)/15;

                //饥饿检查
                factor = clamp((float) player.getFoodData().getFoodLevel() /20,0,1);
                sum += factor -0.8f;
                comfortable += factor -0.8f;

                //饱食检查
                factor = clamp((float) player.getFoodData().getSaturationLevel() /10,0,1);
                sum += factor;
                comfortable += factor;

                //生物检查
                factor =0;
                List<LivingEntity> livingEntityList = player.level().getNearbyEntities(LivingEntity.class,TargetingConditions.forNonCombat(),player,player.getBoundingBox().inflate(5,3,5));
                if(livingEntityList.isEmpty()){
                   sum +=1;
                    comfortable +=1;
                }else{
                    for(LivingEntity livingEntity : livingEntityList){
                        if(livingEntity instanceof OwnableEntity ownableEntity){
                            if(ownableEntity.getOwner()!=null){
                                if(ownableEntity.getOwner().equals(player)){
                                    factor+=0.5f;
                                }
                            }else{
                                factor-=0.5f;
                            }

                        }else{
                            if(livingEntity.canAttack(player)){
                                factor-=1f;
                            }else{
                                factor-=0.5f;
                            }
                        }
                    }
                    sum += factor;
                    comfortable += factor;
                }


                //检查buff
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
                    sum -= 1.5f;
                    comfortable -= 1.5f;
                }

                if(effects.contains(MobEffects.DOLPHINS_GRACE)){
                    sum += 1f;
                    comfortable += 1f;
                }
                if(effects.contains(MobEffects.LUCK)){
                    sum += 1f;
                    comfortable += 1f;
                }
                if(effects.contains(MobEffects.HERO_OF_THE_VILLAGE)){
                    sum += 0.5f;
                    comfortable += 0.5f;
                }
                if(effects.contains(MobEffects.SATURATION)){
                    sum += 0.5f;
                    comfortable += 0.5f;
                }
                if(effects.contains(MobEffects.FIRE_RESISTANCE)){
                    sum += 0.5f;
                    comfortable += 0.5f;
                }
                if(effects.contains(MobEffects.ABSORPTION)){
                    sum += 0.5f;
                    comfortable += 0.5f;
                }
                if(effects.contains(MobEffects.REGENERATION)){
                    sum += 0.5f;
                    comfortable += 0.5f;
                }
                if(effects.contains(MobEffects.HEALTH_BOOST)){
                    sum += 0.5f;
                    comfortable += 0.5f;
                }
                if(effects.contains(MobEffects.WATER_BREATHING)) {
                    sum += 0.5f;
                    comfortable += 0.5f;
                }
                if(effects.contains(MobEffects.INVISIBILITY)) {
                    sum += 1f;
                    comfortable += 1f;
                }
                if(effects.contains(MobEffects.NIGHT_VISION)){
                    sum -= 1f;
                    comfortable -= 1f;
                }
                if(effects.contains(MobEffects.DIG_SPEED)){
                    sum -= 0.5f;
                    comfortable -= 0.5f;
                }
                if(effects.contains(MobEffects.SLOW_FALLING)){
                    sum += 1f;
                    comfortable += 1f;
                }
                if(effects.contains(MobEffects.JUMP)){
                    sum -= 1f;
                    comfortable -= 1f;
                }
                if(effects.contains(MobEffects.MOVEMENT_SPEED)){
                    sum -= 1f;
                    comfortable -= 1f;
                }
                if(effects.contains(MobEffects.DAMAGE_BOOST)){
                    sum -= 0.5f;
                    comfortable -= 0.5f;
                }
                if(comfortable>=8){
                    stopSleepInBed(player);
                    travelToDream(player,true);


                    player.stopSleeping();
                }else if(comfortable<-5){
                    stopSleepInBed(player);
                    travelToDream(player,true);

                    player.stopSleeping();
                }
                if(playerMessageTicks.get(uuid)<=0){
                    boolean haveSoundMessage = playSoundEventMap.get(uuid)!=null&&playerSoundMessageConditions.containsKey(playSoundEventMap.get(uuid));
                    Component component = null;
                    if(haveSoundMessage){
                        component=playerSoundMessageConditions.get(playSoundEventMap.get(uuid)).getComponent(minecraftServer,player);
                        playSoundEventMap.put(uuid,null);
                    }else{
                        int messageCount = player.level().random.nextInt(1, playerMessageConditions.size());
                        component = playerMessageConditions.get(messageCount).getComponent(minecraftServer,player);
                        while(playerMessageConditions.get(messageCount).getComponent(minecraftServer,player)==null){
                            component = playerMessageConditions.get(player.level().random.nextInt(1, playerMessageConditions.size())).getComponent(minecraftServer,player);
                            messageCount = player.level().random.nextInt(1, playerMessageConditions.size());
                        }
                    }
                    player.displayClientMessage(component,true);
                    sum = sleepcycle$areEnoughPlayersSleeping(minecraftServer) ? sum*60/player_size:60;
                    playerMessageTicks.put(uuid, (int) clamp(sum*60/player_size,1,100));

                }
            }
        }

        return sum/minecraftServer.getPlayerList().getPlayers().size();
    }

    public static boolean sleepcycle$areEnoughPlayersSleeping(MinecraftServer server) {
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

    private static void travelToDream(Player player, boolean dream) {
        if (player instanceof ServerPlayer serverPlayer) {
            ResourceKey<Level> sleepDim;
            Random random = new Random();
            Boolean isEntryPaster = random.nextBoolean();
            Boolean have_good_night =ModList.get().isLoaded("good_nights_sleep")|| ModList.get().isLoaded("goodnightssleep");
            Boolean have_pasterdream=ModList.get().isLoaded("pasterdream");
            List<ResourceKey<Level>> resourceKeys = new ArrayList<>();

            if(have_good_night&&have_pasterdream){
                stopSleepInBed(player);
                resourceKeys.add(pasterDream_dyedream_world);
                resourceKeys.add(pasterDream_aaroncos_arena_world);
                resourceKeys.add(pasterDream_lamp_shadow_world);
                resourceKeys.add(pasterDream_wind_journey_world);
                resourceKeys.add(GNSUtil.dreamKey());
                resourceKeys.add(GNSUtil.nightmareKey());
                int index = random.nextInt(0,resourceKeys.size());
                sleepDim = resourceKeys.get(index);
                if(index>3) {

                        GNSUtil.ifPresent(serverPlayer, (d) -> {
                            d.setEnteredDreamTime(serverPlayer.level().getGameTime());
                            d.syncDataToClient();
                        });

                }
                try {
                    ServerLevel serverWorld = player.getServer().getLevel(Level.OVERWORLD);
                    BlockPos pos = player.level().dimension() != Level.OVERWORLD ? serverPlayer.getRespawnPosition() : serverWorld.getSharedSpawnPos();
                    changeDimension(sleepDim, player, pos);
                } catch (NullPointerException var8) {
                    NullPointerException e = var8;
                    e.printStackTrace();
                }
                player.stopSleeping();
            }else if(have_good_night){
                stopSleepInBed(player);
                sleepDim =dream ? GNSUtil.dreamKey() : GNSUtil.nightmareKey();
                ResourceKey<Level> transferDimension = player.level().dimension().equals(sleepDim) ? Level.OVERWORLD : sleepDim;
                if (transferDimension != Level.OVERWORLD) {
                    GNSUtil.ifPresent(serverPlayer, (d) -> {
                        d.setEnteredDreamTime(serverPlayer.level().getGameTime());
                        d.syncDataToClient();
                    });
                }
                sleepDim = transferDimension;
                try {
                    ServerLevel serverWorld = player.getServer().getLevel(Level.OVERWORLD);
                    BlockPos pos = player.level().dimension() != Level.OVERWORLD ? serverPlayer.getRespawnPosition() : serverWorld.getSharedSpawnPos();
                    changeDimension(sleepDim, player, pos);
                } catch (NullPointerException var8) {
                    NullPointerException e = var8;
                    e.printStackTrace();
                }
                player.stopSleeping();
            }else if(have_pasterdream){
                stopSleepInBed(player);
                resourceKeys.add(pasterDream_dyedream_world);
                resourceKeys.add(pasterDream_aaroncos_arena_world);
                resourceKeys.add(pasterDream_lamp_shadow_world);
                resourceKeys.add(pasterDream_wind_journey_world);
                sleepDim = resourceKeys.get(random.nextInt(0,resourceKeys.size()));
                try {
                    ServerLevel serverWorld = player.getServer().getLevel(Level.OVERWORLD);
                    BlockPos pos = player.level().dimension() != Level.OVERWORLD ? serverPlayer.getRespawnPosition() : serverWorld.getSharedSpawnPos();
                    changeDimension(sleepDim, player, pos);
                } catch (NullPointerException var8) {
                    NullPointerException e = var8;
                    e.printStackTrace();
                }
                player.stopSleeping();
            }


        }
    }

    public static void stopSleepInBed(Player player) {
        player.getSleepingPos().filter(player.level()::hasChunkAt).ifPresent(p_261435_ -> {
            BlockState blockstate = player.level().getBlockState(p_261435_);
            if (blockstate.isBed(player.level(), p_261435_, player)) {
                Direction direction = blockstate.getValue(BedBlock.FACING);
                blockstate.setBedOccupied(player.level(), p_261435_, player, false);
            }
        });
    }

    public static boolean getRandomBoolean() {
        Random Random = new Random();
        return Random.nextBoolean();
    }

    public static int getRandomInt(int i) {
        Random Random = new Random();
        return Random.nextInt(0,i);
    }

    public static BlockPos findRandomPlantablePos(Player player, int radius, IPlantable tallFlowerBlock) {
        ServerLevel level = (ServerLevel) player.level();
        BlockPos playerPos = player.blockPosition();
        RandomSource random = level.random;

        // 最多尝试次数防止死循环
        for (int i = 0; i < 100; i++) {
            // 生成随机偏移（包含Y轴检查）
            int x = playerPos.getX() + random.nextInt(-radius, radius + 1);
            int z = playerPos.getZ() + random.nextInt(-radius, radius + 1);
            int y = findValidY(level, x, z, playerPos.getY());

            BlockPos checkPos = new BlockPos(x, y, z);
            BlockPos groundPos = checkPos.below();

            if (isValidFlowerPosition(level, checkPos, groundPos,tallFlowerBlock)) {
                return checkPos;
            }
        }
        return null;
    }

    /**
     * 在垂直方向寻找有效Y坐标
     */
    private static int findValidY(ServerLevel level, int x, int z, int baseY) {
        // 从玩家脚部位置上下各扩展3格范围
        int minY = Math.max(level.getMinBuildHeight(), baseY - 3);
        int maxY = Math.min(level.getMaxBuildHeight(), baseY + 3);

        // 优先检查玩家所在高度附近
        for (int y = baseY; y <= maxY; y++) {
            if (isAirOrReplaceable(level, new BlockPos(x, y, z))) {
                return y;
            }
        }

        // 向下搜索
        for (int y = baseY - 1; y >= minY; y--) {
            if (isAirOrReplaceable(level, new BlockPos(x, y, z))) {
                return y;
            }
        }

        return baseY; // 默认返回玩家高度
    }

    /**
     * 检查是否为空气或可替换方块
     */
    private static boolean isAirOrReplaceable(ServerLevel level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        BlockState state2 = level.getBlockState(pos.above());
        return state.isAir() &&state2.isAir() ;
    }

    /**
     * 验证花朵种植位置有效性
     */
    private static boolean isValidFlowerPosition(ServerLevel level, BlockPos pos, BlockPos groundPos, IPlantable tallFlowerBlock) {
        BlockState groundState = level.getBlockState(groundPos);
        BlockState currentState = level.getBlockState(pos);

        // 检查目标位置是否可放置
        if (!currentState.isAir()) {
            return false;
        }

        // 检查地面是否适合植物生长
        return groundState.getBlock().canSustainPlant(
                groundState,
                level,
                groundPos,
                Direction.UP,
                tallFlowerBlock
        );
    }

    public static float clamp(float value, float min, float max) {
        // This unusual condition allows keeping only one branch
        // on common path when min < max and neither of them is NaN.
        // If min == max, we should additionally check for +0.0/-0.0 case,
        // so we're still visiting the if statement.
        if (!(min < max)) { // min greater than, equal to, or unordered with respect to max; NaN values are unordered
            if (Float.isNaN(min)) {
                throw new IllegalArgumentException("min is NaN");
            }
            if (Float.isNaN(max)) {
                throw new IllegalArgumentException("max is NaN");
            }
            if (Float.compare(min, max) > 0) {
                throw new IllegalArgumentException(min + " > " + max);
            }
            // Fall-through if min and max are exactly equal (or min = -0.0 and max = +0.0)
            // and none of them is NaN
        }
        return Math.min(max, Math.max(value, min));
    }

    public static void changeDimension(ResourceKey<Level> type, Entity entity, BlockPos pos) {
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server != null) {
            ResourceKey<Level> transferDimension = entity.level().dimension() == type ? Level.OVERWORLD : type;
            ServerLevel transferWorld = server.getLevel(transferDimension);
            if (ForgeHooks.onTravelToDimension(entity, transferDimension)) {
                Entity teleportedEntity = teleportEntity(entity, transferWorld, pos);
                teleportedEntity.fallDistance = 0.0F;
            }
        }
    }
    private static Entity teleportEntity(Entity entity, ServerLevel transferWorld, BlockPos pos) {
        if (!(entity instanceof ServerPlayer player)) {
            entity.unRide();
            entity.changeDimension(transferWorld);
            Entity teleportedEntity = entity.getType().create(transferWorld);
            if (teleportedEntity == null) {
                return entity;
            } else {
                teleportedEntity.restoreFrom(entity);
                teleportedEntity.moveTo((double)pos.getX(), (double)pos.getY(), (double)pos.getZ(), entity.getYRot(), entity.getXRot());
                teleportedEntity.setYHeadRot(entity.getYRot());
                teleportedEntity.setDeltaMovement(Vec3.ZERO);
                transferWorld.addDuringTeleport(teleportedEntity);
                entity.remove(Entity.RemovalReason.CHANGED_DIMENSION);
                return teleportedEntity;
            }
        } else {
            player.teleportTo(transferWorld, (double)pos.getX(), (double)pos.getY(), (double)pos.getZ(), entity.getYRot(), entity.getXRot());
            if (transferWorld.dimension() != Level.OVERWORLD) {
                int maxY = entity.level().getHeight(Heightmap.Types.MOTION_BLOCKING, pos.getX(), pos.getZ());
                Vec3 endpointPos = new Vec3((double)pos.getX() + 0.5, (double)maxY, (double)pos.getZ() + 0.5);

                for(int x = -1; x < 2; ++x) {
                    for(int z = -1; z < 2; ++z) {
                        BlockPos newPos = BlockPos.containing(endpointPos.add((double)x, -1.0, (double)z));
                        if (transferWorld.getBlockState(newPos).getBlock() == Blocks.LAVA || transferWorld.getBlockState(newPos).getBlock() == Blocks.LAVA) {
                            transferWorld.setBlockAndUpdate(newPos, Blocks.GRASS_BLOCK.defaultBlockState());
                        }
                    }
                }

                player.moveTo(endpointPos.x(), endpointPos.y(), endpointPos.z(), entity.getYRot(), entity.getXRot());
            }

            return player;
        }
    }
}
