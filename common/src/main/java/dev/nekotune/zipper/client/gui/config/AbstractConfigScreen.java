package dev.nekotune.zipper.client.gui.config;

import dev.nekotune.zipper.Config;
import dev.nekotune.zipper.Resources;
import dev.nekotune.zipper.client.gui.widgets.container.SettingsList;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * General-purpose screen for rendering configurable settings.
 * Renders a top bar with a title, a scrolling list of settings widgets,
 * and a bottom bar with a button.
 */
public abstract class AbstractConfigScreen extends Screen {

    public static final String KEY = Resources.Lang.Gui.Screen.KEY + ".config";
    protected static final int MIN_INNER_PADDING = 80;
    protected static final int MAX_INNER_WIDTH = 400;
    private static final int BAR_BG_COLOR = 0x65000000;

    private final SettingsList settingsList;
    public final Screen lastScreen;
    public final Button backButton;

    protected AbstractConfigScreen(final Component title, final Screen lastScreen) {
        super(title);
        this.lastScreen = lastScreen;
        this.backButton = Button.builder(Component.empty(), $ -> this.onPressBack())
                .size(Button.DEFAULT_WIDTH, Button.DEFAULT_HEIGHT)
                .build();
        this.settingsList = new SettingsList(0, 0, 0, 0);
    }

    /**
     * Populates the settings list with content provided to the supplied builder.
     * 
     * @param builder The builder to submit settings content to.
     */
    protected abstract void populateSettings(final SettingsList.SettingsContent.Builder builder);

    /**
     * @return The {@link Button#getMessage()} result for {@link AbstractConfigScreen#backButton}
     */
    public abstract Component getBackButtonMessage();

    /**
     * Re-builds the settings list, re-running
     * {@link AbstractConfigScreen#populateSettings}.
     */
    private final void rebuildSettings() {
        final int listWidth = this.getInnerWidth();
        this.settingsList.setPosition(this.getInnerPadding(), this.barHeight());
        this.settingsList.setWidth(listWidth);
        this.settingsList.setHeight(this.height - this.barHeight() * 2);
        final int listContentWidth = listWidth - this.settingsList.totalInnerPadding();
        final var listBuilder = new SettingsList.SettingsContent.Builder(listContentWidth, this.font);
        populateSettings(listBuilder);
        this.settingsList.setContent(listBuilder.build());
    }
    
    /**
     * Calls {@link AbstractConfigScreen#rebuildSettings()}.
     */
    public final void refresh() {
        this.rebuildSettings();
    }

    @Override
    public void render(final GuiGraphics guiGraphics, final int mouseX, final int mouseY,
            final float partialTick) {
        super.render(guiGraphics, mouseX, mouseY, partialTick);
        settingsList.renderWidget(guiGraphics, mouseX, mouseY, partialTick);
        this.renderBars(guiGraphics, mouseX, mouseY, partialTick);
    }

    /**
     * Renders the bars at the top and bottom of the screen, as well as their
     * contents.
     * By default, this is a top bar containing the title, and a bottom bar
     * containing the bottom button.
     */
    public void renderBars(final GuiGraphics guiGraphics, final int mouseX, final int mouseY,
            final float partialTick) {

        // Draw bar background
        final int barHeight = this.barHeight();
        guiGraphics.fill(0, 0, this.width, barHeight - 1, BAR_BG_COLOR);
        guiGraphics.fill(0, this.height, this.width, this.height - barHeight + 1, BAR_BG_COLOR);
        guiGraphics.hLine(0, this.width, barHeight - 1, 0x95000000);
        guiGraphics.hLine(0, this.width, barHeight, 0x55FFFFFF);
        guiGraphics.hLine(0, this.width, this.height - barHeight, 0x95000000);
        guiGraphics.hLine(0, this.width, this.height - barHeight - 1, 0x55FFFFFF);

        // Draw bar contents
        this.renderTitle(guiGraphics, mouseX, mouseY, partialTick);
        this.renderBottomButton(guiGraphics, mouseX, mouseY, partialTick);
    }

    /**
     * Renders the title text at the top of the screen.
     */
    public void renderTitle(final GuiGraphics guiGraphics, final int mouseX, final int mouseY,
            final float partialTick) {
        guiGraphics.drawCenteredString(this.font, this.title,
                this.width / 2, this.barHeight() / 2, 0xFFFFFFFF);
    }

    /**
     * Renders the button at the bottom of the screen.
     * By default, this is a button which closes the screen and saves the config.
     */
    public void renderBottomButton(final GuiGraphics guiGraphics, final int mouseX, final int mouseY,
            final float partialTick) {
        backButton.setMessage(getBackButtonMessage());
        backButton.setPosition(this.width / 2 - (backButton.getWidth() / 2),
                this.height - this.barHeight() / 2 - (backButton.getHeight() / 2));
        backButton.render(guiGraphics, mouseX, mouseY, partialTick);
    }

    /**
     * @return The height of the top and bottom bars.
     */
    protected int barHeight() {
        return this.height / 6;
    }

    /**
     * @return The width of the inner scroll list.
     */
    protected int getInnerWidth() {
        return Math.min(this.width - MIN_INNER_PADDING * 2, MAX_INNER_WIDTH);
    }

    /**
     * @return The number of pixels between the left side of the screen and the left side of the scroll list.
     */
    protected final int getInnerPadding() {
        return (this.width - this.getInnerWidth()) / 2;
    }

    /**
     * @return The height of the inner scroll list.
     */
    protected int getInnerHeight() {
        return this.settingsList.getInnerHeight();
    }

    /**
     * Fires when the back button is pressed.
     */
    protected void onPressBack() {
        this.onClose();
    }

    @Override
    protected void init() {
        super.init();
        this.rebuildSettings();
        this.addWidget(this.settingsList);
        this.addWidget(this.backButton);
    }

    /**
     * @return True if the screen should call {@link Config#save} upon closing,
     *         false otherwise.
     */
    protected boolean shouldSaveOnClose() {
        return !(this.lastScreen instanceof AbstractConfigScreen);
    }

    @Override
    public void onClose() {
        if (this.shouldSaveOnClose()) {
            Config.INSTANCE.save();
        }
        this.minecraft.setScreen(lastScreen);
    }
}
