package dev.nekotune.zipper.client.gui.config.dependencies;

import java.util.EnumMap;
import java.util.HashSet;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.Function;

import dev.nekotune.zipper.Resources;
import dev.nekotune.zipper.client.gui.config.AbstractConfigScreen;
import dev.nekotune.zipper.client.gui.config.widgets.container.DropdownContainer;
import dev.nekotune.zipper.client.gui.config.widgets.container.SettingsList;
import dev.nekotune.zipper.client.gui.config.widgets.input.OrderedListInput;
import dev.nekotune.zipper.client.gui.config.widgets.input.SelectionInput;
import dev.nekotune.zipper.client.gui.config.widgets.input.ToggleInput;
import dev.nekotune.zipper.definition.DependencyInfo;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.layouts.LinearLayout;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;

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
        final String hostsKey = KEY + ".hosts";
        final LinearLayout hostsLayout = LinearLayout.horizontal().spacing(4);
        this.settingsWidgets.hosts.values().forEach(hostsLayout::addChild);

        builder.addLabeled(KEY + ".file-name", this.settingsWidgets.fileName());
        builder.addLabeled(KEY + ".mode", this.settingsWidgets.mode());
        builder.addLabeled(KEY + ".load-priority", this.settingsWidgets.loadPriority());
        builder.addLabeled(hostsKey, hostsLayout);
        
        final boolean isDownloaded = this.settingsWidgets.hosts.values().stream()
                .anyMatch(toggle -> toggle.getValue());
        if (isDownloaded) {
            builder.addLabeled(KEY + ".slug", this.settingsWidgets.slug());
            builder.addElement(this.settingsWidgets.mirrors().dropdown());
        }
    }

    @Override
    protected void init() {
        this.settingsWidgets = SettingsWidgets.init(this);
        super.init(); // Calls populateSettings
    }

    @Override
    protected void onPressBack() {
        this.backButton.active = false;
        int loadPriority = 0;
        try {
            loadPriority = Integer.valueOf(this.settingsWidgets.loadPriority().getValue());
        } catch (final NumberFormatException e) {
        }
        final Set<DependencyInfo.Host> hosts = new HashSet<>(this.settingsWidgets.hosts().keySet()
                .stream()
                .filter(host -> this.settingsWidgets.hosts().get(host).getValue())
                .toList());
        final var modified = new DependencyInfo(
                this.editing.type(),
                this.settingsWidgets.fileName().getValue(),
                this.settingsWidgets.slug().getValue(),
                this.settingsWidgets.mirrors().input().getValues().stream()
                        .filter(str -> !str.isBlank())
                        .distinct()
                        .toList(),
                hosts,
                this.settingsWidgets.mode().getValue(),
                loadPriority);
        this.onApply.accept(this.editing, modified);
        this.editing = modified;
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

    /**
     * Widgets which make up the modifiable settings of a dependency.
     * 
     * @param slug         The string input for the dependency's slug.
     * @param mirrors      A modifiable list of string inputs for alternate slugs.
     * @param hosts        Toggles for the host(s) the dependency should try to
     *                     download from.
     * @param mode         A selection button for the mode the dependency loads
     *                     with.
     * @param loadPriority The integer input for the dependency's load priority.
     */
    protected static record SettingsWidgets(
            EditBox fileName,
            EditBox slug,
            MirrorsWidgets mirrors,
            EnumMap<DependencyInfo.Host, ToggleInput.IconToggle> hosts,
            SelectionInput<DependencyInfo.Mode> mode,
            EditBox loadPriority) {

        /**
         * Creates new SettingsWidgets to load into the given editScreen.
         * 
         * @param editScreen The screen to create the widgets for.
         * @return The newly created SettingsWidgets.
         */
        public static SettingsWidgets init(final DependencyEditScreen editScreen) {

            // File name setting
            final var fileNameEditBox = new EditBox(editScreen.font, Button.DEFAULT_WIDTH,
                    Button.DEFAULT_HEIGHT,
                    Component.literal(editScreen.editing.fileName()));
            fileNameEditBox.setValue(editScreen.editing.fileName());
            fileNameEditBox.setResponder(text -> editScreen.backButton.active = true);

            // Hosts setting
            final EnumMap<DependencyInfo.Host, ToggleInput.IconToggle> hostToggles = new EnumMap<>(
                    DependencyInfo.Host.class);
            final String hostsKey = KEY + ".hosts";
            final LinearLayout hostsLayout = LinearLayout.horizontal();
            hostsLayout.spacing(4);
            final String tooltipKey = hostsKey + ".%s.tooltip";
            for (final DependencyInfo.Host host : DependencyInfo.Host.values()) {

                // Button tooltip
                final Function<Boolean, Tooltip> tooltip = state -> {
                    final String hostTooltipKey = tooltipKey.formatted(host.name()
                            .toLowerCase().replace('_', '-'));
                    return Tooltip.create(Component.empty()
                            .append(Component
                                    .translatableWithFallback(hostTooltipKey,
                                            host.displayName)
                                    .setStyle(Style.EMPTY
                                            .withColor(host.displayColor)))
                            .append(Component.literal("\n"))
                            .append(Component.literal(state ? "ENABLED" : "DISABLED")
                                    .setStyle(Style.EMPTY
                                            .withBold(true)
                                            .withColor(state ? ChatFormatting.GREEN
                                                    : ChatFormatting.RED))));
                };

                // Icon button toggle input
                final boolean enabled = editScreen.editing.hosts().contains(host);
                final Resources.HostIcons hostIcons = Resources.HostIcons.of(host);
                final var toggleInput = new ToggleInput.IconToggle(hostIcons.buttonOn(),
                        hostIcons.buttonOff(), enabled);
                toggleInput.setTooltip(tooltip.apply(enabled));
                toggleInput.setResponder(newValue -> {
                    editScreen.backButton.active = true;
                    toggleInput.setTooltip(tooltip.apply(newValue));
                    if (editScreen.settingsWidgets != null)
                            editScreen.refresh();
                });
                hostToggles.put(host, toggleInput);
                hostsLayout.addChild(toggleInput);
            }

            // Primary slug setting
            final var slugEditBox = new EditBox(editScreen.font, Button.DEFAULT_WIDTH,
                    Button.DEFAULT_HEIGHT,
                    Component.literal(editScreen.editing.slug()));
            slugEditBox.setValue(editScreen.editing.slug());
            slugEditBox.setFilter(DependencyInfo.SLUG_VALIDATOR);
            slugEditBox.setResponder(text -> editScreen.backButton.active = true);

            // Mirror slugs setting
            final String mirrorsKey = KEY + ".mirrors";
            final Component mirrorsHeader = Component.translatable(mirrorsKey);
            final Tooltip mirrorsTooltip = Tooltip.create(
                    Component.translatable(mirrorsKey + ".tooltip"));
            final var mirrorsDropdown = new DropdownContainer(0, 0,
                    editScreen.getInnerWidth() - 8, Button.DEFAULT_HEIGHT,
                    mirrorsHeader, editScreen.font,
                    isCollapsed -> editScreen.refresh());
            mirrorsDropdown.setTooltip(mirrorsTooltip);
            final var mirrorsList = new OrderedListInput(0, 0,
                    mirrorsDropdown.getInnerWidth(), editScreen.font,
                    $ -> {
                        if (editScreen.settingsWidgets != null)
                            editScreen.refresh();
                    });
            mirrorsList.setValues(editScreen.editing.mirrors());
            mirrorsList.setResponder(values -> editScreen.backButton.active = true);
            mirrorsList.setFilter(DependencyInfo.SLUG_VALIDATOR);
            mirrorsDropdown.addChild(mirrorsList);

            // Mode setting
            final var modeInput = new SelectionInput<DependencyInfo.Mode>(0, 0,
                    Button.SMALL_WIDTH, Button.DEFAULT_HEIGHT,
                    Set.of(DependencyInfo.Mode.values()), editScreen.editing.mode(),
                    $ -> editScreen.backButton.active = true);
            for (final DependencyInfo.Mode mode : DependencyInfo.Mode.values()) {

                // Assign informational tooltips to each mode option
                final String modeKey = mode.name().toLowerCase().replace('_', '-');
                modeInput.assignTooltip(mode, Tooltip.create(
                        Component.translatable(KEY + ".mode.tooltip." + modeKey)));
                modeInput.setTooltip(modeInput.getTooltip()); // Update existing tooltip
            }

            // Load priority setting
            final var loadPriorityEditBox = new EditBox(editScreen.font, Button.SMALL_WIDTH,
                    Button.DEFAULT_HEIGHT,
                    Component.literal(String.valueOf(editScreen.editing.loadPriority())));
            loadPriorityEditBox.setValue(String.valueOf(editScreen.editing.loadPriority()));
            loadPriorityEditBox.setFilter(text -> text.isEmpty()
                    || (text.matches("^\\d+$") && text.length() <= 3));
            loadPriorityEditBox.setResponder(text -> editScreen.backButton.active = true);

            final var mirrorsWidgets = new MirrorsWidgets(mirrorsDropdown, mirrorsList);
            return new SettingsWidgets(fileNameEditBox, slugEditBox, mirrorsWidgets, hostToggles, modeInput,
                    loadPriorityEditBox);
        }

        public static record MirrorsWidgets(DropdownContainer dropdown, OrderedListInput input) {
        }
    }

    @FunctionalInterface
    public static interface OnApply extends BiConsumer<DependencyInfo, DependencyInfo> {

        @Override
        void accept(final DependencyInfo original, final DependencyInfo modified);
    }

    @Override
    public Component getBackButtonMessage() {
        return Component.translatableWithFallback(KEY + ".button.back", "Apply");
    }
}
