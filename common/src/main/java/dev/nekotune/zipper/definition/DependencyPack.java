package dev.nekotune.zipper.definition;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.EnumMap;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import dev.nekotune.zipper.Config;
import net.minecraft.ChatFormatting;
import net.minecraft.SharedConstants;
import net.minecraft.network.chat.Component;
import net.minecraft.server.packs.FilePackResources;
import net.minecraft.server.packs.PackLocationInfo;
import net.minecraft.server.packs.PackSelectionConfig;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.PathPackResources;
import net.minecraft.server.packs.repository.Pack;
import net.minecraft.server.packs.repository.PackCompatibility;
import net.minecraft.server.packs.repository.PackSource;
import net.minecraft.world.flag.FeatureFlagSet;

/**
 * Custom Pack implementation for modpack dependencies.
 */
public class DependencyPack extends Pack {

    public static final int TOKEN_COLOR = 0xFFF5AA42;

    private static final Component BADGE = Component.literal(" (modpack)")
            .withStyle(ChatFormatting.BOLD);

    /**
     * Custom source type decorator for modpack-loaded dependency packs.
     */
    public static final PackSource SOURCE = PackSource.create((final Component name) -> {
        return Component.empty().append(name)
                .append(BADGE).withColor(TOKEN_COLOR);
    }, true);

    public final DependencyInfo info;

    protected DependencyPack(final DependencyInfo info, final PackLocationInfo location,
            final ResourcesSupplier resources, final Metadata metadata,
            final PackSelectionConfig selectionConfig) {
        super(location, resources, metadata, selectionConfig);
        this.info = info;
    }

    /**
     * Builds a dependency pack from the given dependency information.
     * 
     * @param info The information about the dependency to build the pack for.
     * @return The newly created DependencyPack object.
     * @throws IOException If the pack's file metadata could not be read.
     */
    public static DependencyPack from(final DependencyInfo info) throws IOException {
        final var location = new PackLocationInfo(info.packId(),
                Component.literal(info.fileName()), SOURCE, Optional.empty());
        final Pack.ResourcesSupplier supplier;
        if (Files.isDirectory(info.packDir())) {
            supplier = new PathPackResources.PathResourcesSupplier(info.packDir());
        } else {
            supplier = new FilePackResources.FileResourcesSupplier(info.packDir());
        }
        final int version = SharedConstants.getCurrentVersion().getPackVersion(info.type());
        final Metadata meta = Pack.readPackMetadata(location, supplier, version);
        if (meta == null) {
            throw new IOException("File metadata could not be read for dependency " + info.toString());
        }
        final boolean required = info.mode() == DependencyInfo.Mode.FORCED;
        final Position pos = required ? Position.BOTTOM : Position.TOP;
        final var selectionConfig = new PackSelectionConfig(required, pos, required);
        return new DependencyPack(info, location, supplier, meta, selectionConfig);
    }

    @Override
    public PackCompatibility getCompatibility() {
        return Config.INSTANCE.disableCompatibilityWarnings
                ? PackCompatibility.COMPATIBLE
                : super.getCompatibility();
    }

    @Override
    public Component getTitle() {
        return Component.literal(info.fileName());
    }

    public static final class ModpackResources extends DependencyPack {

        /**
         * The directory which holds modpack resources.
         */
        private static final String FOLDER = "modpack";
        private static final Path ASSETS_PATH = Path.of("resourcepacks", FOLDER, "assets");
        private static final Path DATA_PATH = Path.of("datapacks", FOLDER, "data");

        private static final EnumMap<PackType, ModpackResources> cache = new EnumMap<>(PackType.class);

        private ModpackResources(final DependencyInfo info) {
            super(info, ModpackResources$Info.LOCATION,
                    info.type() == PackType.CLIENT_RESOURCES
                            ? new PathPackResources.PathResourcesSupplier(ASSETS_PATH.getParent())
                            : new PathPackResources.PathResourcesSupplier(DATA_PATH.getParent()),
                    ModpackResources$Info.META, ModpackResources$Info.SELECTION_CONFIG);
        }

        /**
         * Builds a dependency pack from the local modpack resources folder.
         * Caches the result, so future calls will return an existing object.
         * 
         * @return Dependency pack object.
         */
        public static ModpackResources get(final PackType packType) throws IOException {
            if (cache.containsKey(packType)) {
                return cache.get(packType);
            }
            return define(packType);
        }

        private static ModpackResources define(final PackType packType) throws IOException {
            if (!Files.exists(DATA_PATH)) {
                Files.createDirectories(DATA_PATH);
            }
            if (!Files.exists(ASSETS_PATH)) {
                Files.createDirectories(ASSETS_PATH);
            }
            final var info = new DependencyInfo(packType, ModpackResources$Info.ID,
                    ModpackResources$Info.ID, List.of(), Set.of(),
                    DependencyInfo.Mode.FORCED, 0);
            final ModpackResources instance = new ModpackResources(info);
            cache.put(packType, instance);
            return instance;
        }

        private static final class ModpackResources$Info {
            public static final String ID = "modpack";
            public static final Component NAME = Component.literal("modpack resources");
            public static final PackLocationInfo LOCATION = new PackLocationInfo(ID,
                    NAME, PackSource.BUILT_IN, Optional.empty());
            public static final Metadata META = new Metadata(NAME, PackCompatibility.COMPATIBLE,
                    FeatureFlagSet.of(), List.of());
            public static final PackSelectionConfig SELECTION_CONFIG = new PackSelectionConfig(
                    true, Position.BOTTOM, true);
        }
    }
}
