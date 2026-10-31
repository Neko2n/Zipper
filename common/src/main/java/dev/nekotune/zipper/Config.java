package dev.nekotune.zipper;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonIOException;
import com.google.gson.JsonSyntaxException;
import com.google.gson.annotations.Since;
import dev.nekotune.zipper.definition.DependencyInfo;
import dev.nekotune.zipper.platform.Services;
import net.minecraft.server.packs.repository.Pack;

public final class Config {

    /**
     * Where the configuration file is located.
     */
    public static final Path FILE_PATH = Path.of("config",
            "dependencies." + Zipper.MOD_ID + ".json");

    private static final Gson GSON = new GsonBuilder()
            .setVersion(1.0)
            .setPrettyPrinting()
            .create();

    public static final Config INSTANCE = fromExisting().orElse(new Config());

    // Hides the configuration button from the title screen
    @Since(1.0)
    public boolean production = false;

    @Since(1.0)
    public HashSet<DependencyInfo> dependencies = new HashSet<>();

    // Determines which packs should be hidden from selection GUIs
    @Since(1.0)
    public HashSet<String> hiddenPackIDs = new HashSet<>();

    // Overrides the loading logic of existing packs
    @Since(1.0)
    public HashMap<String, DependencyInfo.Mode> packOverrides = new HashMap<>();

    // Determines the loading order of packs
    @Since(1.0)
    public LinkedList<String> orderedPackIDs = new LinkedList<>();

    @Since(1.0)
    public boolean warnEnabled = true;

    @Since(1.0)
    public boolean promptEnabled = true;

    @Since(1.0)
    public boolean disableCompatibilityWarnings = false;

    @Since(1.0)
    public Offset buttonOffset = new Offset(0, 0);

    public static final class Offset {
        public int x;
        public int y;

        public Offset(final int x, final int y) {
            this.x = x;
            this.y = y;
        }
    }

    private Config() {
    }

    /**
     * @return The order number in which the given pack loads.
     * Returns -1 if the given pack is not in the config.
     */
    public int getLoadOrder(final DependencyInfo info) {
        return this.orderedPackIDs.indexOf(info.packId());
    }

    public int getLoadOrder(final Pack pack) {
        return this.orderedPackIDs.indexOf(pack.getId());
    }

    /**
     * Saves the current settings to the configuration file.
     */
    public void save() {
        try {
            sanitize(this);
        } catch (final IllegalStateException e) {
            Zipper.LOG.error("[Config] save() called on invalid configuration. This should never happen.");
        }
        try {
            final String json = GSON.toJson(this);
            final Path parent = FILE_PATH.getParent();
            if (parent != null) {
                Files.createDirectories(FILE_PATH.getParent());
            }
            Files.writeString(FILE_PATH, json);
            Zipper.LOG.debug("[Config] Saving SUCCESS");
        } catch (final IOException e) {
            Zipper.LOG.error("[Config] Saving FAILED: " + e.toString());
        }
    }

    private static Optional<Config> fromExisting() {
        final Path filePath = Services.PLATFORM.get().getGameDirectory()
                .resolve(FILE_PATH);
        if (!(Files.exists(filePath) && Files.isReadable(filePath))) {
            Zipper.LOG.debug("[Config] No config file found at "
                    + filePath.toString()
                    + "; Using default settings");
            return Optional.empty();
        }
        try {
            final BufferedReader reader = Files.newBufferedReader(filePath);
            final Config config = GSON.fromJson(reader, Config.class);
            reader.close();
            config.save();
            Zipper.LOG.debug("[Config] Loading SUCCESS");
            return Optional.of(config);
        } catch (final IOException | IllegalStateException | JsonIOException | JsonSyntaxException e) {
            Zipper.LOG.error("[Config] Loading FAILED: " + e.toString());
            try {
                Path backupPath;
                long i = 1;
                while (true) {
                    backupPath = filePath.resolveSibling(
                            filePath.getFileName() + "-" + i + ".bak");
                    if (!Files.exists(backupPath)) {
                        break;
                    }
                    i++;
                }
                Files.copy(filePath, backupPath, StandardCopyOption.COPY_ATTRIBUTES);
                Zipper.LOG.debug("[Config] Backup created at " + backupPath.toString());
            } catch (final IOException e2) {
                Zipper.LOG.error("[Config] Backup FAILED: " + e2.toString());
            }
            return Optional.empty();
        }
    }

    private static void sanitize(final Config config) throws IllegalStateException {

        config.orderedPackIDs = new LinkedList<>(config.orderedPackIDs.stream()
                .distinct()
                .toList());

        // Filter out invalid entries and count number of web dependencies for safety
        final AtomicInteger webDependencies = new AtomicInteger(0);
        final List<DependencyInfo> dependencies = config.dependencies.stream()
                .filter((final DependencyInfo info) -> {
                    if (info.type() == null || info.fileName() == null) {
                        return false;
                    }
                    boolean isWebDependency = false;
                    for (final String slug : info.downloadSlugs().values()) {
                        if (!slug.isEmpty()) {
                            isWebDependency = true;
                        } else {
                            continue;
                        }
                        if (DependencyInfo.SLUG_VALIDATOR.test(slug))
                            continue;
                        Zipper.LOG.warn("[Config] Invalid slug \"" + slug + "\"; removing entry");
                        return false;
                    }
                    if (isWebDependency) {
                        webDependencies.getAndIncrement();
                    }
                    return true;
                })
                .toList();
        if (webDependencies.get() > 100) {
            throw new IllegalStateException("Too many web dependencies. For user safety, downloads are capped at 100.");
        }
        config.dependencies = new LinkedHashSet<>(dependencies);
    }
}
