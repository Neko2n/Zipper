package dev.nekotune.zipper.client.gui;

import dev.nekotune.zipper.Resources;
import dev.nekotune.zipper.Zipper;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.AlertScreen;
import net.minecraft.network.chat.Component;

public class ReloadPromptScreen extends AlertScreen {

    private static final String ID = Resources.Lang.Gui.Screen.KEY + ".reloadprompt";

    public static final Component TITLE = Component
            .translatableWithFallback(ID + ".title", "Modpack Resources Downloaded")
            .withStyle(ChatFormatting.BOLD);

    public static final Component MESSAGE = Component
            .translatableWithFallback(ID + ".message.1", "New resources have been downloaded.")
            .append(Component.literal("\n"))
            .append(Component.translatableWithFallback(ID + ".message.2", "A reload is required to apply them."));

    public static final Component BUTTON = Component
            .translatableWithFallback(ID + ".button", "Reload");

    public ReloadPromptScreen(final Runnable callback) {
        super(() -> {
            Zipper.LOG.debug("[ReloadPromptScreen] Reloading resources");
            callback.run();
            Minecraft.getInstance().reloadResourcePacks();
        }, TITLE, MESSAGE, BUTTON, false);
    }

    public ReloadPromptScreen() {
        this(() -> {
        });
    }
}
