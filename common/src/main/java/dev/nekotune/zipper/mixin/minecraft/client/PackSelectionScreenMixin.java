package dev.nekotune.zipper.mixin.minecraft.client;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import dev.nekotune.zipper.Resources;
import dev.nekotune.zipper.definition.DependencyPack;
import net.minecraft.client.gui.screens.packs.PackSelectionScreen;
import net.minecraft.client.renderer.texture.TextureManager;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.repository.Pack;

/**
 * Set a custom pack icon for the modpack resources packs
 */
@Mixin(PackSelectionScreen.class)
public class PackSelectionScreenMixin {

    @Inject(method = "loadPackIcon", at = @At("HEAD"), cancellable = true)
    private void zipper$loadPackIcon(final TextureManager textureManager, final Pack pack,
            final CallbackInfoReturnable<ResourceLocation> cir) {
        if (pack instanceof DependencyPack.ModpackResources) {
            cir.setReturnValue(Resources.Gui.MODPACK_RESOURCES_ICON);
        }
    }
}
