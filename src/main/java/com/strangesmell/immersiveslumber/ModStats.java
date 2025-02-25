package com.strangesmell.immersiveslumber;


import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.stats.StatFormatter;
import net.minecraft.stats.Stats;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;

import java.util.function.Supplier;

import static com.strangesmell.immersiveslumber.ImmersiveSlumber.MODID;


public class ModStats {

    private static final DeferredRegister<ResourceLocation> CUSTOM_STATS = DeferredRegister.create(Registries.CUSTOM_STAT, MODID);
    public static final Supplier<ResourceLocation> SLEEP_COUNT = CUSTOM_STATS.register("sleep_count",
            () -> makeCustomStat("sleep_count", StatFormatter.DEFAULT));

/*
    public static final Supplier<ResourceLocation> TIME_SLEPT = CUSTOM_STATS.register("time_slept",
            () -> makeCustomStat("time_slept", StatFormatter.TIME));
    public static final Supplier<ResourceLocation> WELL_RESTED_SLEEPS = CUSTOM_STATS.register("well_rested_sleeps",
            () -> makeCustomStat("well_rested_sleeps", StatFormatter.DEFAULT));
    public static final Supplier<ResourceLocation> TIRED_SLEEPS = CUSTOM_STATS.register("tired_sleeps",
            () -> makeCustomStat("tired_sleeps", StatFormatter.DEFAULT));
    public static final Supplier<ResourceLocation> HEALTH_REGAINED = CUSTOM_STATS.register("health_regained",
            () -> makeCustomStat("health_regained", StatFormatter.DEFAULT));
*/

    private static ResourceLocation makeCustomStat(String name, StatFormatter formatter) {
        ResourceLocation id = ResourceLocation.tryBuild(MODID, name);
        Registry.register(BuiltInRegistries.CUSTOM_STAT, id, id);
        Stats.CUSTOM.get(id, formatter);
        return id;
    }

    private static void registerCustomStat(ResourceLocation id, StatFormatter statFormatter) {
        Registry.register(BuiltInRegistries.CUSTOM_STAT, id, id);
        Stats.CUSTOM.get(id, statFormatter);
    }

    public static void register(IEventBus modBus) {
        CUSTOM_STATS.register(modBus);
    }
}