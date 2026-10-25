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
import dev.nekotune.zipper.Zipper;
import dev.nekotune.zipper.client.gui.AbstractPopupScreen;
import dev.nekotune.zipper.client.gui.config.AbstractConfigScreen;
import dev.nekotune.zipper.client.gui.widgets.PackWidget;
import dev.nekotune.zipper.client.gui.widgets.container.SettingsList;
import dev.nekotune.zipper.definition.DependencyInfo;
import dev.nekotune.zipper.definition.DependencyPack;
import net.minecraft.ChatFormatting;
import net.minecraft.Util;
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
import net.minecraft.client.gui.screens.packs.TransferableSelectionList;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.packs.PackType;

// TODO display modpack resources at the top with two buttons:
//  - a question mark button to show a popup explaining modpack resources
//  - a folder button to open in your OS's explorer

// TODO show vanilla & unregistered packs and allow converting them into zipper packs

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
    }

    private void populateSection(final SettingsList.SettingsContent.Builder builder,
            final LinkedList<DependencyPack> packs, final boolean canMove, final Component title) {
        if (packs.size() == 0)
            return;
        builder.addElement(new StringWidget(title, this.font));
        if (canMove) {
            Optional<Entry<?>> above = Optional.empty();
            Optional<Entry<?>> below = Optional.empty();
            for (int i = 0; i < packs.size(); i++) {
                final Entry<?> entry = below.orElse(new Entry.DependencyEntry(this, packs.get(i)));
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

    protected static sealed abstract class Entry<T extends DependencyPack> extends AbstractContainerWidget {

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
        public Optional<Entry<?>> above = Optional.empty();
        public Optional<Entry<?>> below = Optional.empty();

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
        public void swapWith(final Entry<?> swapWith) {
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
         * Custom PackWidget with a simplified description
         */
        public static class ZipperPackWidget extends PackWidget {

            public ZipperPackWidget(final Entry<?> entry) {
                super(entry.screen.minecraft, 0, 0, 0, entry.pack);
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

                            // On click, re-build the scroll list with this dependency removed.
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

                // Button which displays a help popup window
                final Button helpButton = Button.builder(Component.literal("?"),
                        (final Button button) -> this.screen.minecraft
                                .setScreen(new HelpButtonPopupScreen(this.screen)))
                        .size(Button.DEFAULT_HEIGHT, Button.DEFAULT_HEIGHT)
                        .build();

                buttons.add(helpButton);
                return buttons;
            }

            @Override
            public boolean alwaysShowButtons() {
                return false;
            }

            /**
             * Screen that shows when you click the "?" button next to the modpack resources
             * listing
             */
            public static final class HelpButtonPopupScreen extends AbstractPopupScreen<DependenciesScreen> {

                protected HelpButtonPopupScreen(final DependenciesScreen covering) {
                    super(covering, 400, 100, Component.empty());
                }

                @Override
                public void renderContent(final GuiGraphics guiGraphics, final int mouseX, final int mouseY,
                        final float partialTick) {
                    final int centerX = this.marginX() + (this.getPopupWidth() / 2);
                    final int centerY = this.marginY() + (this.getPopupHeight() / 2);
                    final int textColor = 0xFFFFFFFF;
                    final int padding = 20;

                    // TODO write help text

                    // Localized explanation text
                    final Component explanation = Component.translatable(KEY + ".popup.download-help");
                    guiGraphics.drawCenteredString(this.font, explanation, centerX, centerY - (padding / 2), textColor);

                    // URL example for where to find project slugs
                    final Component urlExample = Component.literal("project-slug")
                            .withStyle(ChatFormatting.BOLD, ChatFormatting.YELLOW);
                    guiGraphics.drawCenteredString(this.font, urlExample, centerX, centerY + (padding / 2), textColor);
                }
            }
        }
    }
}
