package dev.nekotune.zipper.mixin.minecraft.shared;

import java.util.Arrays;
import java.util.Collection;
import java.util.List;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import dev.nekotune.zipper.Zipper;
import dev.nekotune.zipper.definition.DependencyPackSource;

import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.repository.BuiltInPackSource;
import net.minecraft.server.packs.repository.Pack;
import net.minecraft.server.packs.repository.PackRepository;
import net.minecraft.server.packs.repository.RepositorySource;

@Mixin(PackRepository.class)
public class PackRepositoryMixin {

    /**
     * Injects a dependency repository source whenever a pack repository
     * is initialized with a built-in source.
     */
    @ModifyVariable(method = "<init>", at = @At("HEAD"), argsOnly = true)
    private static RepositorySource[] zipper$injectDependencyPackSourceServer(final RepositorySource[] sources) {
        for (final RepositorySource source : sources) {
            if (source instanceof BuiltInPackSource) {
                final var accessor = (BuiltInPackSourceAccessor) source;
                final RepositorySource[] extended = Arrays.copyOf(sources, sources.length + 1);
                final PackType packType = accessor.zipper$getPackType();
                final RepositorySource added = new DependencyPackSource(packType);
                extended[sources.length] = added;
                return extended;
            }
        }
        return sources;
    }

    /**
     * Automatically enforce pack order of dependency packs
     */
    @Inject(method = "rebuildSelected", at = @At("RETURN"), cancellable = true)
    private void zipper$reorderDependencies(final Collection<String> ids,
            final CallbackInfoReturnable<List<Pack>> cir) {
        cir.setReturnValue(Zipper.reorderPacks(cir.getReturnValue()));
    }
}
