package de.dertoaster.multihitboxlib;

import org.slf4j.Logger;

import com.mojang.logging.LogUtils;

import de.dertoaster.multihitboxlib.assetsynch.AbstractAssetEnforcementManager;
import de.dertoaster.multihitboxlib.init.MHLibDatapackLoaders;
import de.dertoaster.multihitboxlib.init.MHLibPackets;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.server.ServerStartingEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;

// The value here should match an entry in the META-INF/mods.toml file
@Mod(Constants.MODID)
public class MHLibMod {
	public static final Logger LOGGER = LogUtils.getLogger();

	public MHLibMod() {
		IEventBus modEventBus = FMLJavaModLoadingContext.get().getModEventBus();

		// Register the commonSetup method for modloading
		modEventBus.addListener(this::commonSetup);

		// Register ourselves for server and other game events we are interested in
		MinecraftForge.EVENT_BUS.register(this);

		MHLibDatapackLoaders.init();
		// Throws registration event and registers all asset enforcers
		AbstractAssetEnforcementManager.init();
	}

	private void commonSetup(final FMLCommonSetupEvent event) {
		MHLibPackets.init();
	}

	// You can use SubscribeEvent and let the Event Bus discover methods to call
	@SubscribeEvent
	public void onServerStarting(ServerStartingEvent event) {
	}

}
