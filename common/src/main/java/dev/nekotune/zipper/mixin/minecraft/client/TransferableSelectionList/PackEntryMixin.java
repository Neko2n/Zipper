package dev.nekotune.zipper.mixin.minecraft.client.TransferableSelectionList;

import java.io.IOException;
import java.util.LinkedList;
import java.util.List;
import java.util.function.Function;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import dev.nekotune.zipper.Config;
import dev.nekotune.zipper.Resources;
import dev.nekotune.zipper.Zipper;
import dev.nekotune.zipper.client.gui.ModpackCreditsPopup;
import dev.nekotune.zipper.definition.DependencyInfo;
import dev.nekotune.zipper.definition.DependencyPack;
import dev.nekotune.zipper.definition.DependencyPack.ModpackResources;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.ObjectSelectionList;
import net.minecraft.client.gui.components.SpriteIconButton;
import net.minecraft.client.gui.screens.packs.PackSelectionModel;
import net.minecraft.client.gui.screens.packs.TransferableSelectionList;
import net.minecraft.client.gui.screens.packs.TransferableSelectionList.PackEntry;
import net.minecraft.network.chat.Component;
import net.minecraft.server.packs.repository.Pack;

// TODO shorten pack text max width to make space for the credits button
/**
 * Adds a credits button to the ModpackResources pack widget, linking to
 * {@link ModpackCreditsPopup}
 */
@Mixin(value = TransferableSelectionList.PackEntry.class)
public abstract class PackEntryMixin extends ObjectSelectionList.Entry<PackEntry> {

    @Shadow
    @Final
    private PackSelectionModel.Entry pack;

    @Shadow
    @Final
    private TransferableSelectionList parent;

    @Unique
    private SpriteIconButton zipper$creditsButton;

    @Unique
    private SpriteIconButton zipper$getCreditsButton() {
        if (this.zipper$creditsButton == null) {
            final Resources.Sprite icon = Resources.Gui.Sprites.Icons.SEARCH; // TODO actual icon
            this.zipper$creditsButton = SpriteIconButton
                    .builder(Component.empty(), $ -> this.zipper$showCreditsScreen(), true)
                    .sprite(icon.location(), icon.width(), icon.height())
                    .size(Button.DEFAULT_HEIGHT, Button.DEFAULT_HEIGHT)
                    .build();
        }
        return this.zipper$creditsButton;
    }

    @Unique
    private void zipper$showCreditsScreen() {
        final Function<DependencyInfo, Pack> mapToPack = info -> {
            try {
                return DependencyPack.from(info);
            } catch (final IOException e) {
                return DependencyPack.from(info, DependencyPack.TEMPLATE_META);
            }
        };
        final List<Pack> dependencies = Config.INSTANCE.dependencies.stream()
                .filter(info -> info.mode() == DependencyInfo.Mode.FORCED)
                .map(mapToPack)
                .toList();
        final LinkedList<Pack> ordered = Zipper.reorderPacks(dependencies);
        final Minecraft minecraft = Minecraft.getInstance();
        minecraft.setScreen(new ModpackCreditsPopup(minecraft.screen, ordered));
    }

    @Inject(method = "render", at = @At("TAIL"))
    private void zipper$render(final GuiGraphics graphics, final int index, final int top, final int left,
            final int width, final int height, final int mouseX, final int mouseY,
            final boolean hovering, final float partialTick, final CallbackInfo ci) {
        if (this.pack.getId() != ModpackResources.ID)
            return;
        final SpriteIconButton button = this.zipper$getCreditsButton();
        button.setPosition(left + width - button.getWidth() - 10, top + (height - button.getHeight()) / 2);
        button.render(graphics, mouseX, mouseY, partialTick);
    }

    @Inject(method = "mouseClicked", at = @At("HEAD"), cancellable = true)
    private void zipper$mouseClicked(final double mouseX, final double mouseY, final int button,
            final CallbackInfoReturnable<Boolean> cir) {
        if (this.pack.getId() == DependencyPack.ModpackResources.ID
                && this.zipper$getCreditsButton().mouseClicked(mouseX, mouseY, button)) {
            cir.setReturnValue(true);
        }
    }
}
