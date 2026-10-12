package dev.nekotune.zipper.definition.web;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Optional;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import dev.nekotune.zipper.definition.DependencyInfo;
import dev.nekotune.zipper.definition.web.api.WebAPI.APIResponse;
import net.minecraft.server.packs.PackType;

public abstract class WebHost {

    protected static final int HTTP_TIMEOUT_MILLIS = 30000;

    protected abstract DependencyInfo.Host hostType();
    
    protected abstract APIResponse<Path> GET(final String slug,
            final PackType packType, final Path downloadTo) throws
                    IOException, InterruptedException,
                    SecurityException;
    
    /**
     * Calls {@link WebHost#GET()} with a timeout guard.
     */
    private final APIResponse<Path> GET$timeout(final String slug,
            final PackType packType, final Path downloadTo, final int millis) throws
                    IOException, InterruptedException,
                    SecurityException, TimeoutException {
        final ExecutorService executor = Executors.newSingleThreadExecutor(r -> {
            final Thread t = new Thread(r);
            t.setDaemon(true);
            return t;
        });
        try {
            final Future<APIResponse<Path>> future = executor.submit(() -> GET(slug, packType, downloadTo));
            try {
                return future.get(millis, TimeUnit.MILLISECONDS);
            } catch (final TimeoutException e) {
                future.cancel(true);
                throw e;
            } catch (final ExecutionException e) {
                final Throwable cause = e.getCause();
                if (cause instanceof final IOException io)
                    throw io;
                if (cause instanceof final SecurityException se)
                    throw se;
                if (cause instanceof final RuntimeException re)
                    throw re;
                throw new IOException("Unexpected error during GET for '" + slug + "'", cause);
            }
        } finally {
            executor.shutdownNow();
        }
    }

    /**
     * Downloads a resource pack from this website.
     * 
     * @param slugInput The slug for the resource pack. This can be found in its URL.
     * @return The downloaded file's path, or {@link Optional#empty()} if the
     *         download failed.
     */
    public final APIResponse<Path> download(final DependencyInfo target)
            throws IOException, InterruptedException, SecurityException, TimeoutException {

        // Send an http GET request for the best file
        APIResponse<Path> response = this.GET$timeout(target.downloadSlugs().get(this.hostType()),
                target.type(), target.packDir(), HTTP_TIMEOUT_MILLIS);

        return response;
    }
}
