package dev.nekotune.zipper.client;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardWatchEventKinds;
import java.nio.file.WatchEvent;
import java.nio.file.WatchKey;
import java.nio.file.WatchService;
import java.util.Optional;

import dev.nekotune.zipper.Zipper;
import net.minecraft.client.gui.screens.packs.PackSelectionScreen;

/**
 * Copied from {@link PackSelectionScreen.Watcher}
 */
public final class PackDirectoryWatcher implements AutoCloseable {
    private final WatchService watcher;
    private final Path directory;

    private PackDirectoryWatcher(final Path directory) throws IOException {
        this.directory = directory;
        this.watcher = directory.getFileSystem().newWatchService();
        try {
            this.watch(directory);
            try (DirectoryStream<Path> stream = Files.newDirectoryStream(directory)) {
                for (final Path path : stream) {
                    if (Files.isDirectory(path, LinkOption.NOFOLLOW_LINKS)) {
                        this.watch(path);
                    }
                }
            }
        } catch (final Exception e) {
            this.watcher.close();
            throw e;
        }
    }

    public static Optional<PackDirectoryWatcher> create(final Path directory) {
        try {
            return Optional.of(new PackDirectoryWatcher(directory));
        } catch (final IOException e) {
            Zipper.LOG.warn("Failed to initialize pack directory {} monitoring", directory, e);
            return Optional.empty();
        }
    }

    /**
     * @return true if anything changed since the last poll
     */
    public boolean pollForChanges() throws IOException {
        boolean changed = false;
        WatchKey key;
        while ((key = this.watcher.poll()) != null) {
            for (final WatchEvent<?> event : key.pollEvents()) {
                changed = true;
                if (key.watchable() == this.directory && event.kind() == StandardWatchEventKinds.ENTRY_CREATE) {
                    final Path path = this.directory.resolve((Path) event.context());
                    if (Files.isDirectory(path, LinkOption.NOFOLLOW_LINKS)) {
                        this.watch(path);
                    }
                }
            }
            key.reset();
        }
        return changed;
    }

    private void watch(final Path directory) throws IOException {
        directory.register(this.watcher, StandardWatchEventKinds.ENTRY_CREATE,
                StandardWatchEventKinds.ENTRY_DELETE, StandardWatchEventKinds.ENTRY_MODIFY);
    }

    @Override
    public void close() throws IOException {
        this.watcher.close();
    }
}