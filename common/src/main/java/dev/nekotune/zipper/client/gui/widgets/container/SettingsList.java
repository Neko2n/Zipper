package dev.nekotune.zipper.client.gui.widgets.container;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.StringWidget;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.layouts.FrameLayout;
import net.minecraft.client.gui.layouts.Layout;
import net.minecraft.client.gui.layouts.LayoutElement;
import net.minecraft.client.gui.layouts.LinearLayout;
import net.minecraft.client.gui.layouts.SpacerElement;
import net.minecraft.client.gui.narration.NarratedElementType;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

/**
 * Renders a formatted list of settings widgets as a vertical layout.
 */
public class SettingsList extends ScrollContainer {
    
    private SettingsContent content = SettingsContent.EMPTY;

    public SettingsList(final int x, final int y, final int width, final int height) {
        super(x, y, width, height);
    }

    /**
     * Sets this container's content to a new value.
     */
    public void setContent(final SettingsContent content) {
        this.content = content;
        this.clearChildren();
        this.addChild(content.container(), content.narration());
        this.updateLayout();
    }

    public SettingsContent getContent() {
        return this.content;
    }

    @Override
    protected void updateWidgetNarration(final NarrationElementOutput narrationOutput) {
        narrationOutput.add(NarratedElementType.TITLE, this.content.narration());
    }

    /**
     * Data type representing the widget's list object.
     * 
     * @param container The container layout holding the list's contents.
     * @param narration The narration label for the container.
     */
    public record SettingsContent(LinearLayout container, List<AbstractWidget> widgets, Component narration) {

        public static final int SPACING = 16;
        public static final int ELEMENT_HEIGHT = Button.DEFAULT_HEIGHT;

        public static final SettingsContent EMPTY = new SettingsContent(
                LinearLayout.vertical(), List.of(), Component.empty());

        public static class Builder {
            private final Font font;
            private final int width;
            private final LinearLayout container;
            private final List<AbstractWidget> widgets = new ArrayList<>();
            private final MutableComponent narration = Component.empty();

            public Builder(final int width, final Font font) {
                this.font = font;
                this.width = width;
                this.container = LinearLayout.vertical();
                this.container.defaultCellSetting().alignHorizontallyCenter();
                this.container.addChild(SpacerElement.width(this.width));
                this.container.addChild(SpacerElement.height(SPACING / 2));
            }

            public Builder addButton(final String translationKey, final Button.OnPress onPress) {
                final Component label = Component.translatable(translationKey);
                final Button button = Button
                        .builder(label, onPress)
                        .size(width, ELEMENT_HEIGHT)
                        .tooltip(Tooltip.create(Component.translatable(translationKey + ".tooltip")))
                        .build();
                return addElement(button, label);
            }

            public Builder addLabeled(final String translationKey, final LayoutElement element) {
                final Component label = Component.translatable(translationKey);
                final var labelWidget = new StringWidget(label, font);
                labelWidget.setTooltip(Tooltip.create(Component.translatable(translationKey + ".tooltip")));
                return addLine(List.of(labelWidget), List.of(element), label);
            }

            public Builder addLine(final Collection<LayoutElement> left, final Collection<LayoutElement> right,
                    final Component narration) {
                final var holder = new FrameLayout(width, ELEMENT_HEIGHT);

                final LinearLayout leftGroup = LinearLayout.horizontal();
                for (final LayoutElement element : left) {
                    leftGroup.addChild(element, settings -> settings.alignVerticallyMiddle());
                    if (element instanceof final AbstractWidget widget) {
                        this.widgets.add(widget);
                    }
                }
                holder.addChild(leftGroup, settings -> settings.alignHorizontallyLeft().alignVerticallyMiddle());

                final LinearLayout rightGroup = LinearLayout.horizontal();
                for (final LayoutElement element : right) {
                    rightGroup.addChild(element, settings -> settings.alignVerticallyMiddle());
                    if (element instanceof final AbstractWidget widget) {
                        this.widgets.add(widget);
                    }
                }
                holder.addChild(rightGroup, settings -> settings.alignHorizontallyRight().alignVerticallyMiddle());

                this.container.addChild(holder, settings -> settings.paddingBottom(SPACING / 2).paddingTop(SPACING / 2));
                this.narration.append(narration);
                return this;
            }

            public Builder addLine(final LayoutElement left, final LayoutElement right, final Component narration) {
                return this.addLine(List.of(left), List.of(right), narration);
            }

            public Builder addElement(final LayoutElement element, final Component narration) {
                return this.addLine(List.of(element), List.of(), narration);
            }

            public Builder addElement(final AbstractWidget widget) {
                return this.addLine(List.of(widget), List.of(), widget.getMessage());
            }

            public Builder addElements(final Collection<LayoutElement> elements, final Component narration) {
                return this.addLine(elements, List.of(), narration);
            }

            public SettingsContent build() {
                this.container.addChild(SpacerElement.height(SPACING / 2));
                arrangeNested(this.container);
                return new SettingsContent(this.container, this.widgets, this.narration);
            }

            private static void arrangeNested(final Layout layout) {
                layout.arrangeElements();
                layout.visitChildren((final LayoutElement child) -> {
                    if (child instanceof final Layout nested && child != layout) {
                        arrangeNested(nested);
                    }
                });
            }
        }
    }
}
