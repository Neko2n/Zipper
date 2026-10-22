package dev.nekotune.zipper.client.gui.config.dependencies;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

import dev.nekotune.zipper.Resources;
import dev.nekotune.zipper.client.gui.AbstractPopupScreen;
import dev.nekotune.zipper.client.gui.config.AbstractConfigScreen;
import dev.nekotune.zipper.client.gui.widgets.container.DropdownContainer;
import dev.nekotune.zipper.client.gui.widgets.container.SettingsList;
import dev.nekotune.zipper.client.gui.widgets.input.SelectionInput;
import dev.nekotune.zipper.definition.DependencyInfo;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractContainerWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.ImageWidget;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.narration.NarratedElementType;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

/**
 * Pop-up screen to edit a dependency.
 */
public class DependencyEditScreen extends AbstractConfigScreen {

    protected static final String KEY = DependenciesScreen.KEY + ".edit";
    private static final Component TITLE = Component.translatableWithFallback(KEY + ".title", "Edit Dependency")
            .withStyle(ChatFormatting.BOLD);
    private static final int BG_COLOR = 0xBB000000;

    private final DependencyEditScreen.OnApply onApply;
    private DependencyInfo editing;
    private final Component subtitle;
    private SettingsWidgets settingsWidgets;

    protected DependencyEditScreen(final Screen below,
            final DependencyInfo dependency, final OnApply onApply) {
        super(TITLE, below);
        final MutableComponent subtitle = Component.empty();
        switch (dependency.type()) {
            case CLIENT_RESOURCES:
                subtitle.append(Component
                        .translatableWithFallback(KEY + ".subtitle.client-resources",
                                "Resource Pack")
                        .withStyle(ChatFormatting.GREEN));
                break;
            case SERVER_DATA:
                subtitle.append(
                        Component.translatableWithFallback(KEY + ".subtitle.server-data",
                                "Data Pack")
                                .withStyle(ChatFormatting.GOLD));
                break;
        }
        this.subtitle = subtitle;
        this.onApply = onApply;
        this.editing = dependency;
    }

    @Override
    protected void populateSettings(final SettingsList.SettingsContent.Builder builder) {
        builder.addLabeled(KEY + ".file-name", this.settingsWidgets.fileName());
        builder.addLabeled(KEY + ".mode", this.settingsWidgets.mode());
        builder.addElement(this.settingsWidgets.hosts().dropdown());
    }

    @Override
    protected void init() {
        this.settingsWidgets = SettingsWidgets.init(this);
        super.init(); // Calls populateSettings
    }

    @Override
    protected void onPressBack() {
        this.backButton.active = false;
        final var modified = new DependencyInfo(
                this.editing.type(),
                this.settingsWidgets.fileName().getValue(),
                this.settingsWidgets.mode().getValue(),
                this.editing.loadPriority(),
                this.settingsWidgets.hosts().toMap());
        this.onApply.accept(this.editing, modified);
        this.editing = modified;
    }

    @Override
    public void render(final GuiGraphics guiGraphics, final int mouseX, final int mouseY,
            final float partialTick) {
        this.lastScreen.width = this.width;
        this.lastScreen.height = this.height;
        super.render(guiGraphics, mouseX, mouseY, partialTick);
    }

    // Render the subtitle along with the main title
    @Override
    public void renderTitle(final GuiGraphics guiGraphics, final int mouseX, final int mouseY,
            final float partialTick) {
        guiGraphics.drawCenteredString(this.font, this.title,
                this.width / 2, this.barHeight() / 2 - 5, 0xFFFFFFFF);
        guiGraphics.drawCenteredString(this.font, this.subtitle,
                this.width / 2, this.barHeight() / 2 + 5, 0xFFFFFFFF);
    }

    // Render a blurred version of the screen below this one
    @Override
    public void renderBackground(final GuiGraphics guiGraphics, final int mouseX, final int mouseY,
            final float partialTick) {
        super.renderBackground(guiGraphics, mouseX, mouseY, partialTick);
        this.lastScreen.render(guiGraphics, -1, -1, partialTick);
        guiGraphics.flush();
        this.minecraft.gameRenderer.processBlurEffect(partialTick);
        this.minecraft.getMainRenderTarget().bindWrite(true);
        guiGraphics.fill(0, 0, this.width, this.height, BG_COLOR);
    }

    @Override
    public boolean shouldCloseOnEsc() {
        return true;
    }

    @Override
    public Component getBackButtonMessage() {
        return Component.translatableWithFallback(KEY + ".button.back", "Apply");
    }

    /**
     * Widgets which make up the modifiable settings of a dependency.
     * 
     * @param fileName The string input for the dependency's file name.
     * @param mode     A selection button for the mode the dependency loads
     *                 with.
     * @param hosts    String inputs for the host API slug(s) the dependency should
     *                 try to download from.
     */
    protected static record SettingsWidgets(
            EditBox fileName,
            SelectionInput<DependencyInfo.Mode> mode,
            HostsSettings hosts) {

        /**
         * Creates new SettingsWidgets to load into the given editScreen.
         * 
         * @param editScreen The screen to create the widgets for.
         * @return The newly created SettingsWidgets.
         */
        public static SettingsWidgets init(final DependencyEditScreen editScreen) {
            return new SettingsWidgets(
                    initFileName(editScreen),
                    initModeInput(editScreen),
                    initHostsSettings(editScreen));
        }

        // Input text box for the pack's file name
        private static EditBox initFileName(final DependencyEditScreen editScreen) {
            final var fileNameEditBox = new EditBox(editScreen.font, Button.DEFAULT_WIDTH,
                    Button.DEFAULT_HEIGHT,
                    Component.literal(editScreen.editing.fileName()));
            fileNameEditBox.setValue(editScreen.editing.fileName());
            fileNameEditBox.setResponder(text -> editScreen.backButton.active = true);
            return fileNameEditBox;
        }

        // Selection button for how the pack should be loaded into the game
        private static SelectionInput<DependencyInfo.Mode> initModeInput(final DependencyEditScreen editScreen) {
            final var modeInput = new SelectionInput<DependencyInfo.Mode>(0, 0,
                    Button.SMALL_WIDTH, Button.DEFAULT_HEIGHT,
                    Set.of(DependencyInfo.Mode.values()), editScreen.editing.mode(),
                    $ -> editScreen.backButton.active = true);
            for (final DependencyInfo.Mode mode : DependencyInfo.Mode.values()) {

                // Assign informational tooltips to each mode option
                final String modeKey = mode.name().toLowerCase().replace('_', '-');
                modeInput.assignTooltip(mode, Tooltip.create(
                        Component.translatable(KEY + ".mode.tooltip." + modeKey)));
                modeInput.setTooltip(modeInput.getTooltip()); // Update existing Tooltip
            }

            return modeInput;
        }

        // Input text boxes for the slugs to use for fallback downloading of the pack
        // from web host APIs
        private static HostsSettings initHostsSettings(final DependencyEditScreen editScreen) {
            final String hostsKey = KEY + ".hosts";
            final Component hostsHeader = Component.translatable(hostsKey);
            final Tooltip hostsTooltip = Tooltip.create(
                    Component.translatable(hostsKey + ".tooltip"));

            // Dropdown container which holds the input text boxes for all of the web host
            // APIs
            final var hostsDropdown = new DropdownContainer(0, 0,
                    editScreen.getInnerWidth() - 8, Button.DEFAULT_HEIGHT, 4,
                    hostsHeader, editScreen.font,
                    isCollapsed -> editScreen.refresh());
            hostsDropdown.setTooltip(hostsTooltip);

            // Build slug input text boxes for each web host API
            final EnumMap<DependencyInfo.Host, HostsSettings.HostWidget> hostsWidgets = new EnumMap<>(
                    DependencyInfo.Host.class);
            for (final DependencyInfo.Host host : DependencyInfo.Host.values()) {
                final HostsSettings.HostWidget hostWidget = new HostsSettings.HostWidget(
                        host, 0, 0, hostsDropdown.getInnerWidth(), editScreen.font,
                        $ -> editScreen.minecraft.setScreen(new HelpButtonPopupScreen(editScreen, host)));
                hostWidget.setResponder(values -> editScreen.backButton.active = true);
                hostWidget.slugInput.setValue(editScreen.editing.downloadSlugs()
                        .getOrDefault(host, ""));
                hostsWidgets.put(host, hostWidget);
                hostsDropdown.addChild(hostWidget);
            }

            return new HostsSettings(hostsDropdown, hostsWidgets);
        }

        public static record HostsSettings(DropdownContainer dropdown,
                EnumMap<DependencyInfo.Host, HostWidget> widgets) {

            public EnumMap<DependencyInfo.Host, String> toMap() {
                final EnumMap<DependencyInfo.Host, String> map = new EnumMap<>(DependencyInfo.Host.class);
                for (final DependencyInfo.Host host : DependencyInfo.Host.values()) {
                    final HostWidget widget = this.widgets.get(host);
                    map.put(host, widget.slugInput.getValue());
                }
                return map;
            }

            public static class HostWidget extends AbstractContainerWidget {

                public final DependencyInfo.Host host;
                public final EditBox slugInput;
                public final Button helpButton;
                protected ImageWidget hostIcon;
                private Consumer<String> responder = $ -> {
                };

                private final List<GuiEventListener> children = new ArrayList<>();

                public HostWidget(final DependencyInfo.Host host, final int x, final int y, final int width,
                        final Font font, final Button.OnPress onPressHelp) {
                    super(x, y, width, Button.DEFAULT_HEIGHT, Component.empty());
                    this.host = host;

                    // Input box for the slug to download from
                    this.slugInput = new EditBox(font, 0, Button.DEFAULT_HEIGHT, Component.literal("project-slug"));
                    this.slugInput.setFilter(DependencyInfo.SLUG_VALIDATOR);
                    this.slugInput.setResponder(value -> {
                        this.responder.accept(value);
                        this.refresh();
                    });
                    this.children.add(this.slugInput);

                    // Logo of the host website
                    final Resources.Sprite logo = this.getHostIcon();
                    this.setHostIcon(logo);
                    this.children.add(this.hostIcon);

                    // Button that shows information about download hosts and where to find slugs
                    this.helpButton = Button.builder(Component.literal("?"), onPressHelp)
                            .size(Button.DEFAULT_HEIGHT, Button.DEFAULT_HEIGHT)
                            .build();
                    this.children.add(this.helpButton);

                    this.refresh();
                }

                public void setResponder(final Consumer<String> responder) {
                    this.responder = responder;
                }

                private void setHostIcon(final Resources.Sprite logo) {
                    this.children.remove(this.hostIcon);
                    this.hostIcon = ImageWidget.sprite(logo.width(), logo.height(), logo.location());
                    final Tooltip hostTooltip = Tooltip.create(Component.literal(host.displayName));
                    this.hostIcon.setTooltip(hostTooltip);
                    this.children.add(this.hostIcon);
                }

                protected Resources.Sprite getHostIcon() {
                    final Resources.HostIcons icons = Resources.HostIcons.of(this.host);
                    final boolean isEnabled = !this.slugInput.getValue().isBlank();
                    return isEnabled ? icons.buttonOn() : icons.buttonOff();
                }

                protected void refresh() {
                    final Resources.Sprite logo = this.getHostIcon();
                    this.setHostIcon(logo);
                    this.hostIcon.setPosition(this.getX(), this.getY());
                    final int slugMargin = logo.width() + 4;
                    final int slugX = this.getX() + slugMargin;
                    this.slugInput.setPosition(slugX, this.getY());
                    final int slugWidth = this.getWidth() - slugMargin - Button.DEFAULT_HEIGHT - 4;
                    this.slugInput.setWidth(slugWidth);
                    this.helpButton.setPosition(slugX + slugWidth + 4, this.getY());
                }

                @Override
                public void setHeight(final int height) {
                    super.setHeight(height);
                    this.refresh();
                }

                @Override
                public void setWidth(final int width) {
                    super.setWidth(width);
                    this.refresh();
                }

                @Override
                public void setX(final int x) {
                    super.setX(x);
                    this.refresh();
                }

                @Override
                public void setY(final int y) {
                    super.setY(y);
                    this.refresh();
                }

                @Override
                public List<? extends GuiEventListener> children() {
                    return this.children;
                }

                @Override
                protected void renderWidget(final GuiGraphics guiGraphics, final int mouseX, final int mouseY,
                        final float partialTick) {
                    this.hostIcon.render(guiGraphics, mouseX, mouseY, partialTick);
                    this.slugInput.render(guiGraphics, mouseX, mouseY, partialTick);
                    this.helpButton.render(guiGraphics, mouseX, mouseY, partialTick);
                }

                @Override
                protected void updateWidgetNarration(final NarrationElementOutput narrationElementOutput) {
                    narrationElementOutput.add(NarratedElementType.TITLE, this.getMessage());
                }
            }
        }
    }

    /**
     * Screen that shows when you click the "?" button next to a host's slug input
     * text box.
     */
    public static final class HelpButtonPopupScreen extends AbstractPopupScreen {

        public final DependencyInfo.Host host;

        protected HelpButtonPopupScreen(final DependencyEditScreen covering, final DependencyInfo.Host host) {
            super(covering, 400, 100, Component.empty());
            this.host = host;
        }

        @Override
        public void renderContent(final GuiGraphics guiGraphics, final int mouseX, final int mouseY,
                final float partialTick) {
            final int centerX = this.marginX() + (this.getPopupWidth() / 2);
            final int centerY = this.marginY() + (this.getPopupHeight() / 2);
            final int textColor = 0xFFFFFFFF;
            final int padding = 20;

            // Localized explanation text
            final Component explanation = Component.translatable(KEY + ".popup.download-help");
            guiGraphics.drawCenteredString(this.font, explanation, centerX, centerY - (padding / 2), textColor);

            // URL example for where to find project slugs
            final Resources.Sprite searchSprite = Resources.Gui.Sprites.Icons.SEARCH;
            final MutableComponent urlExample = Component.empty();
            switch (this.host) {
                case CURSEFORGE:
                    urlExample.append(Component.literal("https://www.curseforge.com/minecraft/texture-packs/")
                            .withStyle(ChatFormatting.ITALIC, ChatFormatting.GRAY));
                    break;
                case MODRINTH:
                    urlExample.append(Component.literal("https://modrinth.com/resourcepack/")
                            .withStyle(ChatFormatting.ITALIC, ChatFormatting.GRAY));
                    break;
            }
            urlExample.append(Component.literal("project-slug")
                    .withStyle(ChatFormatting.BOLD, ChatFormatting.YELLOW));
            final int urlExampleX = centerX + (searchSprite.width() / 2) + 3;
            final int urlExampleY = centerY + (padding / 2);
            guiGraphics.drawCenteredString(this.font, urlExample, urlExampleX, urlExampleY, textColor);
            final int textWidth = this.font.width(urlExample);
            guiGraphics.blitSprite(searchSprite.location(),
                    urlExampleX - (textWidth / 2) - 6 - searchSprite.width(), urlExampleY - (searchSprite.height() / 4),
                    searchSprite.width(), searchSprite.height());
        }
    }

    @FunctionalInterface
    public static interface OnApply extends BiConsumer<DependencyInfo, DependencyInfo> {

        @Override
        void accept(final DependencyInfo original, final DependencyInfo modified);
    }
}
