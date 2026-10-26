package dev.nekotune.zipper.client.gui.config.dependencies;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import dev.nekotune.zipper.Config;
import dev.nekotune.zipper.Resources;
import dev.nekotune.zipper.Zipper;
import dev.nekotune.zipper.client.PackDirectoryWatcher;
import dev.nekotune.zipper.client.gui.AbstractPopupScreen;
import dev.nekotune.zipper.client.gui.config.AbstractConfigScreen;
import dev.nekotune.zipper.client.gui.widgets.PackWidget;
import dev.nekotune.zipper.client.gui.widgets.container.SettingsList;
import dev.nekotune.zipper.definition.DependencyInfo;
import dev.nekotune.zipper.definition.DependencyPack;
import dev.nekotune.zipper.platform.Services;
import net.minecraft.ChatFormatting;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractContainerWidget;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.SpriteIconButton;
import net.minecraft.client.gui.components.StringWidget;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.layouts.FrameLayout;
import net.minecraft.client.gui.layouts.LinearLayout;
import net.minecraft.client.gui.layouts.SpacerElement;
import net.minecraft.client.gui.narration.NarratedElementType;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.packs.PackSelectionScreen;
import net.minecraft.client.gui.screens.packs.TransferableSelectionList;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.PackLocationInfo;
import net.minecraft.server.packs.PackSelectionConfig;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.repository.FolderRepositorySource;
import net.minecraft.server.packs.repository.Pack;
import net.minecraft.server.packs.repository.PackSource;

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

    /** Copied from {@link PackSelectionScreen} */
    private static final int RELOAD_DELAY_TICKS = 20;

    private final Path vanillaPackFolder;
    private final LinkedHashSet<DependencyPack> originalPacks;
    private final LinkedHashSet<DependencyPack> modifiedPacks;
    private final HashMap<Pack, Path> vanillaPacks = new HashMap<>();
    private final PackType packType;
    private int ticksToReload = 0;
    private Optional<PackDirectoryWatcher> vanillaPackWatcher = Optional.empty();
    protected final Button createPackButton;

    public DependenciesScreen(final Screen lastScreen, final PackType packType) {
        super(TITLES.get(packType), lastScreen);
        this.packType = packType;

        // Vanilla pack reader
        final Path gamePath = Services.PLATFORM.get().getGameDirectory();
        this.vanillaPackFolder = switch (packType) {
            case CLIENT_RESOURCES -> gamePath.resolve("resourcepacks");
            case SERVER_DATA -> gamePath.resolve("datapacks");
        };
        this.reloadVanillaPacks();

        // Set up ordered original + modified pack lists
        this.originalPacks = new LinkedHashSet<>(Config.INSTANCE.dependencies.stream()
                .filter(d -> d.type() == packType)
                .map(d -> {
                    try {
                        return DependencyPack.from(d);
                    } catch (final IOException e) {
                        return DependencyPack.from(d, DependencyPack.TEMPLATE_META);
                    }
                })
                .toList());
        this.modifiedPacks = new LinkedHashSet<>(this.originalPacks);

        // Button to create a new zipper pack from scratch
        this.createPackButton = Button.builder(
                Component.literal("+").withStyle(ChatFormatting.BOLD),
                (final Button button) -> editDependency(
                        DependencyPack.from(DependencyInfo.createDefault(this.packType),
                                DependencyPack.TEMPLATE_META)))
                .size(Button.DEFAULT_HEIGHT, Button.DEFAULT_HEIGHT)
                .build();
        final Tooltip createPackTooltip = Tooltip.create(Component.translatable(KEY + ".button.create-new.tooltip"));
        this.createPackButton.setTooltip(createPackTooltip);
    }

    // Reload for changes in the vanilla packs directory
    private void reloadVanillaPacks() {
        this.vanillaPacks.clear();
        try {
            Files.createDirectories(this.vanillaPackFolder);
        } catch (final IOException e) {
            Zipper.LOG.warn("Could not create pack directory \"" + this.vanillaPackFolder + "\"", e);
            return;
        }
        final PackSource source = switch (this.packType) {
            case PackType.CLIENT_RESOURCES -> PackSource.DEFAULT;
            case PackType.SERVER_DATA -> PackSource.WORLD;
        };
        try {
            FolderRepositorySource.discoverPacks(this.vanillaPackFolder,
                    Minecraft.getInstance().directoryValidator(),
                    (final Path path, final Pack.ResourcesSupplier resources) -> {
                        final String name = path.getFileName().toString();
                        final PackLocationInfo location = new PackLocationInfo(name, Component.literal(name), source,
                                Optional.empty());
                        final Pack pack = Pack.readMetaAndCreate(location, resources, this.packType,
                                new PackSelectionConfig(false, Pack.Position.TOP, false));
                        if (pack != null) { // I hate null
                            this.vanillaPacks.put(pack, path);
                        }
                    });
        } catch (final IOException e) {
            Zipper.LOG.warn("Failed to list packs in \"" + this.vanillaPackFolder + "\"", e);
        }
    }

    /**
     * Injects all modified dependencies into the configuration.
     */
    protected void apply() {
        Config.INSTANCE.dependencies.removeAll(this.originalPacks.stream().map(p -> p.info).toList());
        Config.INSTANCE.dependencies.addAll(this.modifiedPacks.stream().map(p -> p.info).toList());
    }

    // Opens the editor screen for the given dependency.
    private final void editDependency(final DependencyPack dependency) {
        final DependencyEditScreen.OnApply injectModified = (final DependencyPack original,
                final DependencyPack modified) -> {
            final var backup = new LinkedHashSet<>(this.modifiedPacks);
            this.modifiedPacks.clear();
            for (final DependencyPack pack : backup) {
                if (pack == original) {
                    this.modifiedPacks.add(modified);
                } else {
                    this.modifiedPacks.add(pack);
                }
            }
            if (!this.modifiedPacks.contains(modified)) {
                this.modifiedPacks.addFirst(modified);
            }
            this.refresh();
        };
        final HashSet<String> takenFileNames = new HashSet<>(this.modifiedPacks.stream()
                .map(p -> p.info.fileName())
                .filter(s -> s != dependency.info.fileName())
                .toList());
        takenFileNames.add(DependencyInfo.createDefault(packType).fileName());
        this.minecraft.setScreen(new DependencyEditScreen(this, dependency, injectModified, takenFileNames));
    }

    @Override
    protected void init() {
        super.init();
        final int x = this.getInnerMargin() + this.getInnerWidth() + SettingsList.SCROLL_BAR_WIDTH + 4;
        final int y = this.height - this.getBarHeight() - this.createPackButton.getHeight() - 4;
        this.createPackButton.setPosition(x, y);
        this.addWidget(this.createPackButton);
        if (this.vanillaPackWatcher.isEmpty()) {
            this.vanillaPackWatcher = PackDirectoryWatcher.create(this.vanillaPackFolder);
        }
    }

    @Override
    public void tick() {
        super.tick();
        this.vanillaPackWatcher.ifPresent(watcher -> {
            try {
                if (watcher.pollForChanges()) {
                    this.ticksToReload = RELOAD_DELAY_TICKS;
                }
            } catch (final IOException e) {
                Zipper.LOG.warn("DependenciesScreen failed to poll for changes in directory \"" + this.vanillaPackFolder
                        + "\", stopping");
                this.closeWatcher();
            }
        });
        this.ticksToReload--;
        if (this.ticksToReload == 0) {
            this.reloadVanillaPacks();
            this.refresh();
        }
    }

    @Override
    public void removed() {
        super.removed();
        this.closeWatcher();
    }

    private void closeWatcher() {
        this.vanillaPackWatcher.ifPresent(watcher -> {
            try {
                watcher.close();
            } catch (final IOException e) {
                Zipper.LOG.warn("Failed to close file watcher for DependenciesScreen", e);
            }
            this.vanillaPackWatcher = Optional.empty();
        });
    }

    @Override
    public void renderBottomButton(final GuiGraphics guiGraphics, final int mouseX, final int mouseY,
            final float partialTick) {
        super.renderBottomButton(guiGraphics, mouseX, mouseY, partialTick);
        this.createPackButton.render(guiGraphics, mouseX, mouseY, partialTick);
    }

    @Override
    protected void populateSettings(final SettingsList.SettingsContent.Builder builder) {

        // Modpack resources listing with help & "open folder" buttons
        try {
            final DependencyPack.ModpackResources pack = DependencyPack.ModpackResources.get(this.packType);
            builder.addElement(new Entry.ModpackResourcesEntry(this, pack));

            // Button which opens the pack folder for the modpack resources pack
            final Button folderButton = Button.builder(
                    /** Component copied from {@link PackSelectionScreen} */
                    Component.translatable("pack.openFolder"),
                    (final Button button) -> Util.getPlatform().openPath(pack.getPath()))
                    .size(Button.SMALL_WIDTH, Button.DEFAULT_HEIGHT)
                    .build();
            /** Component copied from {@link PackSelectionScreen} */
            folderButton.setTooltip(Tooltip.create(Component.translatable("pack.folderInfo")));
            builder.addElement(folderButton);
        } catch (final IOException e) {
            Zipper.LOG.error("Modpack resources could not be read. This should never happen.");
        }

        // List packs separated by mode
        final EnumMap<DependencyInfo.Mode, LinkedList<DependencyPack>> sortedPacks = new EnumMap<>(
                DependencyInfo.Mode.class);
        this.modifiedPacks.forEach((final DependencyPack pack) -> {
            sortedPacks.computeIfAbsent(pack.info.mode(), $ -> new LinkedList<>()).add(pack);
        });
        populateSection(builder, sortedPacks.getOrDefault(DependencyInfo.Mode.FORCED, new LinkedList<>()),
                true, Component.translatable(KEY + ".section.forced.title"));
        populateSection(builder, sortedPacks.getOrDefault(DependencyInfo.Mode.OPTIONAL_ENABLED, new LinkedList<>()),
                true, Component.translatable(KEY + ".section.optional-enabled.title"));
        populateSection(builder, sortedPacks.getOrDefault(DependencyInfo.Mode.OPTIONAL_DISABLED, new LinkedList<>()),
                false, Component.translatable(KEY + ".section.optional-disabled.title"));
        populateSection(builder, sortedPacks.getOrDefault(DependencyInfo.Mode.SUPPORT, new LinkedList<>()),
                false, Component.translatable(KEY + ".section.support.title"));

        // List vanilla packs that can be converted into zipper packs
        final Component sectionTitle = Component.translatable(KEY + ".section.vanilla.title")
                .withStyle(ChatFormatting.GREEN);
        builder.addElement(new StringWidget(sectionTitle, this.font));
        final List<Pack> convertible = this.vanillaPacks.keySet().stream()
                .filter(p -> !this.isConverted(p))
                .sorted(Comparator.comparing(Pack::getId))
                .toList();
        if (!convertible.isEmpty()) {
            for (final Pack pack : convertible) {
                builder.addElement(new Entry.VanillaEntry(this, pack));
            }
        } else {
            builder.addElement(new StringWidget(Component.literal("Nothing here :(")
                    .withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC), this.font));
        }
    }

    private void populateSection(final SettingsList.SettingsContent.Builder builder,
            final LinkedList<DependencyPack> packs, final boolean canMove, final Component title) {
        if (packs.size() == 0)
            return;
        builder.addElement(new StringWidget(title, this.font));
        if (canMove) {
            Optional<Entry<DependencyPack>> above = Optional.empty();
            Optional<Entry<DependencyPack>> below = Optional.empty();
            for (int i = 0; i < packs.size(); i++) {
                final Entry<DependencyPack> entry = below.orElse(new Entry.DependencyEntry(this, packs.get(i)));
                if (i + 1 < packs.size()) {
                    final DependencyPack nextPack = packs.get(i + 1);
                    below = Optional.of(new Entry.DependencyEntry(this, nextPack));
                    entry.below = below;
                }
                entry.above = above;
                above = Optional.of(entry);
                builder.addElement(entry);
            }
        } else {
            for (final DependencyPack pack : packs) {
                builder.addElement(new Entry.DependencyEntry(this, pack));
            }
        }
    }

    private boolean isConverted(final Pack pack) {
        final Path filePath = this.vanillaPacks.get(pack);
        final String fileName = filePath.getFileName().toString()
                .replace(".zip", "");
        final boolean fileExists = Files.exists(DependencyInfo.folder(packType)
                .resolve(fileName + ".zip"));
        final boolean isRegistered = this.modifiedPacks.stream()
                .anyMatch(p -> fileName.equals(p.info.fileName()));
        return fileExists && isRegistered;
    }

    @Override
    public void onClose() {
        this.apply();
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

    protected static sealed abstract class Entry<T extends Pack> extends AbstractContainerWidget {

        /** Copied from {@link TransferableSelectionList} */
        private static final int HOVER_OVERLAY = 0xA0909090;

        public static interface Sprites {
            public static final Resources.Sprite INCREASE_PRIORITY = Resources.Gui.Sprites.Widgets.TransferableSelection.MOVE_UP;
            public static final Resources.Sprite INCREASE_PRIORITY_HIGHLIGHTED = Resources.Gui.Sprites.Widgets.TransferableSelection.MOVE_UP_HIGHLIGHTED;
            public static final Resources.Sprite DECREASE_PRIORITY = Resources.Gui.Sprites.Widgets.TransferableSelection.MOVE_DOWN;
            public static final Resources.Sprite DECREASE_PRIORITY_HIGHLIGHTED = Resources.Gui.Sprites.Widgets.TransferableSelection.MOVE_DOWN_HIGHLIGHTED;
        }

        protected final ZipperPackWidget packWidget;
        protected final PriorityButtons priorityButtons;
        protected final LinkedList<Button> interactionButtons;
        public final DependenciesScreen screen;
        public final T pack;
        public Optional<Entry<DependencyPack>> above = Optional.empty();
        public Optional<Entry<DependencyPack>> below = Optional.empty();

        public Entry(final DependenciesScreen screen, final T pack) {
            super(0, 0, screen.getInnerWidth() - 4, 32, pack.getTitle());
            this.screen = screen;
            this.pack = pack;

            // Display information about the pack (icon, title, description)
            this.packWidget = new ZipperPackWidget(this);

            final var upButton = new TransferButton(
                    Component.translatableWithFallback(KEY + ".move-up.narration", "Increase priority"),
                    Sprites.INCREASE_PRIORITY, Sprites.INCREASE_PRIORITY_HIGHLIGHTED,
                    0, $ -> this.above.ifPresent(this::swapWith));
            final var downButton = new TransferButton(
                    Component.translatableWithFallback(KEY + ".move-down.narration", "Decrease priority"),
                    Sprites.DECREASE_PRIORITY, Sprites.DECREASE_PRIORITY_HIGHLIGHTED,
                    Sprites.DECREASE_PRIORITY.height() / 2, $ -> this.below.ifPresent(this::swapWith));
            this.priorityButtons = new PriorityButtons(upButton, downButton);

            this.interactionButtons = this.constructInteractionButtons();

            this.arrangeElements();
        }

        public abstract LinkedList<Button> constructInteractionButtons();

        public abstract boolean alwaysShowButtons();

        public void arrangeElements() {
            final FrameLayout layout = new FrameLayout();
            layout.setMinWidth(this.getWidth());
            layout.setPosition(this.getX(), this.getY());
            layout.addChild(this.packWidget, settings -> settings.alignHorizontallyLeft().alignVerticallyMiddle());
            final LinearLayout rightGroup = LinearLayout.horizontal();
            rightGroup.addChild(SpacerElement.width(4));
            for (final Button button : this.interactionButtons) {
                rightGroup.addChild(button, settings -> settings.alignVerticallyMiddle());
                rightGroup.addChild(SpacerElement.width(4));
            }
            rightGroup.arrangeElements();
            layout.addChild(rightGroup, settings -> settings.alignHorizontallyRight().alignVerticallyMiddle());
            this.packWidget.setWidth(this.getWidth() - rightGroup.getWidth());
            layout.arrangeElements();
            final int iconX = this.packWidget.getX();
            final int iconY = this.packWidget.getY();
            this.priorityButtons.up().setPosition(
                    iconX + this.priorityButtons.up().sprite.width() / 2,
                    iconY);
            this.priorityButtons.down().setPosition(
                    iconX + this.priorityButtons.down().sprite.width() / 2,
                    iconY + this.priorityButtons.down().sprite.height() / 2);
        }

        /**
         * Swaps this entry with another entry, swapping their loading priorities and
         * refreshing the screen.
         * 
         * @param swapWith The entry to swap loading priorities (positions) with.
         */
        public void swapWith(final Entry<DependencyPack> swapWith) {
            if (!(this.pack instanceof final DependencyPack pack))
                return;
            final var backup = new LinkedHashSet<>(this.screen.modifiedPacks);
            this.screen.modifiedPacks.clear();
            for (final DependencyPack existing : backup) {
                if (existing == this.pack) {
                    this.screen.modifiedPacks.add(swapWith.pack);
                } else if (existing == swapWith.pack) {
                    this.screen.modifiedPacks.add(pack);
                } else {
                    this.screen.modifiedPacks.add(existing);
                }
            }
            this.screen.refresh();
        }

        @Override
        public void setX(int x) {
            super.setX(x);
            this.arrangeElements();
        }

        @Override
        public void setY(int y) {
            super.setY(y);
            this.arrangeElements();
        }

        @Override
        public void setWidth(final int width) {
            super.setWidth(width);
            this.arrangeElements();
        }

        @Override
        public List<? extends GuiEventListener> children() {
            final LinkedList<AbstractWidget> widgets = new LinkedList<>();
            widgets.addAll(this.interactionButtons);
            widgets.add(this.packWidget);
            if (this.above.isPresent()) {
                widgets.addFirst(this.priorityButtons.up());
            }
            if (this.below.isPresent()) {
                widgets.addFirst(this.priorityButtons.down());
            }
            return widgets;
        }

        @Override
        protected final void renderWidget(final GuiGraphics guiGraphics, final int mouseX, final int mouseY,
                final float partialTick) {
            this.packWidget.render(guiGraphics, mouseX, mouseY, partialTick);
            if (this.isHovered() || this.alwaysShowButtons()) {
                for (final Button button : this.interactionButtons) {
                    button.render(guiGraphics, mouseX, mouseY, partialTick);
                }
            }
            if (this.isHovered()) {

                // Render priority shifting arrows over the packWidget icon
                final boolean hasAbove = this.above.isPresent();
                final boolean hasBelow = this.below.isPresent();
                if (hasAbove || hasBelow) {
                    final int iconX = this.packWidget.getX();
                    final int iconY = this.packWidget.getY();
                    final int iconSize = this.packWidget.getIconSize();
                    guiGraphics.fill(iconX, iconY,
                            iconX + iconSize, iconY + iconSize,
                            HOVER_OVERLAY);
                    if (hasAbove) {
                        this.priorityButtons.up().render(guiGraphics, mouseX, mouseY, partialTick);
                    }
                    if (hasBelow) {
                        this.priorityButtons.down().render(guiGraphics, mouseX, mouseY, partialTick);
                    }
                }
            }
        }

        @Override
        protected void updateWidgetNarration(final NarrationElementOutput narrationElementOutput) {
            narrationElementOutput.add(NarratedElementType.TITLE, this.pack.getTitle());
        }

        protected static record PriorityButtons(TransferButton up, TransferButton down) {
        }

        /**
         * Up/down position shifting buttons, copied from
         * {@link TransferableSelectionList}
         */
        protected static class TransferButton extends Button {
            private final Resources.Sprite sprite;
            private final Resources.Sprite highlightedSprite;
            private final int spriteOffsetY;

            protected TransferButton(final Component message, final Resources.Sprite sprite,
                    final Resources.Sprite highlightedSprite, final int spriteOffsetY,
                    final Button.OnPress onPress) {
                super(0, 0, sprite.width() / 2, sprite.height() / 2, message, onPress, Button.DEFAULT_NARRATION);
                this.sprite = sprite;
                this.highlightedSprite = highlightedSprite;
                this.spriteOffsetY = spriteOffsetY;
            }

            @Override
            protected void renderWidget(final GuiGraphics guiGraphics, final int mouseX, final int mouseY,
                    final float partialTick) {
                final Resources.Sprite activeSprite = this.isHovered() ? this.highlightedSprite : this.sprite;
                guiGraphics.blitSprite(activeSprite.location(),
                        this.getX() - sprite.width() / 2, this.getY() - this.spriteOffsetY,
                        sprite.width(), sprite.height());
            }
        }

        /**
         * Custom PackWidget with a simplified description and a custom pack icon for
         * the modpack resources pack.
         */
        public static class ZipperPackWidget extends PackWidget<Pack> {

            public ZipperPackWidget(final Entry<? extends Pack> entry) {
                super(entry.screen.minecraft, 0, 0, 0, entry.pack);
            }

            public ResourceLocation getPackIcon() {
                if (this.pack instanceof DependencyPack.ModpackResources) {
                    return Resources.Gui.MODPACK_RESOURCES_ICON;
                }
                if (this.pack instanceof final DependencyPack dependencyPack
                        && !dependencyPack.info.fileExists()) {
                    return Resources.Gui.DEFAULT_PACK_ICON;
                }
                return super.getPackIcon();
            }

            @Override
            protected Component computeDescription() {
                return this.pack.getDescription();
            }
        }

        public static final class DependencyEntry extends Entry<DependencyPack> {

            public DependencyEntry(final DependenciesScreen screen, final DependencyPack pack) {
                super(screen, pack);
            }

            @Override
            public LinkedList<Button> constructInteractionButtons() {
                final LinkedList<Button> buttons = new LinkedList<>();

                // Button which modifies the dependency's information
                final Resources.Sprite editIcon = Resources.Gui.Sprites.Icons.EDIT;
                final Button editButton = SpriteIconButton.builder(Component.empty(),
                        (final Button button) -> this.screen.editDependency(pack),
                        true)
                        .size(Button.DEFAULT_HEIGHT, Button.DEFAULT_HEIGHT)
                        .sprite(editIcon.location(), editIcon.width(), editIcon.height())
                        .build();
                editButton.setTooltip(Tooltip.create(
                        Component.translatableWithFallback(KEY + ".edit.tooltip",
                                "Edit dependency")));

                // Button which deletes the dependency from the list
                final Resources.Sprite deleteIcon = Resources.Gui.Sprites.Icons.DELETE;
                final Button deleteButton = SpriteIconButton.builder(
                        Component.empty(),
                        (final Button button) -> {
                            this.screen.minecraft.setScreen(new DeleteConfirmationScreen(this.screen, this));
                        },
                        true)
                        .size(Button.DEFAULT_HEIGHT, Button.DEFAULT_HEIGHT)
                        .sprite(deleteIcon.location(), deleteIcon.width(), deleteIcon.height())
                        .build();
                deleteButton.setTooltip(Tooltip.create(
                        Component.translatableWithFallback(KEY + ".delete.tooltip",
                                "Delete dependency").withStyle(ChatFormatting.RED)));

                buttons.add(editButton);
                buttons.add(deleteButton);
                return buttons;
            }

            @Override
            public boolean alwaysShowButtons() {
                return false;
            }

            public static final class DeleteConfirmationScreen extends AbstractPopupScreen<DependenciesScreen> {
                private static final int CONFIRM_CD_TICKS = 30;

                private boolean confirmed = false;
                private final Entry.DependencyEntry entry;
                private int elapsed = 0;
                private final StringWidget questionText;
                private final StringWidget warningText;
                private final Button confirmButton;
                private final Button backButton;
                private final Button.OnPress pressConfirm = $ -> {
                    this.confirmed = true;
                    this.onClose();
                };

                protected DeleteConfirmationScreen(final DependenciesScreen covering,
                        final Entry.DependencyEntry entry) {
                    super(covering, 340, 100 + Button.DEFAULT_HEIGHT, Component.empty());
                    this.entry = entry;
                    this.font = covering.minecraft.font;
                    this.questionText = new StringWidget(Component.translatable(KEY + ".delete.popup.question"),
                            this.font);
                    this.warningText = new StringWidget(Component.translatable(KEY + ".delete.popup.warning"),
                            this.font);
                    this.confirmButton = Button
                            .builder(Component.translatable(KEY + ".delete.popup.button.confirm"), pressConfirm)
                            .size(Button.SMALL_WIDTH, Button.DEFAULT_HEIGHT)
                            .build();
                    this.backButton = Button
                            .builder(Component.translatable(KEY + ".delete.popup.button.back"), $ -> this.onClose())
                            .size(Button.SMALL_WIDTH, Button.DEFAULT_HEIGHT)
                            .build();
                }

                @Override
                public void tick() {
                    this.elapsed++;
                    this.confirmButton.active = this.elapsed >= CONFIRM_CD_TICKS;
                }

                @Override
                public void init() {
                    super.init();
                    final FrameLayout layout = new FrameLayout();
                    final LinearLayout column = LinearLayout.vertical();
                    column.addChild(this.questionText, settings -> settings.alignHorizontallyCenter());
                    column.addChild(SpacerElement.height(8));
                    column.addChild(this.warningText, settings -> settings.alignHorizontallyCenter());
                    column.addChild(SpacerElement.height(Button.DEFAULT_HEIGHT));
                    final LinearLayout buttons = LinearLayout.horizontal();
                    buttons.addChild(this.backButton);
                    buttons.addChild(SpacerElement.width(4));
                    buttons.addChild(this.confirmButton);
                    column.addChild(buttons, settings -> settings.alignHorizontallyCenter());
                    layout.addChild(column, settings -> settings.alignHorizontallyCenter().alignVerticallyMiddle());
                    layout.setPosition(this.marginX(), this.marginY());
                    layout.setMinDimensions(this.getPopupWidth(), this.getPopupHeight());
                    layout.arrangeElements();

                    this.confirmButton.active = false;
                    this.addWidget(this.questionText);
                    this.addWidget(this.warningText);
                    this.addWidget(this.backButton);
                    this.addWidget(this.confirmButton);
                }

                @Override
                public void onClose() {
                    super.onClose();
                    if (this.confirmed) {
                        this.entry.screen.modifiedPacks.remove(this.entry.pack);
                        final boolean isEnabled = this.entry.pack.info.type() == PackType.CLIENT_RESOURCES
                                && this.entry.screen.minecraft.getResourcePackRepository()
                                        .getSelectedIds().contains(this.entry.pack.getId());
                        if (isEnabled) {
                            this.entry.screen.apply();
                            this.entry.screen.minecraft.reloadResourcePacks();
                        }
                        this.entry.screen.refresh();
                    }
                }

                @Override
                public void renderContent(final GuiGraphics guiGraphics, final int mouseX, final int mouseY,
                        final float partialTick) {
                    this.questionText.render(guiGraphics, mouseX, mouseY, partialTick);
                    this.warningText.render(guiGraphics, mouseX, mouseY, partialTick);
                    this.confirmButton.render(guiGraphics, mouseX, mouseY, partialTick);
                    this.backButton.render(guiGraphics, mouseX, mouseY, partialTick);

                    // Confirm button cooldown overlay
                    final float alpha = ((float) this.elapsed) / ((float) CONFIRM_CD_TICKS);
                    if (alpha < 1f) {
                        final int fillWidth = this.confirmButton.getX() + (int) (this.confirmButton.getWidth() * alpha);
                        guiGraphics.fill(this.confirmButton.getX(), this.confirmButton.getY(),
                                fillWidth, this.confirmButton.getY() + this.confirmButton.getHeight(),
                                0x55FFFFFF);
                    }
                }
            }
        }

        public static final class ModpackResourcesEntry extends Entry<DependencyPack.ModpackResources> {

            public ModpackResourcesEntry(final DependenciesScreen screen, final DependencyPack.ModpackResources pack) {
                super(screen, pack);
            }

            @Override
            public LinkedList<Button> constructInteractionButtons() {

                final LinkedList<Button> buttons = new LinkedList<>();

                // Button which displays a help message on hover
                final Button helpButton = Button.builder(Component.literal("?"), $ -> {
                })
                        .size(Button.DEFAULT_HEIGHT, Button.DEFAULT_HEIGHT)
                        .build();
                final Tooltip helpTooltip = Tooltip.create(Component.translatable(KEY + ".modpack-resources.help"));
                helpButton.setTooltip(helpTooltip);

                buttons.add(helpButton);
                return buttons;
            }

            @Override
            public boolean alwaysShowButtons() {
                return false;
            }
        }

        /**
         * Entry for vanilla packs that can be converted into zipper packs
         */
        public static final class VanillaEntry extends Entry<Pack> {

            public VanillaEntry(final DependenciesScreen screen, final Pack pack) {
                super(screen, pack);
            }

            @Override
            public LinkedList<Button> constructInteractionButtons() {

                final LinkedList<Button> buttons = new LinkedList<>();

                // Button which converts the pack into a zipper pack
                final Button.OnPress convert = $ -> this.convertToZipperPack();
                final Button convertButton = Button.builder(Component.literal("%"), convert)
                        .size(Button.DEFAULT_HEIGHT, Button.DEFAULT_HEIGHT)
                        .build();
                final Tooltip convertTooltip = Tooltip.create(Component.translatable(KEY + ".convert.tooltip"));
                convertButton.setTooltip(convertTooltip);

                buttons.add(convertButton);
                return buttons;
            }

            @Override
            public boolean alwaysShowButtons() {
                return true;
            }

            private void convertToZipperPack() {
                final Path src = this.screen.vanillaPacks.get(this.pack);
                String destFileName = src.getFileName().toString();
                if (!destFileName.endsWith(".zip")) {
                    destFileName += ".zip";
                }
                final Path dest = DependencyInfo.folder(this.screen.packType)
                        .resolve(destFileName);
                try {
                    Files.createDirectories(dest.getParent());
                    if (Files.isDirectory(src)) {
                        zipDirectory(src, dest);
                    } else {
                        Files.copy(src, dest, StandardCopyOption.REPLACE_EXISTING);
                    }
                } catch (final IOException e) {
                    Zipper.LOG.error("Pack conversion failed; Failed to copy file \""
                            + src + "\" to \"" + dest + "\"", e);
                    return;
                }
                final DependencyInfo info = new DependencyInfo(this.screen.packType,
                        src.getFileName().toString().replace(".zip", ""),
                        DependencyInfo.Mode.OPTIONAL_DISABLED,
                        new EnumMap<>(DependencyInfo.Host.class));
                DependencyPack pack;
                try {
                    pack = DependencyPack.from(info);
                } catch (final IOException e) {
                    pack = DependencyPack.from(info, DependencyPack.TEMPLATE_META);
                }
                this.screen.modifiedPacks.add(pack);
                this.screen.editDependency(pack);
            }

            private static void zipDirectory(final Path src, final Path dest)
                    throws IOException {
                final Path tmpFile = Files.createTempFile(dest.getParent(),
                        dest.getFileName().toString(), ".tmp");
                try {
                    try (ZipOutputStream zip = new ZipOutputStream(Files.newOutputStream(tmpFile));
                            Stream<Path> paths = Files.walk(src)) {
                        for (final Path path : (Iterable<Path>) paths::iterator) {
                            if (Files.isDirectory(path))
                                continue;
                            final String entryName = src.relativize(path).toString()
                                    .replace(path.getFileSystem().getSeparator(), "/");
                            zip.putNextEntry(new ZipEntry(entryName));
                            Files.copy(path, zip);
                            zip.closeEntry();
                        }
                    }
                    Files.move(tmpFile, dest, StandardCopyOption.REPLACE_EXISTING);
                } finally {
                    Files.deleteIfExists(tmpFile);
                }
            }
        }
    }
}
