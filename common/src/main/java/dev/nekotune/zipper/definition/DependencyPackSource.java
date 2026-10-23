package dev.nekotune.zipper.definition;

import java.io.IOException;
import java.nio.file.Files;
import java.util.function.Consumer;
import dev.nekotune.zipper.Config;
import dev.nekotune.zipper.Constants;
import dev.nekotune.zipper.definition.DependencyPack.ModpackResources;
import dev.nekotune.zipper.mixin.minecraft.shared.PackRepositoryMixin;

import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.repository.Pack;
import net.minecraft.server.packs.repository.RepositorySource;

/**
 * Repository source type which loads dependency packs.
 * 
 * @see PackRepositoryMixin
 */
public class DependencyPackSource implements RepositorySource {

    private final PackType packType;

    public DependencyPackSource(final PackType packType) {
        this.packType = packType;
    }

    @Override
    public void loadPacks(final Consumer<Pack> onLoad) {

        // Load web dependencies
        for (final DependencyInfo dependency : Config.INSTANCE.dependencies) {
            if (dependency.type() != this.packType)
                continue;
            if (dependency.mode() == DependencyInfo.Mode.SUPPORT)
                continue;
            if (!Files.exists(dependency.packDir()))
                continue;
            try {
                final DependencyPack pack = DependencyPack.from(dependency);
                onLoad.accept(pack);
            } catch (final IOException e) {
                Constants.LOG.error("Failed to load pack for dependency: " + dependency, e);
            }
        }

        // Load modpack resources
        try {
            onLoad.accept(ModpackResources.get(this.packType));
        } catch (final IOException e) {
            Constants.LOG.error("Failed to load modpack resources", e);
        }
    }
}
