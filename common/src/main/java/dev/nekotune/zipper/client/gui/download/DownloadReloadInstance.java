package dev.nekotune.zipper.client.gui.download;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.function.Supplier;

import dev.nekotune.zipper.Zipper;
import dev.nekotune.zipper.core.Event;
import net.minecraft.server.packs.resources.ReloadInstance;

public class DownloadReloadInstance implements ReloadInstance {

    public final int toDownload;
    public final Supplier<Integer> remaining;
    private final CountDownLatch latch = new CountDownLatch(1);
    private final CompletableFuture<Void> future;

    public DownloadReloadInstance(final int toDownload, final Supplier<Integer> remaining,
            final Event.Hook<?> onComplete) {
        this.toDownload = toDownload;
        this.remaining = remaining;
        onComplete.connect(this.latch::countDown);

        // Resolve race condition where the event fires before it's connected
        if (remaining.get() <= 0) {
            this.latch.countDown();
        }
        
        future = CompletableFuture.runAsync(() -> {
            try {
                this.latch.await();
            } catch (final InterruptedException e) {
                Zipper.LOG.error("[DownloadReloadInstance] An exception occured", e);
            }
        });
    }

    @Override
    public CompletableFuture<Void> done() {
        return future;
    }

    @Override
    public float getActualProgress() {
        return 1f - (((float)remaining.get()) / ((float)toDownload));
    }
}
