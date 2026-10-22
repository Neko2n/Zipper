package dev.nekotune.zipper.client.gui.widgets.input;

import java.util.function.Consumer;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.layouts.LinearLayout;
import net.minecraft.network.chat.Component;

/**
 * Input which accepts N integers where N >= 1.
 */
public final class VectorInput extends LinearLayout {
    private int[] value;
    private final Consumer<int[]> onValueChanged;
    private final int size;

    public VectorInput(final Font font, final int[] defaultValue, final Consumer<int[]> onValueChanged) {
        super(0, 0, LinearLayout.Orientation.HORIZONTAL);
        this.value = defaultValue;
        this.onValueChanged = onValueChanged;
        this.size = defaultValue.length;
        for (int i = 0; i < this.size; i++) {
            final var editBox = new EditBox(font, 40, 20, Component.empty());
            editBox.setFilter(text -> (text.isEmpty() || text.matches("-?\\d*"))
                    && text.length() <= 4);
            editBox.setValue(String.valueOf(this.value[i]));
            final int i$immutable = i;
            editBox.setResponder(text -> {
                try {
                    this.value[i$immutable] = Integer.parseInt(text);
                    this.onValueChanged.accept(this.value);
                } catch (final NumberFormatException e) {
                }
            });
            this.addChild(editBox);
        }
        this.arrangeElements();
    }
}