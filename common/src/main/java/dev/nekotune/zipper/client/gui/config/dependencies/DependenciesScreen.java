package dev.nekotune.zipper.client.gui.config.dependencies;

import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import dev.nekotune.zipper.Config;
import dev.nekotune.zipper.Resources;
import dev.nekotune.zipper.client.gui.config.AbstractConfigScreen;
import dev.nekotune.zipper.client.gui.widgets.container.SettingsList;
import dev.nekotune.zipper.definition.DependencyInfo;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.SpriteIconButton;
import net.minecraft.client.gui.components.StringWidget;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.layouts.LayoutElement;
import net.minecraft.client.gui.layouts.SpacerElement;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.server.packs.PackType;

/**
 * Screen which shows a list of active dependencies.
 * Dependencies in the list may be edited with a button, opening
 * {@link DependencyEditScreen}.
 * Dependencies in the list may be removed with a button.
 * New dependencies may be added to the list with a button.
 * Dependencies may be re-ordered with arrow buttons.
 */
public class DependenciesScreen extends AbstractConfigScreen {
    
    protected static final String KEY = AbstractConfigScreen.KEY + ".dependencies";

    private static final Map<PackType, Component> TITLES = new EnumMap<>(Map.of(
            PackType.CLIENT_RESOURCES, Component
                    .translatableWithFallback(KEY + "client-resources", "Resource Packs")
                    .withStyle(ChatFormatting.GREEN, ChatFormatting.BOLD),
            PackType.SERVER_DATA, Component
                    .translatableWithFallback(KEY + "server-data", "Data Packs")
                    .withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD)));

    private final Map<String, DependencyInfo> originals;
    private final LinkedHashMap<String, DependencyInfo> modifying;
    private final PackType packType;

    public DependenciesScreen(final Screen lastScreen, final PackType packType) {
        super(TITLES.get(packType), lastScreen);
        this.originals = new HashMap<>();
        Config.INSTANCE.dependencies.values().stream()
                .filter(d -> d.type() == packType)
                .toList()
                .forEach(d -> this.originals.put(d.fileName(), d));
        this.modifying = new LinkedHashMap<>(this.originals);
        this.packType = packType;
    }

    /**
     * Injects all modified dependencies into the configuration.
     * 
     * @param modified Ordered list of modified dependencies.
     */
    protected void apply(final LinkedHashMap<String, DependencyInfo> modified) {
        this.originals.keySet().forEach(fileName -> {
            Config.INSTANCE.dependencies.remove(fileName);
        });
        modified.forEach((final String fileName, final DependencyInfo d) -> {
            Config.INSTANCE.dependencies.put(fileName, d);
        });
    }

    /**
     * Opens the editor screen for the given dependency.
     * 
     * @param dependency The dependency to edit.
     */
    private final void editDependency(final DependencyInfo dependency) {
        final DependencyEditScreen.OnApply injectModified = (final DependencyInfo original,
                final DependencyInfo modified) -> {
            this.modifying.remove(original.fileName());
            this.modifying.put(modified.fileName(), modified);
            this.refresh();
        };
        this.minecraft.setScreen(new DependencyEditScreen(this, dependency, injectModified));
    }

    @Override
    protected void populateSettings(final SettingsList.SettingsContent.Builder builder) {
        final var sorted = this.modifying.values().stream()
                .sorted(Comparator.comparingInt((final DependencyInfo d) -> d.loadPriority())
                        .reversed())
                .toList();
        for (final DependencyInfo dependency : sorted) {
            final List<LayoutElement> infoWidgets = new LinkedList<>();

            // Display the dependency's title
            int titleInfoWidth = this.getInnerWidth() - 4;
            final var titleInfo = new StringWidget(Component.literal(dependency.fileName()), font);
            infoWidgets.add(titleInfo);

            // Button which modifies the dependency's information
            final Resources.Sprite editIcon = Resources.Gui.Sprites.Icons.EDIT;
            final Button editButton = SpriteIconButton.builder(Component.empty(),
                    (final Button button) -> editDependency(dependency),
                    true)
                    .size(Button.DEFAULT_HEIGHT, Button.DEFAULT_HEIGHT)
                    .sprite(editIcon.location(), editIcon.width(), editIcon.height())
                    .build();
            editButton.setTooltip(Tooltip.create(
                    Component.translatableWithFallback(KEY + ".edit.tooltip",
                            "Edit dependency")));
            titleInfoWidth -= editButton.getWidth() + 4;

            // Button which deletes the dependency from the list
            final Resources.Sprite deleteIcon = Resources.Gui.Sprites.Icons.DELETE;
            final Button deleteButton = SpriteIconButton.builder(
                    Component.empty(),
                    (final Button button) -> {

                        // On click, re-build the scroll list with this dependency removed.
                        this.modifying.remove(dependency.fileName());
                        this.refresh();
                    },
                    true)
                    .size(Button.DEFAULT_HEIGHT, Button.DEFAULT_HEIGHT)
                    .sprite(deleteIcon.location(), deleteIcon.width(), deleteIcon.height())
                    .build();
            deleteButton.setTooltip(Tooltip.create(
                    Component.translatableWithFallback(KEY + ".delete.tooltip",
                            "Delete dependency").withStyle(ChatFormatting.RED)));
            titleInfoWidth -= deleteButton.getWidth() + 4;

            // Arrange the edit & delete buttons
            final List<LayoutElement> buttons = new LinkedList<>();
            buttons.add(editButton);
            buttons.add(SpacerElement.width(4));
            buttons.add(deleteButton);

            // Adjust the title's width to fit
            titleInfo.setWidth(Math.min(titleInfo.getWidth(), titleInfoWidth));

            // Commit the line with information on the left and buttons on the right.
            builder.addLine(infoWidgets, buttons,
                    Component.translatableWithFallback(KEY + ".item.narration", "Dependency"));
        }

        // Button to add a new dependency with default values to the list
        builder.addElement(Button.builder(
                Component.literal("+").withStyle(ChatFormatting.BOLD),
                (final Button button) -> {
                    final DependencyInfo dependency = DependencyInfo.createDefault(this.packType);
                    this.modifying.put(dependency.fileName(), dependency);
                    this.refresh();
                    editDependency(dependency); // Automatically opens the editor for it
                }).size(Button.DEFAULT_HEIGHT * 2, Button.DEFAULT_HEIGHT)
                .build(), Component.translatableWithFallback(KEY + ".add.narration", "Add new dependency"));
    }

    @Override
    public void onClose() {
        this.apply(this.modifying);
        super.onClose();
    }

    @Override
    public boolean shouldCloseOnEsc() {
        return true;
    }

    @Override
    public Component getBackButtonMessage() {
        return Component.translatableWithFallback(KEY + ".button.back", "Apply");
    }
}
