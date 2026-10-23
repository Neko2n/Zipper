package dev.nekotune.zipper.definition;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import dev.nekotune.zipper.Config;
import dev.nekotune.zipper.Zipper;
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

    public static final Pack.Metadata TEMPLATE_META = new Pack.Metadata(Component.empty(), PackCompatibility.COMPATIBLE,
            FeatureFlagSet.of(), List.of());
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

    private static PackLocationInfo getPackLocation(final DependencyInfo info) {
        return new PackLocationInfo(info.packId(),
                Component.literal(info.fileName()), SOURCE, Optional.empty());
    }

    private static Pack.ResourcesSupplier getPackSupplier(final DependencyInfo info) {
        final Pack.ResourcesSupplier supplier;
        if (Files.isDirectory(info.packDir())) {
            supplier = new PathPackResources.PathResourcesSupplier(info.packDir());
        } else {
            supplier = new FilePackResources.FileResourcesSupplier(info.packDir());
        }
        return supplier;
    }

    private static PackSelectionConfig getPackSelectionConfig(final DependencyInfo info) {
        final boolean required = info.mode() == DependencyInfo.Mode.FORCED;
        final Position pos = required ? Position.BOTTOM : Position.TOP;
        return new PackSelectionConfig(required, pos, required);
    }

    /**
     * Builds a dependency pack from the given dependency information, with the
     * given metadata.
     * 
     * @param info The information about the dependency to build the pack for.
     * @param meta The metadata to apply to the pack.
     * @return The newly created DependencyPack object.
     */
    public static DependencyPack from(final DependencyInfo info, final Metadata meta) {
        final PackLocationInfo location = getPackLocation(info);
        final Pack.ResourcesSupplier supplier = getPackSupplier(info);
        final PackSelectionConfig selectionConfig = getPackSelectionConfig(info);
        return new DependencyPack(info, location, supplier, meta, selectionConfig);
    }

    /**
     * Builds a dependency pack from the given dependency information.
     * 
     * @param info The information about the dependency to build the pack for.
     * @return The newly created DependencyPack object.
     * @throws IOException If the pack's file metadata could not be read.
     */
    public static DependencyPack from(final DependencyInfo info) throws IOException {
        final PackLocationInfo location = getPackLocation(info);
        final Pack.ResourcesSupplier supplier = getPackSupplier(info);
        final PackSelectionConfig selectionConfig = getPackSelectionConfig(info);
        final int version = SharedConstants.getCurrentVersion().getPackVersion(info.type());
        final Metadata meta = Pack.readPackMetadata(location, supplier, version);
        if (meta == null) {
            throw new IOException("File metadata could not be read for dependency " + info.toString());
        }
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
        private static final EnumMap<PackType, Path> PATHS = new EnumMap<>(Map.of(
                PackType.CLIENT_RESOURCES, Path.of("resourcepacks", FOLDER, "assets"),
                PackType.SERVER_DATA, Path.of("datapacks", FOLDER, "data")));
        private static final EnumMap<PackType, ModpackResources> cache = new EnumMap<>(PackType.class);

        private ModpackResources(final DependencyInfo info) {
            super(info, ModpackResources$Info.LOCATION,
                    new PathPackResources.PathResourcesSupplier(PATHS.get(info.type()).getParent()),
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
            final Path directory = PATHS.get(packType);
            if (!Files.exists(directory)) {
                Files.createDirectories(directory);
            }
            final var info = new DependencyInfo(packType, ModpackResources$Info.NAME,
                    DependencyInfo.Mode.FORCED, new EnumMap<>(DependencyInfo.Host.class));
            final ModpackResources instance = new ModpackResources(info);
            cache.put(packType, instance);
            return instance;
        }

        private static final class ModpackResources$Info {
            public static final String NAME = "Modpack Resources";
            public static final Component DESCRIPTION = Component
                    .translatable(Zipper.MOD_ID + ".modpack-resources.description");
            public static final PackLocationInfo LOCATION = new PackLocationInfo(NAME,
                    DESCRIPTION, PackSource.BUILT_IN, Optional.empty());
            public static final Metadata META = new Metadata(DESCRIPTION, PackCompatibility.COMPATIBLE,
                    FeatureFlagSet.of(), List.of());
            public static final PackSelectionConfig SELECTION_CONFIG = new PackSelectionConfig(
                    true, Position.BOTTOM, true);
        }
    }
}
