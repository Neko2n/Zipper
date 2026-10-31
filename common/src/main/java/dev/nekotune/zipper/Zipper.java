package dev.nekotune.zipper;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import dev.nekotune.zipper.DownloadManager.Download;
import dev.nekotune.zipper.definition.DependencyInfo;
import dev.nekotune.zipper.definition.DependencyPack;
import dev.nekotune.zipper.definition.DependencyPack.ModpackResources;
import dev.nekotune.zipper.platform.Services;
import dev.nekotune.zipper.platform.services.IPlatformHelper;
import dev.nekotune.zipper.platform.services.IPlatformHelper.Dist;
import dev.nekotune.zipper.server.ServerZipper;

import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.repository.Pack;
import net.minecraft.server.packs.repository.PackRepository;
import net.minecraft.server.packs.repository.PackSource;

public final class Zipper {

    public static final String MOD_ID = "zipper";
    public static final String MOD_NAME = "Zipper";
    public static final Logger LOG = LoggerFactory.getLogger(MOD_NAME);

    /**
     * The versions of Minecraft that this mod can run on.
     */
    public static final String[] MC_VERSIONS = {
            "1.21.1"
    };

    public static void init() {
        final IPlatformHelper platform = Services.PLATFORM.get();
        if (platform.dist() == Dist.SERVER) {
            ServerZipper.init();
        }

        // Download dependencies
        (new DownloadTaskThread()).start();
    }

    /**
     * Utility method to order a collection of packs as they would be when enabled.
     * 
     * @param packs The initial, unordered collection of packs to order.
     * @return The ordered list of packs.
     */
    public static LinkedList<Pack> reorderPacks(final Collection<Pack> packs) {
        final LinkedList<Pack> modpackResources = new LinkedList<>();
        final LinkedList<Pack> required = new LinkedList<>();
        final LinkedList<Pack> remove = new LinkedList<>();

        for (final Pack pack : packs) {
            if (pack instanceof final DependencyPack dependency) {
                if (pack instanceof ModpackResources) {
                    modpackResources.add(pack);
                    continue;
                }
                switch (dependency.info.mode()) {
                    case FORCED:
                        required.add(pack);
                        break;
                    // case SUPPORT:
                    //     remove.add(pack);
                    //     break;
                    default:
                        break;
                }
            } else if (pack.isRequired()) {
                required.add(pack);
            }
        }

        final LinkedList<Pack> orderedPacks = new LinkedList<>(packs);
        orderedPacks.removeAll(remove);

        // Sort required packs as defined in the configuration
        required.sort(Comparator.<Pack>comparingInt(p -> {
            final int loadOrder = Config.INSTANCE.getLoadOrder(p);
            if (loadOrder == -1) {
                return Config.INSTANCE.orderedPackIDs.size();
            }
            return loadOrder;
        })
                .reversed());

        // Modpack resources are always-on-top required packs
        required.addAll(modpackResources);

        // Remove and re-insert required packs after built-in packs
        orderedPacks.removeAll(required);
        int i = 0;
        while (i < orderedPacks.size()) {
            final Pack candidate = orderedPacks.get(i);
            if (candidate.getPackSource() == PackSource.BUILT_IN
                    || (candidate.isRequired() && candidate.getPackSource() == PackSource.DEFAULT)) {
                i++;
                Zipper.LOG.debug("[PackRepositoryMixin] Forced dependency insert shifted after pack \""
                        + candidate.getId() + "\" to position " + i);
            } else {
                break;
            }
        }
        orderedPacks.addAll(i, required);
        Zipper.LOG.debug("[PackRepositoryMixin] Inserted " + required.size()
                + " required dependency packs at position " + i);

        return orderedPacks;
    }

    /**
     * Utility method to automatically enable downloaded dependency packs marked as
     * OPTIONAL_ENABLED
     * 
     * @param repo The pack repository to enable the packs within
     * @param type The type of packs in the repository
     */
    public static void enableDownloadedOptionals(final PackRepository repo, final PackType type) {
        repo.reload();
        final List<DependencyInfo> optionalEnabled = DownloadManager.getDownloaded().stream()
                .filter(dependency -> dependency.mode() == DependencyInfo.Mode.OPTIONAL_ENABLED
                        && dependency.type() == type)
                .toList();
        final Set<String> selectedPacks = new LinkedHashSet<>(repo.getSelectedIds());
        selectedPacks.addAll(optionalEnabled.stream()
                .sorted(Comparator.comparingInt(v -> Config.INSTANCE.getLoadOrder(v)))
                .map(DependencyInfo::packId)
                .toList());
        repo.setSelected(selectedPacks);
    }

    /**
     * Task thread to handle downloading dependencies separately from the main
     * thread.
     * 
     * @see DownloadManager
     */
    public static final class DownloadTaskThread extends Thread {

        private final List<DependencyInfo> targets;

        private DownloadTaskThread() {

            // Build a list of download targets, excluding those already downloaded
            this.targets = new ArrayList<>(Config.INSTANCE.dependencies.stream()
                    .filter(target -> {
                        final boolean isWebDependency = !target.downloadSlugs().values().stream()
                                .allMatch(String::isBlank);
                        return isWebDependency && !target.fileExists();
                    }).toList());
        }

        @Override
        public void run() {
            Zipper.LOG.debug("[Zipper] [Download Thread] Thread started");
            final Download threads = DownloadManager.dispatch(targets);
            final long startTime = System.currentTimeMillis();
            Zipper.LOG.debug("[Zipper] [Download Thread] Downloading dependencies...");
            try {
                threads.await();
            } catch (final InterruptedException e) {
                Zipper.LOG.error("[Zipper] [Download Thread] Dependencies download FAILURE; ", e);
                Thread.currentThread().interrupt();
                return;
            }
            final long downloadTime = System.currentTimeMillis() - startTime;

            // Report successful download
            String report = "[Zipper] [Download Thread] Dependencies download SUCCESS; Took " + downloadTime
                    + " ms; Downloaded: [ ";
            for (final DependencyInfo target : DownloadManager.getDownloaded()) {
                report += target.fileName() + ", ";
            }
            report = (report + "]").replace(", ]", " ]");
            Zipper.LOG.debug(report);
        }
    }
}
