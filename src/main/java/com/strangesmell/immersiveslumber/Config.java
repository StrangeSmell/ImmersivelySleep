package com.strangesmell.immersiveslumber;

import net.minecraft.world.item.Item;
import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.event.config.ModConfigEvent;

import java.util.Set;

public class Config
{
	private static final ForgeConfigSpec.Builder BUILDER = new ForgeConfigSpec.Builder();

	private static final ForgeConfigSpec.BooleanValue LOG_DIRT_BLOCK = BUILDER
			.comment("Whether to log the dirt block on common setup")
			.define("logDirtBlock", true);

	private static final ForgeConfigSpec.IntValue MAGIC_NUMBER = BUILDER
			.comment("A magic number")
			.defineInRange("magicNumber", 42, 0, Integer.MAX_VALUE);

	public static final ForgeConfigSpec.ConfigValue<String> MAGIC_NUMBER_INTRODUCTION = BUILDER
			.comment("What you want the introduction message to be for the magic number")
			.define("magicNumberIntroduction", "The magic number is... ");

	private static final ForgeConfigSpec.DoubleValue DAY_SKIP_SPEED  = BUILDER
			.comment("sleep.day.skip.speed")
			.defineInRange("magicNumber", 60.0d, 0, Double.MAX_VALUE);

	private static final ForgeConfigSpec.BooleanValue CHANGE_TICK_SPEED  = BUILDER
			.comment("sleep.change.tick.speed").define("sleep.change.tick.speed",true);



	static final ForgeConfigSpec SPEC = BUILDER.build();

	public static boolean logDirtBlock;
	public static int magicNumber;
	public static String magicNumberIntroduction;
	public static Set<Item> items;



	public static double daySkipSpeed;
	public static boolean changeTickSpeed;
	public static double SLEEP_TICK_MULTIPLIER;


	@SubscribeEvent
	static void onLoad(final ModConfigEvent event)
	{
		logDirtBlock = LOG_DIRT_BLOCK.get();
		magicNumber = MAGIC_NUMBER.get();
		magicNumberIntroduction = MAGIC_NUMBER_INTRODUCTION.get();


		daySkipSpeed = DAY_SKIP_SPEED.get();
		changeTickSpeed = CHANGE_TICK_SPEED.get();
	}
}
