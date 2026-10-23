package dev.nekotune.zipper;

import dev.nekotune.zipper.platform.PlatformEvents;
import dev.nekotune.zipper.platform.Services;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.server.ServerStartingEvent;

@Mod(Zipper.MOD_ID)
public class NeoForgeLoader {

    public NeoForgeLoader(final ModContainer mod) {
        Services.init(this.getClass().getClassLoader());
        Zipper.init();

        // Hook up configuration screen to the mods menu button
        if (FMLEnvironment.dist == Dist.CLIENT)
            NeoForgeConfigScreen.register(mod);

        NeoForge.EVENT_BUS.register(this);
    }
    
    @SubscribeEvent
    public void onServerStarting(final ServerStartingEvent event) {
        PlatformEvents.SERVER_STARTING.controller.post(event.getServer());
    }
}