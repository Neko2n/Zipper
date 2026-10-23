package dev.nekotune.zipper.client.gui.widgets;

import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;

import com.google.common.hash.Hashing;
import com.mojang.blaze3d.platform.NativeImage;

import dev.nekotune.zipper.Constants;
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
public class PackWidget extends AbstractWidget {

    private static final String TOO_LONG_NAME_SUFFIX = "...";
    private static final int ICON_SIZE = 32;

    protected final Minecraft minecraft;
    protected final Pack pack;
    private FormattedCharSequence nameDisplayCache;
    private MultiLineLabel descriptionDisplayCache;

    public PackWidget(final Minecraft minecraft, final int x, final int y, final int width,
            final Pack pack) {
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
        return MultiLineLabel.create(this.minecraft.font, this.getWidth() - ICON_SIZE, 2, new Component[] { computeDescription() });
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

    @Override
    public void setWidth(final int width) {
        super.setWidth(width);
        this.nameDisplayCache = computeName();
        this.descriptionDisplayCache = computeDescriptionLabel();
    }

    @Override
    protected void renderWidget(final GuiGraphics guiGraphics,
            final int mouseX, final int mouseY, final float partialTick) {
        guiGraphics.blit(PackUtils.getPackIcon(this.pack, this.minecraft.getTextureManager()), this.getX(), this.getY(),
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
        private static final ResourceLocation DEFAULT_ICON = ResourceLocation
                .withDefaultNamespace("textures/misc/unknown_pack.png");
        private static final Map<String, ResourceLocation> iconCache = new HashMap<>();

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
         * Copied from {@link PackSelectionScreen}
         * 
         * @param pack           The pack to fetch the icon for.
         * @param textureManager The TextureManager to fetch the icon from.
         * @return The ICON_SIZExICON_SIZE icon for the given pack.
         */
        public static ResourceLocation getPackIcon(final Pack pack, final TextureManager textureManager) {
            return iconCache.computeIfAbsent(pack.getId(), $ -> loadPackIcon(pack, textureManager));
        }

        /**
         * Copied from {@link PackSelectionScreen}
         */
        private static ResourceLocation loadPackIcon(final Pack pack, final TextureManager textureManager) {
            try {
                ResourceLocation resourcelocation1;
                try (PackResources packresources = pack.open()) {
                    IoSupplier<InputStream> iosupplier = packresources.getRootResource("pack.png");
                    if (iosupplier == null) {
                        return DEFAULT_ICON;
                    }

                    String s = pack.getId();
                    ResourceLocation resourcelocation = ResourceLocation.withDefaultNamespace(
                            "pack/" + Util.sanitizeName(s, ResourceLocation::validPathChar) + "/"
                                    + Hashing.sha256().hashUnencodedChars(s) + "/icon");

                    try (InputStream inputstream = iosupplier.get()) {
                        NativeImage nativeimage = NativeImage.read(inputstream);
                        textureManager.register(resourcelocation, new DynamicTexture(nativeimage));
                        resourcelocation1 = resourcelocation;
                    }
                }

                return resourcelocation1;
            } catch (Exception exception) {
                Constants.LOG.warn("Failed to load icon from pack {}", pack.getId(), exception);
                return DEFAULT_ICON;
            }
        }
    }
}
