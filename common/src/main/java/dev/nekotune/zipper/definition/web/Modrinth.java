package dev.nekotune.zipper.definition.web;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import dev.nekotune.zipper.Zipper;
import dev.nekotune.zipper.definition.DependencyInfo;
import dev.nekotune.zipper.definition.DependencyInfo.Host;
import dev.nekotune.zipper.definition.web.api.ModrinthAPI.*;
import dev.nekotune.zipper.definition.web.api.WebAPI.APIResponse;
import net.minecraft.server.packs.PackType;

public final class Modrinth extends WebHost {

    public static final Modrinth INSTANCE = new Modrinth();

    private Modrinth() {
    }

    @Override
    protected Host hostType() {
        return DependencyInfo.Host.MODRINTH;
    }

    @Override
    protected APIResponse<Path> GET(final String slug,
            final PackType packType, final Path downloadTo) throws IOException, InterruptedException,
            SecurityException {
        final APIResponse<String> fileUrl = resolveFileURL(slug, packType);
        Zipper.LOG
                .debug("[WebFetch$Modrinth] Queried file URL, got " + fileUrl.statusCode() + ": " + fileUrl.body());
        if (fileUrl.statusCode() != 200) {
            return new APIResponse<>(fileUrl.statusCode(), null);
        }
        return Endpoints.GET.download(fileUrl.body(), downloadTo);
    }

    private APIResponse<String> resolveFileURL(final String slug, final PackType packType)
            throws IOException, InterruptedException, SecurityException {

        // Fetch a list of version URLs
        final APIResponse<Versions> response = Endpoints.GET.versions(slug);
        if (response.statusCode() != 200) {
            return new APIResponse<>(response.statusCode(), "");
        }
        final List<Version> versions = response.body().stream()
                .filter(version -> versionLoadsFor(version, packType))
                .toList();
        if (versions.isEmpty()) {
            Zipper.LOG.debug("[WebFetch$Modrinth] Versions came up empty for slug \"" + slug + "\" with pack type "
                    + packType.toString());
            return new APIResponse<>(404, "");
        }

        // Return the first (newest) file for the running game version
        for (final Version version : versions) {
            if (!hasRunningGameVersion(version))
                continue;
            for (final File file : version.files()) {
                return new APIResponse<>(200, file.url());
            }
        }
        Zipper.LOG.debug("[WebFetch$Modrinth] File for minecraft "
                + Arrays.deepToString(Zipper.MC_VERSIONS)
                + " not found at slug "
                + slug
                + "; using primary file.");

        // Return primary version
        for (final File file : allFiles(versions, packType)) {
            if (file.primary()) {
                return new APIResponse<>(200, file.url());
            }
        }
        Zipper.LOG.debug("[WebFetch$Modrinth] No primary file found at slug "
                + slug
                + "; using latest file.");

        // Return the first valid version
        for (final File file : allFiles(versions, packType)) {
            return new APIResponse<>(200, file.url());
        }
        Zipper.LOG.debug("[WebFetch$Modrinth] No files found at slug " + slug);

        // No valid versions found
        return new APIResponse<>(404, "");
    }

    private boolean versionLoadsFor(final Version version, final PackType packType) {
        for (final Version.Loader loader : version.loaders()) {
            switch (loader) {
                case datapack:
                    if (packType == PackType.SERVER_DATA) {
                        return true;
                    }
                    break;
                case minecraft:
                    if (packType == PackType.CLIENT_RESOURCES) {
                        return true;
                    }
                    break;
                default:
                    break;
            }
        }
        return false;
    }

    private boolean hasRunningGameVersion(final Version version) {
        for (final String gameVersion : version.game_versions()) {
            for (final String mcVersion : Zipper.MC_VERSIONS) {
                if (gameVersion.equals(mcVersion))
                    return true;
            }
        }
        return false;
    }

    private List<File> allFiles(final List<Version> versions, final PackType packType) {
        final List<File> files = new ArrayList<>();
        for (final Version version : versions) {
            if (!versionLoadsFor(version, packType))
                continue;
            files.addAll(version.files());
        }
        return files;
    }
}
