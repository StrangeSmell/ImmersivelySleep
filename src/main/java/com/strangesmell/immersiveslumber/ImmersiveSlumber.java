package com.strangesmell.immersiveslumber;

import net.minecraft.nbt.CompoundTag;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static com.strangesmell.immersiveslumber.TimeProgressionHandler.initMessage;
import static com.strangesmell.immersiveslumber.TimeProgressionHandler.initSoundMessage;

@Mod(ImmersiveSlumber.MODID)
public class ImmersiveSlumber
{
	public static final Logger LOGGER = LogManager.getLogger();
	public static final String MODID = "immersiveslumber";

	public static int x=0;
	public static int y=0;
	public static int timeAfterUp=60;
	public static boolean haveRandom=false;
	public static Map<UUID, CompoundTag> playerNbt=new HashMap<>();


	public ImmersiveSlumber()
	{

		IEventBus modBus = FMLJavaModLoadingContext.get().getModEventBus();
		ModStats.register(modBus);
		initMessage();
		initSoundMessage();
	}
}