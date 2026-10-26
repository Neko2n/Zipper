package dev.nekotune.zipper.client.gui.widgets;

import java.io.InputStream;
import java.util.HashMap;

import com.google.common.hash.Hashing;
import com.mojang.blaze3d.platform.NativeImage;

import dev.nekotune.zipper.Resources;
import dev.nekotune.zipper.Zipper;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.MultiLineLabel;
import net.minecraft.client.gui.narration.NarratedElementType;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.gui.screens.packs.PackSelectionModel;
import net.minecraft.client.gui.screens.packs.PackSelectionScreen;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.client.renderer.texture.TextureManager;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.PackResources;
import net.minecraft.server.packs.repository.Pack;
import net.minecraft.server.packs.resources.IoSupplier;
import net.minecraft.util.FormattedCharSequence;

/**
 * Widget which shows information about a resource/data pack.
 */
public class PackWidget<T extends Pack> extends AbstractWidget {

    private static final String TOO_LONG_NAME_SUFFIX = "...";
    private static final int ICON_SIZE = 32;

    protected final Minecraft minecraft;
    protected final T pack;
    private FormattedCharSequence nameDisplayCache;
    private MultiLineLabel descriptionDisplayCache;

    public PackWidget(final Minecraft minecraft, final int x, final int y, final int width,
            final T pack) {
        super(x, y, width, ICON_SIZE, pack.getTitle());
        this.minecraft = minecraft;
        this.pack = pack;
        this.nameDisplayCache = computeName();
        this.descriptionDisplayCache = computeDescriptionLabel();
    }

    protected FormattedCharSequence computeName() {
        final Component title = pack.getTitle();
        final Font font = this.minecraft.font;
        int i = font.width(title);
        if (i > 157) {
            FormattedText formattedText = FormattedText.composite(new FormattedText[] {
                    font.substrByWidth(title, this.getWidth() - ICON_SIZE - font.width(TOO_LONG_NAME_SUFFIX)),
                    FormattedText.of(TOO_LONG_NAME_SUFFIX) });
            return Language.getInstance().getVisualOrder(formattedText);
        } else {
            return title.getVisualOrderText();
        }
    }

    private MultiLineLabel computeDescriptionLabel() {
        return MultiLineLabel.create(this.minecraft.font, this.getWidth() - ICON_SIZE, 2,
                new Component[] { computeDescription() });
    }

    protected Component computeDescription() {
        return PackUtils.getExtendedDescription(pack);
    }

    public Component getNarration() {
        return Component.translatable("narrator.select", new Object[] { this.pack.getTitle() });
    }

    public int getIconSize() {
        return ICON_SIZE;
    }

    public ResourceLocation getPackIcon() {
        return PackUtils.getPackIcon(this.pack, this.minecraft.getTextureManager());
    }

    @Override
    public void setWidth(final int width) {
        super.setWidth(width);
        this.nameDisplayCache = computeName();
        this.descriptionDisplayCache = computeDescriptionLabel();
    }

    @Override
    protected void renderWidget(final GuiGraphics guiGraphics,
            final int mouseX, final int mouseY, final float partialTick) {
        guiGraphics.blit(this.getPackIcon(), this.getX(), this.getY(),
                0.0F, 0.0F, ICON_SIZE, ICON_SIZE, ICON_SIZE, ICON_SIZE);
        final FormattedCharSequence formattedCharSequence = this.nameDisplayCache;
        final MultiLineLabel multiLineLabel = this.descriptionDisplayCache;
        guiGraphics.drawString(this.minecraft.font, formattedCharSequence, this.getX() + ICON_SIZE + 2, this.getY() + 1,
                16777215);
        multiLineLabel.renderLeftAligned(guiGraphics, this.getX() + ICON_SIZE + 2, this.getY() + 12, 10, -8355712);
    }

    @Override
    protected void updateWidgetNarration(final NarrationElementOutput narrationElementOutput) {
        narrationElementOutput.add(NarratedElementType.TITLE, this.pack.getTitle());
    }

    protected static final class PackUtils {
        private static final HashMap<String, ResourceLocation> iconCache = new HashMap<>();

        /**
         * Copied from {@link PackSelectionModel.Entry}
         * 
         * @param pack The pack to read the extended description of.
         * @return The extended description for the given pack.
         */
        public static Component getExtendedDescription(final Pack pack) {
            return pack.getPackSource().decorate(pack.getDescription());
        }

        /**
         * Derived from {@link PackSelectionScreen}
         * 
         * @param pack           The pack to fetch the icon for.
         * @param textureManager The TextureManager to fetch the icon from.
         * @return The icon for the given pack.
         */
        public static ResourceLocation getPackIcon(final Pack pack, final TextureManager textureManager) {
            return iconCache.computeIfAbsent(pack.getId(), $ -> loadPackIcon(pack, textureManager));
        }

        /**
         * Derived from {@link PackSelectionScreen}
         */
        private static ResourceLocation loadPackIcon(final Pack pack, final TextureManager textureManager) {
            try {
                try (PackResources packresources = pack.open()) {
                    final IoSupplier<InputStream> iosupplier = packresources.getRootResource("pack.png");
                    if (iosupplier == null) {
                        return Resources.Gui.DEFAULT_PACK_ICON;
                    }
                    String s = pack.getId();
                    final ResourceLocation icon = ResourceLocation.withDefaultNamespace(
                            "pack/" + Util.sanitizeName(s, ResourceLocation::validPathChar) + "/"
                                    + Hashing.sha256().hashUnencodedChars(s) + "/icon");
                    try (InputStream inputstream = iosupplier.get()) {
                        final NativeImage nativeimage = NativeImage.read(inputstream);
                        textureManager.register(icon, new DynamicTexture(nativeimage));
                    }
                    return icon;
                }
            } catch (final Exception e) {
                Zipper.LOG.warn("Failed to load icon from pack " + pack.getId(), e);
                return Resources.Gui.DEFAULT_PACK_ICON;
            }
        }
    }
}
