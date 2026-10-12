package dev.nekotune.zipper;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Collection;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeoutException;
import java.util.stream.Stream;

import dev.nekotune.zipper.core.Event;
import dev.nekotune.zipper.definition.DependencyInfo;
import dev.nekotune.zipper.definition.web.WebHost;
import dev.nekotune.zipper.definition.web.api.WebAPI.APIResponse;

public final class DownloadManager {

    private static final Event<Void> downloadsFinished = new Event<>();
    public static final Event.Hook<Void> onDownloadsFinished = downloadsFinished.hook;

    private static volatile DownloadState state = DownloadState.NOT_STARTED;
    private static volatile int remaining = 0;
    private static volatile int total = 0;

    public static enum DownloadState {
        NOT_STARTED,
        STARTED,
        FINISHED,
        INTERRUPTED;
    }

    public static DownloadState getState() {
        return state;
    }

    public static int getRemaining() {
        return remaining;
    }

    public static int getTotal() {
        return total;
    }

    /**
     * A list of errors that occured during the download process.
     * Errors are mapped by the slug belonging to the dependency that errored.
     */
    public static final Map<String, DownloadResult> DOWNLOAD_ERRORS = new HashMap<>();

    /**
     * A list of download targets which have been successfully downloaded
     * in the current session.
     */
    private static final Map<String, DependencyInfo> DOWNLOADED = new HashMap<>();

    /**
     * @see DownloadManager#DOWNLOADED
     */
    public static Collection<DependencyInfo> getDownloaded() {
        return DOWNLOADED.values();
    }

    /**
     * @return True if the dependency with the given target was downloaded
     *         in the running session, false otherwise.
     */
    public static boolean wasDownloaded(final DependencyInfo target) {
        return DOWNLOADED.containsKey(target.fileName());
    }

    /**
     * Dispatches worker threads to fetch the provided dependency targets.
     * Populates the {@link DownloadManager#DOWNLOADED} map.
     * 
     * @param targets A list of dependency targets to download from the web.
     * @return The scheduled worker threads bundled as a DownloadThreads object.
     * @see WebHost
     */
    public static Download dispatch(final List<DependencyInfo> targets) {
        final Download threads = new Download(targets);
        threads.forEach(WorkerThread::start);
        state = DownloadState.STARTED;
        Thread.startVirtualThread(() -> {
            try {
                threads.await();
                state = DownloadState.FINISHED;
            } catch (final InterruptedException e) {
                Constants.LOG.error(e.toString());
                state = DownloadState.INTERRUPTED;
            }
            downloadsFinished.controller.post(null);
            downloadsFinished.controller.clear();
        });
        return threads;
    }

    public static final class Download extends CountDownLatch implements Iterable<WorkerThread> {
        private final EnumMap<DependencyInfo.Host, WorkerThread> map = new EnumMap<>(DependencyInfo.Host.class);

        public Download(final List<DependencyInfo> targets) {
            super(DependencyInfo.Host.values().length);
            for (final DependencyInfo.Host host : DependencyInfo.Host.values()) {
                map.put(host, new WorkerThread(this,
                        targets.stream().filter(host::downloads).toList()));
            }
            total = targets.size();
            remaining = total;
        }

        public Map<DependencyInfo.Host, WorkerThread> toMap() {
            return map;
        }

        @Override
        public Iterator<WorkerThread> iterator() {
            return map.values().iterator();
        }
    }

    private static final class WorkerThread extends Thread {

        private static final Set<DependencyInfo> handled = new HashSet<>();
        private static final Semaphore sem = new Semaphore(1);

        private final CountDownLatch latch;
        private final Set<DependencyInfo> targets;

        private WorkerThread(final CountDownLatch latch,
                final Collection<DependencyInfo> targets) {
            this.latch = latch;
            this.targets = Set.copyOf(targets);
        }

        @Override
        public void run() {
            for (final DependencyInfo target : targets) {

                // Ensure target is not already handled by another thread
                sem.acquireUninterruptibly();
                if (handled.contains(target)) {
                    sem.release();
                    continue;
                }
                handled.add(target);
                sem.release();

                // Download dependency target
                final DownloadResult result;
                try {
                    result = tryDownload(target);
                    remaining--;
                } catch (final Exception e) {
                    DOWNLOAD_ERRORS.put(target.fileName(), DownloadResult.EXCEPTION);
                    remaining--;
                    latch.countDown();
                    Constants.LOG.error("[DownloadManager$WorkerThread] An exception occured", e);
                    this.interrupt();
                    return;
                }
                switch (result) {
                    case SUCCESS:
                        Constants.LOG.debug("[DownloadManager] [Working Thread " + this.threadId()
                                + "] Download SUCCESS for target " + target.toString());
                        DOWNLOADED.put(target.fileName(), target);
                        break;
                    case NOT_FOUND:
                        Constants.LOG.warn("[DownloadManager] [Working Thread " + this.threadId()
                                + "] Download FAILURE; No files found for target " + target.toString()
                                + "; Report this to the modpack author");
                        DOWNLOAD_ERRORS.put(target.fileName(), result);
                        break;
                    case SECURITY_BLOCKED:
                        Constants.LOG.warn("[DownloadManager] [Working Thread " + this.threadId()
                                + "] Download FAILURE; Downloads blocked by security permissions; Check your firewall settings");
                        DOWNLOAD_ERRORS.put(target.fileName(), result);
                        break;
                    case TIMED_OUT:
                        Constants.LOG.warn("[DownloadManager] [Working Thread " + this.threadId()
                                + "] Download FAILURE; Connection timed out");
                        DOWNLOAD_ERRORS.put(target.fileName(), result);
                        break;
                    case EXCEPTION:
                        Constants.LOG.error("[DownloadManager] [Working Thread " + this.threadId()
                                + "] Download FAILURE; An exception occured");
                        DOWNLOAD_ERRORS.put(target.fileName(), result);
                        break;
                }
            }
            latch.countDown();
        }

        private static DownloadResult tryDownload(final DependencyInfo target)
                throws InterruptedException {

            // Attempt to download file from all websites
            final List<DependencyInfo.Host> hosts = Stream.of(DependencyInfo.Host.values())
                    .filter(host -> host.downloads(target))
                    .toList();
            for (final DependencyInfo.Host host : hosts) {
                final APIResponse<Path> response;
                try {
                    response = host.get().download(target);
                } catch (final SecurityException e) {
                    Constants.LOG.error("[DownloadManager$tryDownload] An exception occured", e);
                    return DownloadResult.SECURITY_BLOCKED;
                } catch (final TimeoutException e) {
                    Constants.LOG.error("[DownloadManager$tryDownload] An exception occured", e);
                    return DownloadResult.TIMED_OUT;
                } catch (final IOException e) {
                    Constants.LOG.error("[DownloadManager$tryDownload] An exception occured", e);
                    return DownloadResult.EXCEPTION;
                }

                // Report a successful download
                if (response.statusCode() == 200) {
                    return DownloadResult.SUCCESS;
                }

                // Report that there was no file at the given slugs, and try the next host.
                if (response.statusCode() == 404) {
                    Constants.LOG.warn(
                            "[DownloadManager$tryDownload] HTTP 404; No files found for dependency "
                                    + target.fileName() + " at host " + host.toString());
                    continue;
                }
            }
            return DownloadResult.NOT_FOUND;
        }
    }

    public static enum DownloadResult {
        SUCCESS,
        NOT_FOUND,
        EXCEPTION,
        TIMED_OUT,
        SECURITY_BLOCKED;
    }
}
