package dev.nekotune.zipper.platform.services;

import java.nio.file.Path;

public interface IPlatformHelper {

    public static enum Dist {
        CLIENT,
        SERVER;
    }

    /**
     * Gets the name of the current platform
     *
     * @return The name of the current platform.
     */
    public abstract String getPlatformName();

    /**
     * Checks if a mod with the given id is loaded.
     *
     * @param modId The mod to check if it is loaded.
     * @return True if the mod is loaded, false otherwise.
     */
    public abstract boolean isModLoaded(String modId);

    /**
     * Check if the game is currently in a development environment.
     *
     * @return True if in a development environment, false otherwise.
     */
    public abstract boolean isDevelopmentEnvironment();

    /**
     * Check which logical side the game is running on.
     * 
     * @return Distribution type
     */
    public abstract Dist dist();

    /**
     * Gets the current working directory of the game (the .minecraft folder)
     * 
     * @return The Path object for the game directory
     */
    public abstract Path getGameDirectory();

    /**
     * Gets the name of the environment type as a string.
     *
     * @return The name of the environment type.
     */
    public default String getEnvironmentName() {

        return isDevelopmentEnvironment() ? "development" : "production";
    }
}