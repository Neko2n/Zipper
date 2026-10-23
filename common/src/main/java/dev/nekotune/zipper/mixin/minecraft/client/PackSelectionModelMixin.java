package dev.nekotune.zipper.mixin.minecraft.client;

import java.util.Collection;
import java.util.Set;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import dev.nekotune.zipper.Config;
import dev.nekotune.zipper.definition.DependencyInfo;
import dev.nekotune.zipper.definition.DependencyPack;
import net.minecraft.client.gui.screens.packs.PackSelectionModel;
import net.minecraft.server.packs.repository.Pack;
import net.minecraft.server.packs.repository.PackRepository;
import net.minecraft.server.packs.repository.PackSource;

@Mixin(PackSelectionModel.class)
public abstract class PackSelectionModelMixin {

    private static final Set<String> LOADER_PACK_IDS = Set.of(
        "mod_resources",
        "fabric"
    );

    // Hide hidden packs 
    @Redirect(method = "findNewPacks", at = @At(value = "INVOKE", target = "Lnet/minecraft/server/packs/repository/PackRepository;getAvailablePacks()Ljava/util/Collection;"))
    private Collection<Pack> zipper$getVisiblePacks(final PackRepository repository) {
        return repository.getAvailablePacks().stream()
                .filter(p -> isVisible(repository, p))
                .toList();
    }

    private static boolean isVisible(final PackRepository repository, final Pack pack) {

        // Hide built-in packs depending on configuration settings
        if ((pack.getPackSource() == PackSource.BUILT_IN || LOADER_PACK_IDS.contains(pack.getId()))
                && Config.INSTANCE.hideBuiltIn) {
            return false;
        } else if (pack instanceof DependencyPack.ModpackResources) {
            return true;
        }

        // Hide hidden dependency packs depending on configuration settings
        if (pack instanceof final DependencyPack zipperPack) {
            if (zipperPack.info.mode() == DependencyInfo.Mode.FORCED) {
                return !Config.INSTANCE.hideForced;
            }
            return !zipperPack.info.mode().isHidden;
        }

        // Use vanilla logic on non-dependency packs
        return repository.isAvailable(pack.getId());
    }
}
