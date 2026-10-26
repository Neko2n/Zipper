package dev.nekotune.zipper.platform;

import java.nio.file.Path;

import dev.nekotune.zipper.platform.services.IPlatformHelper;

import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.loading.FMLEnvironment;
import net.minecraftforge.fml.loading.FMLLoader;
import net.minecraftforge.fml.loading.FMLPaths;

public class ForgePlatformHelper implements IPlatformHelper {

    @Override
    public String getPlatformName() {

        return "Forge";
    }

    @Override
    public boolean isModLoaded(String modId) {

        return ModList.get().isLoaded(modId);
    }

    @Override
    public boolean isDevelopmentEnvironment() {

        return !FMLLoader.isProduction();
    }

    @Override
    public Dist dist() {
        switch (FMLEnvironment.dist) {
            case CLIENT:
                return Dist.CLIENT;
            case DEDICATED_SERVER:
                return Dist.SERVER;
            default:
                throw new UnsupportedOperationException();
        }
    }

    @Override
    public Path getGameDirectory() {
        return FMLPaths.GAMEDIR.get();
    }
}