package dev.nekotune.zipper.client.gui.widgets.input;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Predicate;

import dev.nekotune.zipper.Resources;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractContainerWidget;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.SpriteIconButton;
import net.minecraft.client.gui.layouts.LayoutSettings;
import net.minecraft.client.gui.layouts.LinearLayout;
import net.minecraft.client.gui.layouts.SpacerElement;
import net.minecraft.client.gui.narration.NarratedElementType;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.network.chat.Component;

/**
 * An ordered list of EditBoxes which can add and remove values.
 */
public class OrderedListInput extends AbstractContainerWidget {

    public static final String KEY = Resources.Lang.Gui.Widget.Input.KEY + ".ordered-list";

    public static final Component ADD_TEXT = Component.literal("+")
            .withStyle(ChatFormatting.BOLD);

    private Font font;
    private Predicate<String> inputValidator = $ -> true;
    private Consumer<Collection<String>> responder = $ -> {
    };
    private final LinkedHashMap<EditBox, Entry> entries = new LinkedHashMap<>();
    private final Consumer<OrderedListInput> onArrangeElements;
    private final Button addButton;

    public OrderedListInput(final int x, final int y, final int width,
            final Font font, final Consumer<OrderedListInput> onArrangeElements) {
        super(x, y, width, Button.DEFAULT_HEIGHT, Component.empty());
        this.font = font;
        this.onArrangeElements = onArrangeElements;
        final Button.OnPress addPressed = $ -> {
            if (this.getValues().stream().anyMatch(str -> str.isBlank()))
                return;
            this.pushValue("");
        };
        this.addButton = Button.builder(ADD_TEXT, addPressed)
                .size(Button.DEFAULT_HEIGHT * 2, Button.DEFAULT_HEIGHT)
                .build();
        final LinearLayout layout = LinearLayout.vertical();
        layout.setPosition(x, y);
        layout.addChild(this.addButton);
        layout.arrangeElements();
    }

    /**
     * Sets the filter to apply to values input to the list's EditBoxes.
     * 
     * @param validator The predicate to apply to input values
     * @see EditBox#setFilter(Predicate)
     */
    public final void setFilter(final Predicate<String> validator) {
        this.inputValidator = validator;
        for (final Entry entry : this.entries.values()) {
            entry.editBox.setFilter(validator);
        }
    }

    /**
     * Sets the responding consumer for all EditBoxes in the list.
     * 
     * @param responder The consumer to apply to changes
     * @see EditBox#setResponder(Consumer)
     */
    public final void setResponder(final Consumer<Collection<String>> responder) {
        this.responder = responder;
    }

    /**
     * @return The string values currently input into this list, in order.
     */
    public final List<String> getValues() {
        return this.entries.keySet().stream().map(editBox -> editBox.getValue()).toList();
    }

    /**
     * Sets the values of this list input.
     * 
     * @param values The ordered collection of string values to set
     */
    public final void setValues(final Collection<String> values) {
        this.entries.clear();
        values.forEach(this::pushValue);
    }

    /**
     * Pushes a new value to the list.
     * 
     * @param value The value to push
     */
    public final void pushValue(final String value) {
        final Resources.Sprite deleteIcon = Resources.Gui.Sprites.Icons.DELETE;
        final int editBoxWidth = this.getWidth() - deleteIcon.width() - 4;
        final EditBox editBox = new EditBox(this.font, editBoxWidth, Button.DEFAULT_HEIGHT,
                Component.empty());
        editBox.setFilter(this.inputValidator);
        if (this.inputValidator.test(value))
            editBox.setValue(value);
        editBox.setResponder((final String text) -> {
            this.responder.accept(getValues());
        });
        final SpriteIconButton deleteButton = SpriteIconButton.builder(Component.empty(),
                $ -> {
                    this.entries.remove(editBox);
                    this.arrangeElements();
                }, true)
                .size(Button.DEFAULT_HEIGHT, Button.DEFAULT_HEIGHT)
                .sprite(deleteIcon.location(), deleteIcon.width(), deleteIcon.height())
                .build();
        final Entry entry = new Entry(editBox, deleteButton);
        this.entries.put(editBox, entry);
        this.arrangeElements();
    }

    /**
     * Pops the last value from the list.
     * 
     * @return The removed value
     */
    public final String popValue() {
        final Entry entry = this.entries.remove(this.entries.lastEntry().getKey());
        this.arrangeElements();
        this.responder.accept(this.getValues());
        return entry.editBox.getValue();
    }

    private void arrangeElements() {
        final LinearLayout layout = LinearLayout.vertical();
        layout.setPosition(this.getX(), this.getY());
        final Consumer<LayoutSettings> padding = settings -> settings.paddingBottom(2).paddingTop(2);
        for (final Entry entry : this.entries.values()) {
            final LinearLayout entryLayout = LinearLayout.horizontal();
            entryLayout.addChild(entry.editBox);
            entryLayout.addChild(SpacerElement.width(4));
            entryLayout.addChild(entry.deleteButton);
            layout.addChild(entryLayout, padding);
        }
        layout.addChild(this.addButton, padding);
        layout.arrangeElements();
        super.setHeight(layout.getHeight());
        this.onArrangeElements.accept(this);
    }

    @Override
    public void setHeight(final int height) {
    }

    @Override
    public void setWidth(final int width) {
        super.setWidth(width);
        this.entries.values().forEach(entry -> {
            entry.editBox.setWidth(width - entry.deleteButton.getWidth() - 4);
        });
        this.arrangeElements();
    }

    @Override
    public void setX(final int x) {
        final int delta = x - this.getX();
        super.setX(x);
        this.children().forEach(child -> child.setX(child.getX() + delta));
    }

    @Override
    public void setY(final int y) {
        final int delta = y - this.getY();
        super.setY(y);
        this.children().forEach(child -> child.setY(child.getY() + delta));
    }

    @Override
    public List<AbstractWidget> children() {
        final List<AbstractWidget> list = new ArrayList<>();
        this.entries.values().stream().forEach(entry -> {
            list.add(entry.editBox);
            list.add(entry.deleteButton);
        });
        list.add(this.addButton);
        return list;
    }

    @Override
    protected void renderWidget(final GuiGraphics guiGraphics, final int mouseX, final int mouseY,
            final float partialTick) {
        this.entries.values().forEach(entry -> {
            entry.editBox.render(guiGraphics, mouseX, mouseY, partialTick);
            entry.deleteButton.render(guiGraphics, mouseX, mouseY, partialTick);
        });
        this.addButton.render(guiGraphics, mouseX, mouseY, partialTick);
    }

    @Override
    protected void updateWidgetNarration(final NarrationElementOutput narrationElementOutput) {
        narrationElementOutput.add(NarratedElementType.TITLE, this.getMessage());
    }

    protected static record Entry(EditBox editBox, Button deleteButton) {
    }
}
