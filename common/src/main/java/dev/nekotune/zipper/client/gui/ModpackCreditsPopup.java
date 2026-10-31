package dev.nekotune.zipper.client.gui;

import java.util.LinkedList;

import dev.nekotune.zipper.Zipper;
import dev.nekotune.zipper.client.gui.widgets.PackWidget;
import dev.nekotune.zipper.client.gui.widgets.container.ScrollContainer;
import dev.nekotune.zipper.mixin.minecraft.client.TransferableSelectionList.PackEntryMixin;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.navigation.ScreenAxis;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.server.packs.repository.Pack;

/**
 * An ordered list of FORCED packs which are making up the modpack's resources.
 * @see PackEntryMixin
 */
public class ModpackCreditsPopup extends AbstractPopupScreen<Screen> {

    public static final int WIDTH = 260;
    public static final Component TITLE = Component.translatable(Zipper.MOD_ID + ".modpack-resources.credits.title");

    protected final ScrollContainer scrollContainer;
    protected final LinkedList<Pack> packs;

    public ModpackCreditsPopup(final Screen covering, final LinkedList<Pack> packs) {
        super(covering, WIDTH, 0, TITLE);
        this.scrollContainer = new ScrollContainer(0, 0, 0, 0);
        this.packs = packs;
    }

    public int getPaddingX() {
        return 20;
    }

    public int getPaddingBottom() {
        return 20;
    }

    public int getTitleHeight() {
        return 40;
    }

    @Override 
    public int getPopupHeight() {
        return Math.min(this.height - 40, 400);
    }

    @Override 
    public void init() {
        super.init();
        final ScreenRectangle rect = this.getPopupRectangle();

        // Add packs list
        this.scrollContainer.setPosition(rect.left() + this.getPaddingX(),
                rect.top() + this.getTitleHeight());
        this.scrollContainer.setSize(rect.width() - this.getPaddingX()*2,
                rect.height() - this.getTitleHeight() - this.getPaddingBottom());
        this.addWidget(this.scrollContainer);
        
        // Populate packs list
        for (final Pack pack : packs) {
            final PackWidget<Pack> widget = new PackWidget<>(this.minecraft, 0, 0,
                    this.scrollContainer.getWidth() - 4, pack);
            this.scrollContainer.addChild(widget, widget.getNarration(),
                    settings -> settings.paddingBottom(8).paddingTop(8));
        }
    }

    @Override
    public void renderContent(final GuiGraphics guiGraphics, final int mouseX, final int mouseY,
            final float partialTick) {

        // Popup title
        final ScreenRectangle rect = this.getPopupRectangle();
        guiGraphics.drawCenteredString(this.font, this.getTitle(),
                rect.getCenterInAxis(ScreenAxis.HORIZONTAL), rect.top() + this.getTitleHeight()/2,
                0xFFFFFFFF);
        
        // Pack list
        guiGraphics.hLine(rect.left() + this.getPaddingX(), rect.right() - this.getPaddingX(),
                rect.top() + this.getTitleHeight(), 0xFFFFFFFF);
        guiGraphics.hLine(rect.left() + this.getPaddingX(), rect.right() - this.getPaddingX(),
                rect.bottom() - this.getPaddingBottom(), 0xFFFFFFFF);
        this.scrollContainer.render(guiGraphics, mouseX, mouseY, partialTick);
    }

}
