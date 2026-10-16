package dev.nekotune.zipper.client.gui;

import dev.nekotune.zipper.Resources;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.SpriteIconButton;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * Temporary screen with modifiable dimensions and a close button.
 */
public abstract class AbstractPopupScreen extends Screen {
    public final Screen screenBelow;
    protected final Button closeButton;
    protected int popupWidth;
    protected int popupHeight;

    protected AbstractPopupScreen(final Screen covering, final int width, final int height, final Component title) {
        super(title);
        this.screenBelow = covering;
        this.popupWidth = 0;
        this.popupHeight = 0;

        final Resources.Sprite closeIcon = Resources.Gui.Sprites.Icons.CLOSE;
        final Button.OnPress onPressClose = $ -> this.onClose();
        this.closeButton = SpriteIconButton.builder(Component.literal("close"), onPressClose, true)
                .size(closeIcon.width(), closeIcon.height())
                .sprite(closeIcon.location(), closeIcon.width(), closeIcon.height())
                .build();
        
        this.setPopupWidth(width);
        this.setPopupHeight(height);
    }

    private void updateCloseButtonPosition() {
        final int x = (this.marginX() + this.getPopupWidth()) - this.closeButton.getWidth();
        final int y = this.marginY();
        this.closeButton.setPosition(x, y);
    }

    public int bgColor() {
        return 0xFF000000;
    }

    public int getPopupWidth() {
        return this.popupWidth;
    }

    public int getPopupHeight() {
        return this.popupHeight;
    }

    public void setPopupWidth(final int popupWidth) {
        this.popupWidth = popupWidth;
        this.updateCloseButtonPosition();
    }

    public void setPopupHeight(final int popupHeight) {
        this.popupHeight = popupHeight;
        this.updateCloseButtonPosition();
    }

    public final int marginX() {
        return (this.width - this.popupWidth) / 2;
    }

    public final int marginY() {
        return (this.height - this.popupHeight) / 2;
    }

    protected boolean showCloseButton() {
        return true;
    }

    @Override 
    public void init() {
        super.init();
        this.updateCloseButtonPosition();
        if (this.showCloseButton()) {
            this.addRenderableWidget(this.closeButton);
        }
    }

    @Override
    public void render(final GuiGraphics guiGraphics, final int mouseX, final int mouseY,
            final float partialTick) {
        this.screenBelow.width = this.width;
        this.screenBelow.height = this.height;
        super.render(guiGraphics, mouseX, mouseY, partialTick);
    }

    @Override
    public void renderBackground(final GuiGraphics guiGraphics, final int mouseX, final int mouseY,
            final float partialTick) {
        super.renderBackground(guiGraphics, mouseX, mouseY, partialTick);
        this.renderScreenBelow(guiGraphics, mouseX, mouseY, partialTick);
        guiGraphics.fill(this.marginX(), this.marginY(),
                this.marginX() + this.getPopupWidth(), this.marginY() + this.getPopupHeight(),
                this.bgColor());
        this.renderOutline(guiGraphics, mouseX, mouseY, partialTick);
    }

    public void renderScreenBelow(final GuiGraphics guiGraphics, final int mouseX, final int mouseY,
            final float partialTick) {
        this.screenBelow.render(guiGraphics, -1, -1, partialTick);
        guiGraphics.flush();
        this.minecraft.gameRenderer.processBlurEffect(partialTick);
        this.minecraft.getMainRenderTarget().bindWrite(true);
    }

    public void renderOutline(final GuiGraphics guiGraphics, final int mouseX, final int mouseY,
            final float partialTick) {
        guiGraphics.renderOutline(this.marginX(), this.marginY(),
                this.getPopupWidth(), this.getPopupHeight(), 0xFFFFFFFF);
    }

    @Override
    public boolean shouldCloseOnEsc() {
        return true;
    }

    @Override
    public void onClose() {
        this.minecraft.setScreen(this.screenBelow);
    }
}
