package dev.nekotune.zipper;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonIOException;
import com.google.gson.JsonSyntaxException;
import com.google.gson.annotations.Since;
import dev.nekotune.zipper.definition.DependencyInfo;

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

    @Since(1.0)
    public boolean production = false;

    @Since(1.0)
    public LinkedHashSet<DependencyInfo> dependencies = new LinkedHashSet<>();

    @Since(1.0)
    public boolean hideForced = true;

    @Since(1.0)
    public boolean hideBuiltIn = true;

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
        int pos = 0;
        for (final DependencyInfo dependency : this.dependencies) {
            if (dependency == info)
                return pos;
            pos++;
        }
        return -1;
    }

    /**
     * Saves the current settings to the configuration file.
     */
    public void save() {
        final String json = GSON.toJson(this);
        try {
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
        if (!(Files.exists(FILE_PATH) && Files.isReadable(FILE_PATH))) {
            Zipper.LOG.debug("[Config] No config file found at "
                    + FILE_PATH.toString()
                    + "; Using default settings");
            return Optional.empty();
        }
        try {
            final BufferedReader reader = Files.newBufferedReader(FILE_PATH);
            final Config config = GSON.fromJson(reader, Config.class);
            reader.close();
            sanitize(config);
            Zipper.LOG.debug("[Config] Loading SUCCESS");
            return Optional.of(config);
        } catch (final IOException | JsonIOException | JsonSyntaxException e) {
            Zipper.LOG.error("[Config] Loading FAILED: " + e.toString());
            try {
                Path backupPath;
                long i = 1;
                while (true) {
                    backupPath = FILE_PATH.resolveSibling(
                            FILE_PATH.getFileName() + "-" + i + ".bak");
                    if (!Files.exists(backupPath)) {
                        break;
                    }
                    i++;
                }
                Files.copy(FILE_PATH, backupPath, StandardCopyOption.COPY_ATTRIBUTES);
                Zipper.LOG.debug("[Config] Backup created at " + backupPath.toString());
            } catch (final IOException e2) {
                Zipper.LOG.error("[Config] Backup FAILED: " + e2.toString());
            }
            return Optional.empty();
        }
    }

    private static void sanitize(final Config config) throws IllegalStateException {

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

        config.save();
    }
}
