package dev.nekotune.zipper.definition;

import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;
import java.util.function.Supplier;

import dev.nekotune.zipper.definition.web.Curseforge;
import dev.nekotune.zipper.definition.web.Modrinth;
import dev.nekotune.zipper.definition.web.WebHost;
import dev.nekotune.zipper.platform.Services;
import net.minecraft.server.packs.PackType;

/**
 * Information about a downloaded dependency.
 * 
 * @param slugInput         The slug to search up and download from.
 * @param mirrors      Mirror slugs for if the initial slug failed to find a
 *                     match.
 * @param hosts        The website host(s) to attempt to download from.
 * @param mode         The way the dependency should be loaded into the game
 *                     once
 *                     downloaded.
 * @param type         Whether the dependency is a resource pack or a data pack.
 */
public record DependencyInfo(
        PackType type,
        String fileName,
        Mode mode,
        EnumMap<DependencyInfo.Host, String> downloadSlugs) {

    /**
     * Validates a given slug string.
     * This validator must ensure that the slug is a valid path.
     */
    public static final Predicate<String> SLUG_VALIDATOR = (final String slug) -> {
        return (slug.isEmpty() || slug.matches("^[\\w-]+$"))
            && slug.length() <= 50;
    };

    /**
     * @param packType The type of pack to create information for.
     * @return The default construction of a DependencyInfo object.
     */
    public static final DependencyInfo createDefault(final PackType packType) {
        return new DependencyInfo(packType, "New Dependency", DependencyInfo.Mode.OPTIONAL_ENABLED,
                new EnumMap<>(DependencyInfo.Host.class));
    }

    public DependencyInfo {
        mode = mode != null ? mode : Mode.OPTIONAL_DISABLED;
        downloadSlugs = downloadSlugs != null ? downloadSlugs : new EnumMap<>(DependencyInfo.Host.class);
    }

    private static final Map<PackType, Path> folder$CACHE = new EnumMap<>(PackType.class);

    /**
     * Returns the folder which holds packs of the given pack type.
     * 
     * @param packType The pack type to return the folder for
     * @return Folder directory path
     */
    public static Path folder(final PackType packType) {
        return folder$CACHE.computeIfAbsent(packType, t -> {
            Path dir = Services.PLATFORM.get().getGameDirectory();
            switch (packType) {
                case CLIENT_RESOURCES:
                    dir = dir.resolve("resourcepacks");
                    break;
                case SERVER_DATA:
                    dir = dir.resolve("datapacks");
                    break;
                default:
                    throw new UnsupportedOperationException();
            }
            return dir.resolve(".zipper");
        });
    }

    @Override
    public String toString() {
        return "{fileName: " + fileName
                + ", type: " + type.toString()
                + ", mode: " + mode.toString()
                + ", downloadSlugs: " + downloadSlugs.toString()
                + "}";
    }

    /**
     * @return True if this pack's file already exists on the user's machine,
     *         false otherwise.
     */
    public boolean fileExists() {
        return Files.exists(this.packDir(), LinkOption.NOFOLLOW_LINKS);
    }

    /**
     * @return The pack ID representing the dependency.
     * @see DependencyPack
     */
    public String packId() {
        return "modpack/" + fileName();
    }

    /**
     * @return {@link DependencyInfo#fileName} + the file extension (.zip), as a Path object.
     */
    public Path file() {
        return Path.of(fileName() + ".zip");
    }

    /**
     * @return The full pack directory path this dependency exists at.
     */
    public Path packDir() {
        return folder(type).resolve(file());
    }

    public static enum Host implements Supplier<WebHost> {
        CURSEFORGE(Curseforge.INSTANCE, 0xFFF16436),
        MODRINTH(Modrinth.INSTANCE, 0xFF1BD96A);

        public static List<Host> any() {
            return List.of(Host.values());
        }

        private final WebHost website;
        public final String displayName;
        public final int displayColor;

        private Host(final WebHost website, final int displayColor) {
            this.website = website;
            this.displayColor = displayColor;
            String displayName = "";
            for (final String term : this.name().toLowerCase()
                    .split("_")) {
                displayName += term.substring(0, 1).toUpperCase()
                        + term.substring(1) + " ";
            }
            this.displayName = displayName.strip();
        }

        public boolean downloads(final DependencyInfo target) {
            return !target.downloadSlugs().getOrDefault(this, "").isBlank();
        }

        @Override
        public WebHost get() {
            return website;
        }
    }

    /**
     * How this dependency should be loaded into the game.
     */
    public static enum Mode {
        /**
         * The dependency will be force-enabled and hidden.
         * Use this for mandatory game assets.
         * All dependencies marked FORCED will be loaded before all other dependencies
         * and resources,
         * so that they may be overridden.
         */
        FORCED(true),

        /**
         * The dependency will be enabled by default, but users can disable it.
         */
        OPTIONAL_ENABLED(false),

        /**
         * The dependency will be disabled by default, but users can enable it.
         */
        OPTIONAL_DISABLED(false),

        /**
         * The dependency will be downloaded, but will not be added to the game.
         * Use this for when you need to distribute specific assets,
         * modified or otherwise, and still need to support the author.
         */
        SUPPORT(true);

        public final boolean isHidden;

        private Mode(final boolean isHidden) {
            this.isHidden = isHidden;
        }

        public boolean matches(final DependencyInfo target) {
            return target.mode == this;
        }
    }
}