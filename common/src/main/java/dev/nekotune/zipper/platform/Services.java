package dev.nekotune.zipper.platform;

import com.google.common.base.Suppliers;

import dev.nekotune.zipper.Zipper;
import dev.nekotune.zipper.platform.services.IPlatformHelper;

import java.util.ServiceLoader;
import java.util.function.Supplier;

public final class Services {
    public static final Supplier<IPlatformHelper> PLATFORM = Suppliers.memoize(
            () -> load(IPlatformHelper.class));

    private static ClassLoader loader;

    /**
     * Initializes the service loader to use the given class loader.
     * 
     * @param loader The class loader to load services with.
     */
    public static void init(final ClassLoader loader) {
        Services.loader = loader;
    }

    /**
     * Loads a platform-specific service class.
     * 
     * @param <T>   The class type
     * @param clazz The class to load
     * @return The loaded service
     */
    private static <T> T load(Class<T> clazz) {
        final T loadedService = ServiceLoader.load(clazz, loader)
                .findFirst()
                .orElseThrow(() -> new NullPointerException("Failed to load service for " + clazz.getName()));
        Zipper.LOG.debug("Loaded {} for service {}", loadedService, clazz);
        return loadedService;
    }
}