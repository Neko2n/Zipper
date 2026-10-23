package dev.nekotune.zipper.client.gui.config.dependencies;

import java.io.IOException;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import dev.nekotune.zipper.Config;
import dev.nekotune.zipper.Resources;
import dev.nekotune.zipper.client.gui.config.AbstractConfigScreen;
import dev.nekotune.zipper.client.gui.widgets.PackWidget;
import dev.nekotune.zipper.client.gui.widgets.container.SettingsList;
import dev.nekotune.zipper.definition.DependencyInfo;
import dev.nekotune.zipper.definition.DependencyPack;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractContainerWidget;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.SpriteIconButton;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.layouts.FrameLayout;
import net.minecraft.client.gui.layouts.LinearLayout;
import net.minecraft.client.gui.layouts.SpacerElement;
import net.minecraft.client.gui.narration.NarratedElementType;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.packs.TransferableSelectionList;
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

    private final LinkedHashSet<DependencyPack> originalPacks;
    private final LinkedHashSet<DependencyPack> modifiedPacks;
    private final PackType packType;
    protected final Button createPackButton;

    public DependenciesScreen(final Screen lastScreen, final PackType packType) {
        super(TITLES.get(packType), lastScreen);
        this.originalPacks = new LinkedHashSet<DependencyPack>(Config.INSTANCE.dependencies.stream()
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
        this.packType = packType;
        this.createPackButton = Button.builder(
                Component.literal("+").withStyle(ChatFormatting.BOLD),
                (final Button button) -> editDependency(
                        DependencyPack.from(DependencyInfo.createDefault(this.packType),
                                DependencyPack.TEMPLATE_META)))
                .size(Button.DEFAULT_HEIGHT, Button.DEFAULT_HEIGHT)
                .build();
    }

    /**
     * Injects all modified dependencies into the configuration.
     */
    protected void apply() {
        Config.INSTANCE.dependencies.removeAll(this.originalPacks.stream().map(p -> p.info).toList());
        Config.INSTANCE.dependencies.addAll(this.modifiedPacks.stream().map(p -> p.info).toList());
    }

    /**
     * Opens the editor screen for the given dependency.
     * 
     * @param dependency The dependency to edit.
     */
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
    }

    @Override
    public void renderBottomButton(final GuiGraphics guiGraphics, final int mouseX, final int mouseY,
            final float partialTick) {
        super.renderBottomButton(guiGraphics, mouseX, mouseY, partialTick);
        this.createPackButton.render(guiGraphics, mouseX, mouseY, partialTick);
    }

    @Override
    protected void populateSettings(final SettingsList.SettingsContent.Builder builder) {
        final LinkedList<DependencyPack> indexable = new LinkedList<>(this.modifiedPacks);
        Optional<Entry> above = Optional.empty();
        Optional<Entry> below = Optional.empty();
        for (int i = 0; i < indexable.size(); i++) {
            final Entry entry = below.orElse(new Entry(this, indexable.get(i)));
            if (i + 1 < indexable.size()) {
                final DependencyPack nextPack = indexable.get(i + 1);
                below = Optional.of(new Entry(this, nextPack));
                entry.below = below;
            }
            entry.above = above;
            above = Optional.of(entry);
            builder.addElement(entry);
        }
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

    protected static class Entry extends AbstractContainerWidget {

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
        protected final Button editButton;
        protected final Button deleteButton;
        public final DependenciesScreen screen;
        public final DependencyPack pack;
        public Optional<Entry> above = Optional.empty();
        public Optional<Entry> below = Optional.empty();

        public Entry(final DependenciesScreen screen, final DependencyPack pack) {
            super(0, 0, screen.getInnerWidth() - 4, 32, pack.getTitle());
            this.screen = screen;
            this.pack = pack;

            // Button which modifies the dependency's information
            final Resources.Sprite editIcon = Resources.Gui.Sprites.Icons.EDIT;
            this.editButton = SpriteIconButton.builder(Component.empty(),
                    (final Button button) -> this.screen.editDependency(pack),
                    true)
                    .size(Button.DEFAULT_HEIGHT, Button.DEFAULT_HEIGHT)
                    .sprite(editIcon.location(), editIcon.width(), editIcon.height())
                    .build();
            this.editButton.setTooltip(Tooltip.create(
                    Component.translatableWithFallback(KEY + ".edit.tooltip",
                            "Edit dependency")));

            // Button which deletes the dependency from the list
            final Resources.Sprite deleteIcon = Resources.Gui.Sprites.Icons.DELETE;
            this.deleteButton = SpriteIconButton.builder(
                    Component.empty(),
                    (final Button button) -> {

                        // On click, re-build the scroll list with this dependency removed.
                        // TODO show confirmation pop-up
                        this.screen.modifiedPacks.remove(pack);
                        this.screen.refresh();
                    },
                    true)
                    .size(Button.DEFAULT_HEIGHT, Button.DEFAULT_HEIGHT)
                    .sprite(deleteIcon.location(), deleteIcon.width(), deleteIcon.height())
                    .build();
            this.deleteButton.setTooltip(Tooltip.create(
                    Component.translatableWithFallback(KEY + ".delete.tooltip",
                            "Delete dependency").withStyle(ChatFormatting.RED)));

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

            this.arrangeElements();
        }

        public void arrangeElements() {
            final FrameLayout layout = new FrameLayout();
            layout.setMinWidth(this.getWidth());
            layout.setPosition(this.getX(), this.getY());
            layout.addChild(this.packWidget, settings -> settings.alignHorizontallyLeft().alignVerticallyMiddle());
            final LinearLayout rightGroup = LinearLayout.horizontal();
            rightGroup.addChild(SpacerElement.width(4));
            rightGroup.addChild(this.editButton, settings -> settings.alignVerticallyMiddle());
            rightGroup.addChild(SpacerElement.width(4));
            rightGroup.addChild(this.deleteButton, settings -> settings.alignVerticallyMiddle());
            rightGroup.addChild(SpacerElement.width(4));
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
        public void swapWith(final Entry swapWith) {
            final var backup = new LinkedHashSet<>(this.screen.modifiedPacks);
            this.screen.modifiedPacks.clear();
            for (final DependencyPack existing : backup) {
                if (existing == this.pack) {
                    this.screen.modifiedPacks.add(swapWith.pack);
                } else if (existing == swapWith.pack) {
                    this.screen.modifiedPacks.add(this.pack);
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
            final List<AbstractWidget> widgets = new LinkedList<>(
                    List.of(this.editButton, this.deleteButton, packWidget));
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
            if (this.isHovered()) {
                this.editButton.render(guiGraphics, mouseX, mouseY, partialTick);
                this.deleteButton.render(guiGraphics, mouseX, mouseY, partialTick);

                // Render priority shifting arrows over the packWidget icon
                final int iconX = this.packWidget.getX();
                final int iconY = this.packWidget.getY();
                final int iconSize = this.packWidget.getIconSize();
                guiGraphics.fill(iconX, iconY,
                        iconX + iconSize, iconY + iconSize,
                        HOVER_OVERLAY);
                if (this.above.isPresent()) {
                    this.priorityButtons.up().render(guiGraphics, mouseX, mouseY, partialTick);
                }
                if (this.below.isPresent()) {
                    this.priorityButtons.down().render(guiGraphics, mouseX, mouseY, partialTick);
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
         * Custom PackWidget with a simplified description
         */
        public static class ZipperPackWidget extends PackWidget {

            public ZipperPackWidget(final Entry entry) {
                super(entry.screen.minecraft, 0, 0, 0, entry.pack);
            }

            @Override
            protected Component computeDescription() {
                return this.pack.getDescription();
            }
        }
    }
}
