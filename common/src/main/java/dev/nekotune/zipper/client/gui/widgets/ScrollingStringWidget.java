package dev.nekotune.zipper.client.gui.widgets;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.components.StringWidget;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

/**
 * Version of {@link StringWidget} with scrolling text.
 */
public class ScrollingStringWidget extends StringWidget {

    public ScrollingStringWidget(final Component message, final Font font) {
        super(message, font);
    }
    
    /**
     * Derived from {@link AbstractButton}
     */
    @Override
    public void renderWidget(final GuiGraphics graphics, final int mouseX, final int mouseY,
            final float partialTick) {
        final int color = this.getColor() | Mth.ceil(this.alpha * 255.0F) << 24;
        final int minX = this.getX();
        final int maxX = minX + this.getWidth();
        renderScrollingString(graphics, this.getFont(), this.getMessage(),
                minX, minX, this.getY(), maxX, this.getY() + this.getHeight(), color);
    }
}
