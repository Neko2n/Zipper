package dev.nekotune.zipper.client.gui.widgets;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.network.chat.Component;

/**
 * Widget which shows information about a resource/data pack.
 * Shows up/down arrows on hover which can be given custom OnClick callbacks.
 */
public class ZipperPackWidget extends AbstractWidget {

    public ZipperPackWidget(final int x, final int y,
            final int width, final int height, final Component message) {
        super(x, y, width, height, message);
    }

    @Override
    protected void renderWidget(final GuiGraphics guiGraphics,
            final int mouseX, final int mouseY, final float partialTick) {
        throw new UnsupportedOperationException("Unimplemented method 'renderWidget'");
    }

    @Override
    protected void updateWidgetNarration(final NarrationElementOutput narrationElementOutput) {
        throw new UnsupportedOperationException("Unimplemented method 'updateWidgetNarration'");
    }
}
