package dev.nekotune.zipper.client.gui.download;

import java.util.List;
import java.util.Optional;

import dev.nekotune.zipper.DownloadManager.DownloadResult;
import dev.nekotune.zipper.Resources;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.MultiLineTextWidget;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;

public class DownloadErrorScreen extends Screen {

    private static final String KEY = Resources.Lang.Gui.Screen.KEY + ".downloaderror";

    public static final Component TITLE = Component
            .translatableWithFallback(KEY + ".title", "Error Downloading Modpack Resources")
            .withStyle(ChatFormatting.RED, ChatFormatting.BOLD);

    private static record Widgets(
            Button button,
            MultiLineTextWidget message,
            MultiLineTextWidget causes) {
    }

    private final Component message;
    public final Component causes;
    private final Button.OnPress callback;
    private Optional<Widgets> widgets = Optional.empty();

    private DownloadErrorScreen(final List<String> causes, final String message,
            final Button.OnPress callback) {
        super(TITLE);
        this.message = Component.translatable(KEY + ".message." + message + ".1")
                .append(Component.literal("\n"))
                .append(Component.translatable(KEY + ".message." + message + ".2"));
        MutableComponent causesBuilder = Component.empty().append(
                Component.translatableWithFallback(KEY + ".causes", "Caused by:")
                        .withStyle(ChatFormatting.BOLD));
        for (final String cause : causes) {
            causesBuilder = causesBuilder.append(Component.literal("\n"))
                    .append(Component.literal(cause)
                            .setStyle(Style.EMPTY.withColor(ChatFormatting.RED)));
        }
        this.causes = causesBuilder;
        this.callback = callback;
    }

    public DownloadErrorScreen(final DownloadResult errorType, final List<String> causes,
            final Button.OnPress callback) {
        this(causes, errorType.toString().toLowerCase().replace('_', '-'), callback);
    }

    @Override
    protected void init() {
        super.init();
        final var buttonWidget = this.addRenderableWidget(Button.builder(CommonComponents.GUI_CONTINUE, this.callback)
                .size(200, 20)
                .build());
        final var messageWidget = this.addRenderableWidget(
                new MultiLineTextWidget(this.message, this.font)
                        .setCentered(true)
                        .setColor(0xFFFFFFFF));
        final var causesWidget = this.addRenderableWidget(
                new MultiLineTextWidget(this.causes, this.font)
                        .setCentered(true)
                        .setColor(0xFFFFFFFF));
        this.widgets = Optional.of(new Widgets(buttonWidget, messageWidget, causesWidget));
    }

    @Override
    public void render(final GuiGraphics guiGraphics, final int mouseX, final int mouseY,
            final float partialTick) {
        super.render(guiGraphics, mouseX, mouseY, partialTick);
        final int titlePosY = this.height / 4;
        guiGraphics.drawCenteredString(this.font, this.title, this.width / 2, titlePosY, 0xFFFFFFFF);
        this.widgets.ifPresent((final Widgets widgets) -> {
            int yPos = titlePosY + 20;
            widgets.message.setPosition(this.width / 2 - widgets.message.getWidth() / 2, yPos);
            yPos += widgets.message.getHeight() + 20;
            widgets.causes.setPosition(this.width / 2 - widgets.causes.getWidth() / 2, yPos);
            yPos += widgets.causes.getHeight() + 20;
            widgets.button.setPosition(this.width / 2 - widgets.button.getWidth() / 2,
                    Math.min(this.height - 20, yPos));
        });
    }

    @Override
    public void renderBackground(final GuiGraphics guiGraphics, final int mouseX, final int mouseY,
            final float partialTick) {
        guiGraphics.fillGradient(0, 0, this.width, this.height, -12574688, -11530224);
    }

    @Override
    public boolean shouldCloseOnEsc() {
        return false;
    }
}
