package dev.nekotune.zipper.platform;

import java.nio.file.Path;

import dev.nekotune.zipper.platform.services.IPlatformHelper;

import net.fabricmc.loader.api.FabricLoader;

public class FabricPlatformHelper implements IPlatformHelper {

    @Override
    public String getPlatformName() {
        return "Fabric";
    }

    @Override
    public boolean isModLoaded(String modId) {

        return FabricLoader.getInstance().isModLoaded(modId);
    }

    @Override
    public boolean isDevelopmentEnvironment() {

        return FabricLoader.getInstance().isDevelopmentEnvironment();
    }

    @Override
    public Dist dist() {
        switch (FabricLoader.getInstance().getEnvironmentType()) {
            case CLIENT:
                return Dist.CLIENT;
            case SERVER:
                return Dist.SERVER;
            default:
                throw new UnsupportedOperationException();
        }
    }

    @Override
    public Path getGameDirectory() {
        return FabricLoader.getInstance().getGameDir();
    }
}
