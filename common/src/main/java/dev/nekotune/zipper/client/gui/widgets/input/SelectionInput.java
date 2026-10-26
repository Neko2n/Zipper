package dev.nekotune.zipper.client.gui.widgets.input;

import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.Map;

import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.network.chat.Component;

public class SelectionInput<T extends Enum<T>> extends Button {

    private final LinkedList<T> values;
    private int index = 0;
    private Map<T, Tooltip> tooltips = new HashMap<>();
    private boolean autoTooltips = false;

    public SelectionInput(final int x, final int y, final int width, final int height,
            final LinkedHashSet<T> values, final T defaultValue, final Button.OnPress onPress) {
        super(x, y, width, height, Component.empty(), onPress, Button.DEFAULT_NARRATION);
        this.values = new LinkedList<>(values);
        this.setValue(defaultValue);
        this.setMessage(this.getMessage());
    }

    public T getValue() {
        return this.values.get(this.index);
    }

    public void setValue(final T value) {
        this.index = this.values.indexOf(value);
    }

    public void assignTooltip(final T value, final Tooltip tooltip) {
        tooltips.put(value, tooltip);
        autoTooltips = true;
        if (value == this.getValue()) {
            this.setTooltip(tooltip);
        }
    }

    @Override
    public void onPress() {
        super.onPress();
        this.index = (this.index + 1) % this.values.size();
        this.setMessage(this.getMessage());
        if (autoTooltips)
            this.setTooltip(this.tooltips.get(this.getValue()));
    }

    @Override
    public Component getMessage() {
        return Component.literal(this.getValue().name()
                .replace('_', ' '))
                .withStyle(ChatFormatting.BOLD);
    }
}
