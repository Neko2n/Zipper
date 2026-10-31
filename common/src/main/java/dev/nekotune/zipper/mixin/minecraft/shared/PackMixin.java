package dev.nekotune.zipper.mixin.minecraft.shared;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import dev.nekotune.zipper.Config;
import dev.nekotune.zipper.definition.DependencyInfo;
import net.minecraft.server.packs.repository.Pack;

/**
 * Force-apply the required flag to packs as determined by the configuration.
 */
@Mixin(value = Pack.class)
public class PackMixin {
    
    @Inject(method = "isRequired", at = @At("RETURN"), cancellable = true)
    private void zipper$isRequired(final CallbackInfoReturnable<Boolean> cir) {
        if (cir.getReturnValue())
            return;
        final String id = ((Pack)(Object)this).getId();
        if (Config.INSTANCE.packOverrides.containsKey(id)) {
            final DependencyInfo.Mode overrideMode = Config.INSTANCE.packOverrides.get(id);
            if (overrideMode == DependencyInfo.Mode.FORCED) {
                cir.setReturnValue(true);
            }
        }
    }
}
