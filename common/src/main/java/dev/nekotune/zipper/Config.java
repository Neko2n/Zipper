package dev.nekotune.zipper;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.annotations.Since;
import dev.nekotune.zipper.definition.DependencyInfo;
import net.minecraft.server.packs.PackType;

public final class Config {

    /**
     * Where the configuration file is located.
     */
    public static final Path FILE_PATH = Path.of("config",
            "dependencies." + Constants.MOD_ID + ".json");

    private static final Gson GSON = new GsonBuilder()
            .setVersion(1.0)
            .setPrettyPrinting()
            .create();

    public static final Config INSTANCE = fromExisting().orElse(new Config());

    @Since(1.0)
    public boolean production = false;

    @Since(1.0)
    public Map<String, DependencyInfo> dependencies = new HashMap<>();

    @Since(1.0)
    public boolean hideForced = true;

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
            Constants.LOG.debug("[Config] Saving SUCCESS");
        } catch (final IOException e) {
            Constants.LOG.error("[Config] Saving FAILED: " + e.toString());
        }
    }

    private static Optional<Config> fromExisting() {
        if (!(Files.exists(FILE_PATH) && Files.isReadable(FILE_PATH))) {
            Constants.LOG.debug("[Config] No config file found at "
                    + FILE_PATH.toString()
                    + "; Using default settings");
            return Optional.empty();
        }
        try {
            final BufferedReader reader = Files.newBufferedReader(FILE_PATH);
            final Config config = GSON.fromJson(reader, Config.class);
            reader.close();
            sanitize(config);
            Constants.LOG.debug("[Config] Loading SUCCESS");
            return Optional.of(config);
        } catch (final IOException e) {
            Constants.LOG.error("[Config] Loading FAILED: " + e.toString());
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
                Constants.LOG.debug("[Config] Backup created at " + backupPath.toString());
            } catch (final IOException e2) {
                Constants.LOG.error("[Config] Backup FAILED: " + e2.toString());
            }
            return Optional.empty();
        }
    }

    private static void sanitize(final Config config) throws IllegalStateException {

        // Filter out invalid entries and count number of web dependencies for safety
        final AtomicInteger webDependencies = new AtomicInteger(0);
        final List<DependencyInfo> dependencies = config.dependencies.keySet().stream()
                .filter((final String fileName) -> {
                    final DependencyInfo dependency = config.dependencies.get(fileName);
                    if (dependency.type() == null || dependency.fileName() == null) {
                        return false;
                    }
                    boolean isWebDependency = false;
                    for (final String slug : dependency.downloadSlugs().values()) {
                        if (!slug.isEmpty()) {
                            isWebDependency = true;
                        } else {
                            continue;
                        }
                        if (DependencyInfo.SLUG_VALIDATOR.test(slug))
                            continue;
                        Constants.LOG.warn("[Config] Invalid slug \"" + slug + "\"; removing entry");
                        return false;
                    }
                    if (isWebDependency) {
                        webDependencies.getAndIncrement();
                    }
                    return true;
                })
                .map(fileName -> config.dependencies.get(fileName))
                .toList();
        if (webDependencies.get() > 100) {
            throw new IllegalStateException("Too many web dependencies. For user safety, downloads are capped at 100.");
        }

        // Ensure packs' loading priorities map cleanly to their order in their repositories
        final Comparator<DependencyInfo> sortByPriority = Comparator
                .comparingInt((final DependencyInfo d) -> d.loadPriority());
        config.dependencies = new HashMap<>();
        for (final PackType packType : PackType.values()) {
            final List<DependencyInfo> sorted = dependencies.stream()
                    .filter(d -> d.type() == packType)
                    .sorted(sortByPriority)
                    .toList();
            for (int i = 0; i < sorted.size(); i++) {
                DependencyInfo dependency = sorted.get(i);
                if (dependency.loadPriority() != i) {
                    dependency = new DependencyInfo(dependency.type(), dependency.fileName(),
                            dependency.mode(), i, dependency.downloadSlugs());
                }
                config.dependencies.put(dependency.fileName(), dependency);
            }
        }

        config.save();
    }
}
