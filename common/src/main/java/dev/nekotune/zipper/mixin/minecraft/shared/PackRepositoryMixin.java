package dev.nekotune.zipper.mixin.minecraft.shared;

import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedList;
import java.util.List;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import dev.nekotune.zipper.Config;
import dev.nekotune.zipper.Zipper;
import dev.nekotune.zipper.definition.DependencyPack;
import dev.nekotune.zipper.definition.DependencyPack.ModpackResources;
import dev.nekotune.zipper.definition.DependencyPackSource;

import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.repository.BuiltInPackSource;
import net.minecraft.server.packs.repository.Pack;
import net.minecraft.server.packs.repository.PackRepository;
import net.minecraft.server.packs.repository.PackSource;
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
        final List<Pack> packs = cir.getReturnValue();
        cir.setReturnValue(reorderDependencies(packs));
    }

    private static List<Pack> reorderDependencies(final List<Pack> packs) {

        // Sort dependency packs into lists
        final LinkedList<DependencyPack> modpackResources = new LinkedList<>();
        final LinkedList<DependencyPack> forced = new LinkedList<>();
        final LinkedList<DependencyPack> support = new LinkedList<>();
        for (final Pack pack : packs) {
            if (!(pack instanceof final DependencyPack dependency))
                continue;
            if (pack instanceof final ModpackResources resources) {
                modpackResources.add(resources);
                continue;
            }
            switch (dependency.info.mode()) {
                case FORCED:
                    forced.add(dependency);
                    break;
                case SUPPORT:
                    support.add(dependency);
                    break;
                default:
                    break;
            }
        }

        // Remove support packs
        final LinkedList<Pack> result = new LinkedList<>(packs);
        result.removeAll(support);

        // Sort forced packs by priority
        forced.sort(Comparator.comparingInt((final DependencyPack pack) -> Config.INSTANCE.getLoadOrder(pack.info))
                .reversed());

        // Modpack resources are always-on-top forced packs
        forced.addAll(modpackResources);

        // Remove and re-insert forced packs after built-in packs
        result.removeAll(forced);
        int i = 0;
        while (i < result.size()) {
            final Pack candidate = result.get(i);
            if (candidate.getPackSource() == PackSource.BUILT_IN
                    || (candidate.isRequired() && candidate.getPackSource() == PackSource.DEFAULT)) {
                i++;
                Zipper.LOG.debug("[PackRepositoryMixin] Forced dependency insert shifted after pack \""
                        + candidate.getId() + "\" to position " + i);
            } else {
                break;
            }
        }
        result.addAll(i, forced);
        Zipper.LOG.debug("[PackRepositoryMixin] Inserted " + forced.size()
                + " forced dependency packs at position " + i);
        
        return result;
    }
}
