package dev.nekotune.zipper.server;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import dev.nekotune.zipper.Zipper;
import dev.nekotune.zipper.Config;
import dev.nekotune.zipper.Constants;
import dev.nekotune.zipper.DownloadManager;
import dev.nekotune.zipper.definition.DependencyInfo;
import dev.nekotune.zipper.platform.PlatformEvents;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.repository.PackRepository;

public class ServerZipper {

    public static volatile Optional<MinecraftServer> running = Optional.empty();

    public static void init() {
        PlatformEvents.SERVER_STARTING.hook.connect(ServerZipper::serverStarting);
        PlatformEvents.SERVER_CLOSING.hook.connect(ServerZipper::serverClosing);

        // Automatically enable OPTIONAL_ENABLED server packs
        DownloadManager.onDownloadsFinished.connect(() -> {
            Constants.LOG.debug("[ServerZipper] onDownloadFinished called");
            running.ifPresent((final MinecraftServer server) -> {
                final PackRepository repo = server.getPackRepository();
                Zipper.enableDownloadedOptionals(repo, PackType.SERVER_DATA);
                server.reloadResources(repo.getSelectedIds());
            });
        });
    }

    /**
     * Fires when a server first begins loading.
     * 
     * @see PlatformEvents
     */
    public static void serverStarting(final MinecraftServer server) {
        Constants.LOG.debug("[ServerZipper] serverLoaded called");
        running = Optional.of(server);

        // Configure enabled server data packs
        final PackRepository repo = server.getPackRepository();
        final Path flagFile = server.getServerDirectory().resolve(
                Path.of(".zipper", "loaded.flag"));
        if (Files.exists(flagFile)) {

            // If the flag file exists, optionals have already been enabled by default;
            // Only enable newly downloaded optionals, if there are any.
            Zipper.enableDownloadedOptionals(repo, PackType.SERVER_DATA);
        } else {

            // If the flag file does not exist yet, write it, and enable all
            // dependencies flagged as OPTIONAL_ENABLED.
            try {
                Files.write(flagFile, new byte[] {}, StandardOpenOption.CREATE_NEW);
            } catch (final Exception e) {
                Constants.LOG.error("Exception occured while writing dependency flag file", e);
            }
            repo.reload();
            final List<DependencyInfo> optionalEnabled = Config.INSTANCE.dependencies.stream()
                    .filter(dependency -> dependency.mode() == DependencyInfo.Mode.OPTIONAL_ENABLED
                            && dependency.type() == PackType.SERVER_DATA)
                    .toList();
            final Set<String> selectedPacks = new LinkedHashSet<>(repo.getSelectedIds());
            selectedPacks.addAll(optionalEnabled.stream()
                    .sorted(Comparator.comparingInt(v -> Config.INSTANCE.getLoadOrder(v)))
                    .map(DependencyInfo::packId)
                    .toList());
            repo.setSelected(selectedPacks);
        }

        // Apply changes automatically
        server.reloadResources(repo.getSelectedIds());
    }

    /**
     * Fires when a server is closing.
     * 
     * @see PlatformEvents
     */
    public static void serverClosing(final MinecraftServer server) {
        Constants.LOG.debug("[ServerZipper] serverClosing called");
        running = Optional.empty();
    }
}
