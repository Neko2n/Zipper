package dev.nekotune.zipper.client.gui.widgets.container;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import dev.nekotune.zipper.Resources;
import dev.nekotune.zipper.Resources.Gui.Sprites.Widgets;
import dev.nekotune.zipper.Zipper;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractContainerWidget;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.StringWidget;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.layouts.LinearLayout;
import net.minecraft.client.gui.narration.NarratedElementType;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.network.chat.Component;

/**
 * Widget which contains a Layout of child widgets within a clickable dropdown.
 */
public class DropdownContainer extends AbstractContainerWidget {

    public static final int MARGIN_LINE_COLOR = 0xFFFFFFFF;
    
    private final DropdownContainer.OnClick onClick;
    protected final StringWidget headerText;
    private final LinearLayout layout = LinearLayout.vertical();
    private List<GuiEventListener> children = new ArrayList<>();
    protected Font font;
    private int headerHeight;
    private int padding;
    private int contentMargin = 24;
    private boolean collapsed = true;

    public DropdownContainer(final int x, final int y, final int width, final int height,
            final int padding, final Component header, final Font font,
            final DropdownContainer.OnClick onClick) {
        super(x, y, width, height, header);
        this.headerHeight = height;
        this.padding = padding;
        this.font = font;
        this.onClick = onClick;
        this.headerText = new StringWidget(header, font);
    }

    @Override
    public List<? extends GuiEventListener> children() {
        return this.children;
    }

    /**
     * Sets the padding between the dropdown's listed children.
     * @param padding The padding, in pixels
     */
    public void setPadding(final int padding) {
        this.padding = padding;
    }

    private void updateLayout() {
        this.layout.setPosition(this.getX() + this.getContentMargin(), this.getY() + this.getHeaderHeight());
        this.layout.arrangeElements();
        this.setHeight(this.getHeaderHeight() + (this.isCollapsed() ? 0 : this.layout.getHeight()));
    }

    private void collapseChild(final AbstractWidget child) {
        final boolean isActive = !this.isCollapsed();
        child.active = isActive;
        child.visible = isActive;
    }

    /**
     * Adds a widget to this dropdown.
     */
    public void addChild(final AbstractWidget child) {
        this.children.add(child);
        this.layout.addChild(child, settings -> settings.paddingVertical(this.padding/2));
        this.collapseChild(child);
        this.updateLayout();
    }

    @Override
    public void visitWidgets(final Consumer<AbstractWidget> consumer) {
        super.visitWidgets(consumer);
        this.layout.visitWidgets(consumer);
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

    /**
     * Sets the height of the dropdown's header.
     */
    public void setHeaderHeight(final int headerHeight) {
        this.headerHeight = headerHeight;
        this.updateLayout();
    }

    /**
     * @return The height of the dropdown's header.
     */
    public int getHeaderHeight() {
        return this.headerHeight;
    }

    /**
     * @return True if the dropdown is collapsed and its contents should be hidden,
     *         false otherwise.
     */
    public boolean isCollapsed() {
        return this.collapsed;
    }

    /**
     * Sets the collapsed state of this dropdown.
     * @param collapsed True if the dropdown should hide its internal content.
     */
    public void setCollapsed(final boolean collapsed) {
        this.collapsed = collapsed;
        this.layout.visitWidgets(this::collapseChild);
        this.updateLayout();
    }

    /**
     * @return The margin at which to render content, in local space.
     */
    public int getContentMargin() {
        return this.contentMargin;
    }

    /**
     * Sets the margin at which to render the inner content.
     * @param contentMargin The margin offset in pixels from this widget's X-position.
     */
    public void setContentMargin(final int contentMargin) {
        this.contentMargin = contentMargin;
        this.updateLayout();
    }

    /**
     * @return The available width inside of the dropdown.
     */
    public int getInnerWidth() {
        return this.getWidth() - this.contentMargin;
    }

    public boolean isPointInHeader(final double x, final double y) {
        return x >= this.getX() && y >= this.getY() && x < this.getX() + this.getWidth() && y < this.getY() + this.getHeaderHeight();
    }

    /**
     * Renders the inner contents contained within this dropdown.
     */
    protected void renderContent(final GuiGraphics guiGraphics, final int mouseX, final int mouseY,
            final float partialTick) {
        this.layout.visitWidgets(widget -> widget.render(guiGraphics, mouseX, mouseY, partialTick));
    }

    /**
     * Renders the margin line.
     */
    protected void renderDecorations(final GuiGraphics guiGraphics) {
        final int contentHeight = this.layout.getHeight();
        if (contentHeight <= 0)
            return;
        final int marginLineX = this.getX() + ((this.getContentMargin() - 4) / 2) - 1;
        final int marginLineY = this.getY() + this.getHeaderHeight();
        guiGraphics.vLine(marginLineX, marginLineY, marginLineY + contentHeight,
                MARGIN_LINE_COLOR);
    }

    @Override
    public void setTooltip(final Tooltip tooltip) {
        this.headerText.setTooltip(tooltip);
    }

    @Override
    public void setMessage(final Component message) {
        super.setMessage(message);
        this.headerText.setMessage(message);
    }

    @Override
    protected void renderWidget(final GuiGraphics guiGraphics, final int mouseX, final int mouseY,
            final float partialTick) {

        // If not collapsed, render contents and margin line
        if (!this.isCollapsed()) {
            this.renderContent(guiGraphics, mouseX, mouseY, partialTick);
            this.renderDecorations(guiGraphics);
        }

        // Render header
        final int centerY = this.getY() + this.getHeaderHeight() / 2;
        final Resources.Sprite arrowSprite = Widgets.Dropdown.get(
                !this.isCollapsed(), this.isPointInHeader(mouseX, mouseY));
        guiGraphics.blitSprite(arrowSprite.location(),
                this.getX(), centerY - arrowSprite.height() / 2,
                arrowSprite.width(), arrowSprite.height());
        this.headerText.setPosition(this.getX() + this.getContentMargin(),
                centerY - this.headerText.getHeight() / 2);
        this.headerText.render(guiGraphics, mouseX, mouseY, partialTick);
    }

    @Override
    protected void updateWidgetNarration(final NarrationElementOutput narrationElementOutput) {
        narrationElementOutput.add(NarratedElementType.TITLE, this.getMessage());
    }

    // Restrict onClick fire requirements to be clicking the header.
    @Override
    protected boolean clicked(final double mouseX, final double mouseY) {
        return this.isPointInHeader(mouseX, mouseY) ? super.clicked(mouseX, mouseY) : false;
    }

    // Toggle collapsed state when clicked.
    @Override
    public void onClick(final double mouseX, final double mouseY) {
        super.onClick(mouseX, mouseY);
        Zipper.LOG.debug("onClick fired");
        this.setCollapsed(!this.isCollapsed());
        this.onClick.accept(this.isCollapsed());
    }
    
    // AbstractContainerWidget discards click functionality, so add it back
    @Override
    public boolean mouseClicked(final double mouseX, final double mouseY, final int button) {
        if (this.active && this.visible && this.isValidClickButton(button)
                && this.clicked(mouseX, mouseY)) {
            this.playDownSound(Minecraft.getInstance().getSoundManager());
            this.onClick(mouseX, mouseY);
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @FunctionalInterface
    public static interface OnClick extends Consumer<Boolean> {

        /**
         * @param collapsedIs The current collapsed state of the dropdown after the
         *                    click.
         */
        @Override
        abstract void accept(final Boolean collapsedIs);
    }
}