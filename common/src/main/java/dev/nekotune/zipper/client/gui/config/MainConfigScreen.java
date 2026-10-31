package dev.nekotune.zipper.client.gui.config;

import java.util.function.Function;

import dev.nekotune.zipper.Config;
import dev.nekotune.zipper.Zipper;
import dev.nekotune.zipper.client.gui.config.dependencies.DependenciesScreen;
import dev.nekotune.zipper.client.gui.widgets.container.SettingsList;
import dev.nekotune.zipper.client.gui.widgets.input.ToggleInput;
import dev.nekotune.zipper.client.gui.widgets.input.VectorInput;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.layouts.LayoutElement;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.server.packs.PackType;

public class MainConfigScreen extends AbstractConfigScreen<Screen> {

    protected static final String KEY = AbstractConfigScreen.KEY + ".main";
    protected static final Component TITLE = Component
                .translatableWithFallback(KEY + ".title", Zipper.MOD_NAME)
                .withStyle(ChatFormatting.BOLD);

    public MainConfigScreen(final Screen lastScreen) {
        super(TITLE, lastScreen);
    }

    @Override
    public boolean shouldCloseOnEsc() {
        return false;
    }

    @Override
    protected void populateSettings(final SettingsList.SettingsContent.Builder builder) {
        builder.addButton(KEY + ".button.client-resources", (final Button button) -> {
            this.minecraft.setScreen(new DependenciesScreen(this, PackType.CLIENT_RESOURCES));
        });
        builder.addButton(KEY + ".button.server-data", (final Button button) -> {
            this.minecraft.setScreen(new DependenciesScreen(this, PackType.SERVER_DATA));
        });
        MainSettings.PRODUCTION.appendTo(builder, this.font);
        MainSettings.WARN_ENABLED.appendTo(builder, this.font);
        MainSettings.PROMPT_ENABLED.appendTo(builder, this.font);
        MainSettings.DISABLE_COMPATIBILITY_WARNINGS.appendTo(builder, this.font);
        MainSettings.BUTTON_OFFSET.appendTo(builder, this.font);
    }

    public static enum MainSettings {
        PRODUCTION(font -> {
            final var toggle = new ToggleInput.TextToggle(Config.INSTANCE.production);
            toggle.setResponder(value -> Config.INSTANCE.production = value);
            return toggle;
        }),
        WARN_ENABLED(font -> {
            final var toggle = new ToggleInput.TextToggle(Config.INSTANCE.warnEnabled);
            toggle.setResponder(value -> Config.INSTANCE.warnEnabled = value);
            return toggle;
        }),
        PROMPT_ENABLED(font -> {
            final var toggle = new ToggleInput.TextToggle(Config.INSTANCE.promptEnabled);
            toggle.setResponder(value -> Config.INSTANCE.promptEnabled = value);
            return toggle;
        }),
        DISABLE_COMPATIBILITY_WARNINGS(font -> {
            final var toggle = new ToggleInput.TextToggle(Config.INSTANCE.disableCompatibilityWarnings);
            toggle.setResponder(value -> Config.INSTANCE.disableCompatibilityWarnings = value);
            return toggle;
        }),
        BUTTON_OFFSET(font -> new VectorInput(font,
                new int[] { Config.INSTANCE.buttonOffset.x, Config.INSTANCE.buttonOffset.y },
                value -> {
                    Config.INSTANCE.buttonOffset.x = value[0];
                    Config.INSTANCE.buttonOffset.y = value[1];
                }));
        
        public final Function<Font, LayoutElement> inputElement;
        public final String translationKey = KEY + "."
                + this.name().toLowerCase().replace('_', '-');
        
        private MainSettings(final Function<Font, LayoutElement> inputElement) {
            this.inputElement = inputElement;
        }

        public SettingsList.SettingsContent.Builder appendTo(
                final SettingsList.SettingsContent.Builder builder, final Font font) {
            return builder.addLabeled(this.translationKey, this.inputElement.apply(font));
        }
    }

    @Override
    public Component getBackButtonMessage() {
        return Component.translatableWithFallback(KEY + ".button.back", "Save & Exit");
    }
}
