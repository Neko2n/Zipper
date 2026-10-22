package dev.nekotune.zipper.client.gui.widgets.container;

import java.util.ArrayList;
import java.util.List;

import com.mojang.blaze3d.systems.RenderSystem;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractContainerWidget;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.layouts.LayoutElement;
import net.minecraft.client.gui.layouts.LinearLayout;
import net.minecraft.client.gui.narration.NarratedElementType;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

// TODO fix input being passed to child widgets when outside of the bounds of the container
/**
 * Widget which renders a scrolling list of child widgets.
 */
public class ScrollContainer extends AbstractContainerWidget {

    public static final ResourceLocation SCROLLER_SPRITE = ResourceLocation.withDefaultNamespace("widget/scroller");
    public static final int INNER_PADDING = 4;
    public static final int SCROLL_BAR_WIDTH = 8;

    private double scrollAmount;
    private boolean isScrolling;
    private LinearLayout layout = LinearLayout.vertical();
    private final List<GuiEventListener> children = new ArrayList<>();

    public ScrollContainer(final int x, final int y, final int width, final int height) {
        super(x, y, width, height, Component.empty());
    }

    protected final void clearChildren() {
        this.layout = LinearLayout.vertical();
        this.children.clear();
    }

    protected final void updateLayout() {
        final int baseX = this.getX() + this.innerPadding();
        final int scrolledY = this.getY() + this.innerPadding() - ((int) this.scrollAmount());
        this.layout.setX(baseX);
        this.layout.setY(scrolledY);
        this.layout.arrangeElements();
    }

    @Override
    public final List<? extends GuiEventListener> children() {
        return this.children;
    }

    public final void addChild(final LayoutElement child, final Component narration) {
        child.visitWidgets(this.children::add);
        this.layout.addChild(child);
        this.setMessage(this.getMessage().copy().append(narration));
        updateLayout();
    }

    // Match children positions to parent
    @Override
    public void setX(final int x) {
        super.setX(x);
        this.updateLayout();
    }

    // Match children positions to parent
    @Override
    public void setY(final int y) {
        super.setY(y);
        this.updateLayout();
    }

    protected int innerPadding() {
        return INNER_PADDING;
    }

    public int scrollbarWidth() {
        return SCROLL_BAR_WIDTH;
    }

    public int getInnerHeight() {
        return this.layout.getHeight();
    }

    protected double scrollRate() {
        return 9.0d;
    }

    @Override
    public void renderWidget(final GuiGraphics guiGraphics, final int mouseX, final int mouseY,
            final float partialTick) {
        if (!this.visible)
            return;
        this.renderBackground(guiGraphics);
        guiGraphics.enableScissor(this.getX() + 1, this.getY() + 1,
                this.getX() + this.getWidth() - 1, this.getY() + this.getHeight() - 1);
        this.renderContents(guiGraphics, mouseX, mouseY, partialTick);
        guiGraphics.disableScissor();
        this.renderDecorations(guiGraphics);
    }

    protected void renderBackground(final GuiGraphics guiGraphics) {
    }

    protected final void renderContents(final GuiGraphics guiGraphics, final int mouseX, final int mouseY,
            final float partialTick) {
        this.layout.visitWidgets(widget -> widget.render(guiGraphics, mouseX, mouseY, partialTick));
    }

    protected void renderDecorations(final GuiGraphics guiGraphics) {
        if (this.scrollbarVisible()) {
            this.renderScrollBar(guiGraphics);
        }
    }

    private void renderScrollBar(final GuiGraphics guiGraphics) {
        int i = this.getScrollBarHeight();
        int j = this.getX() + this.width;
        int k = Math.max(this.getY(),
                (int) this.scrollAmount * (this.height - i) / this.getMaxScrollAmount() + this.getY());
        RenderSystem.enableBlend();
        guiGraphics.blitSprite(SCROLLER_SPRITE, j, k, this.scrollbarWidth(), i);
        RenderSystem.disableBlend();
    }

    public boolean mouseClicked(final double mouseX, final double mouseY, final int button) {
        if (!this.visible) {
            return false;
        } else {
            final boolean onScrollbar = this.scrollbarVisible()
                    && !this.withinContentAreaPoint(mouseX, mouseY)
                    && this.withinContentAreaPoint(mouseX - this.scrollbarWidth(), mouseY);
            if (onScrollbar && button == 0) {
                this.isScrolling = true;
                return true;
            }
            final boolean consumed = super.mouseClicked(mouseX, mouseY, button);
            return consumed;
        }
    }

    public boolean mouseReleased(final double mouseX, final double mouseY, final int button) {
        if (button == 0) {
            this.isScrolling = false;
        }

        return super.mouseReleased(mouseX, mouseY, button);
    }

    public boolean mouseDragged(final double mouseX, final double mouseY, final int button,
            final double dragX, final double dragY) {
        if (this.visible && this.isScrolling) {
            if (mouseY < (double) this.getY()) {
                this.setScrollAmount((double) 0.0F);
            } else if (mouseY > (double) (this.getY() + this.height)) {
                this.setScrollAmount((double) this.getMaxScrollAmount());
            } else {
                int i = this.getScrollBarHeight();
                double d = (double) Math.max(1, this.getMaxScrollAmount() / (this.height - i));
                this.setScrollAmount(this.scrollAmount + dragY * d);
            }

            return true;
        } else {
            return false;
        }
    }

    public boolean mouseScrolled(final double mouseX, final double mouseY,
            final double scrollX, final double scrollY) {
        if (!this.visible) {
            return false;
        }
        this.setScrollAmount(this.scrollAmount - scrollY * this.scrollRate());
        return true;
    }

    public boolean keyPressed(final int keyCode, final int scanCode, final int modifiers) {
        boolean bl = keyCode == 265;
        boolean bl2 = keyCode == 264;
        if (bl || bl2) {
            double d = this.scrollAmount;
            this.setScrollAmount(this.scrollAmount + (double) (bl ? -1 : 1) * this.scrollRate());
            if (d != this.scrollAmount) {
                return true;
            }
        }

        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    private int getScrollBarHeight() {
        return Mth.clamp((int) ((float) (this.height * this.height) / (float) this.getContentHeight()), 32,
                this.height);
    }

    public int totalInnerPadding() {
        return this.innerPadding() * 2;
    }

    protected double scrollAmount() {
        return this.scrollAmount;
    }

    protected void setScrollAmount(final double scrollAmount) {
        this.scrollAmount = Mth.clamp(scrollAmount, (double) 0.0F, (double) this.getMaxScrollAmount());
        updateLayout();
    }

    protected int getMaxScrollAmount() {
        return Math.max(0, this.getContentHeight() - (this.height - this.innerPadding()));
    }

    private int getContentHeight() {
        return this.getInnerHeight() + this.innerPadding();
    }

    protected boolean withinContentAreaTopBottom(final int top, final int bottom) {
        return (double) bottom - this.scrollAmount >= (double) this.getY()
                && (double) top - this.scrollAmount <= (double) (this.getY() + this.height);
    }

    protected boolean withinContentAreaPoint(final double x, final double y) {
        return x >= (double) this.getX() && x < (double) (this.getX() + this.width) && y >= (double) this.getY()
                && y < (double) (this.getY() + this.height);
    }

    protected boolean scrollbarVisible() {
        return this.getInnerHeight() > this.getHeight();
    }

    @Override
    protected void updateWidgetNarration(final NarrationElementOutput narrationOutput) {
        narrationOutput.add(NarratedElementType.TITLE, this.getMessage());
    }
}
