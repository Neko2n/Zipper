package dev.nekotune.zipper.definition.web;

import java.io.IOException;
import java.nio.file.Path;

import dev.nekotune.zipper.Zipper;
import dev.nekotune.zipper.definition.DependencyInfo;
import dev.nekotune.zipper.definition.DependencyInfo.Host;
import dev.nekotune.zipper.definition.web.api.CurseforgeAPI.*;
import dev.nekotune.zipper.definition.web.api.WebAPI.APIResponse;
import net.minecraft.server.packs.PackType;

public class Curseforge extends WebHost {

    public static final Curseforge INSTANCE = new Curseforge();

    @Override
    protected Host hostType() {
        return DependencyInfo.Host.CURSEFORGE;
    }

    @Override
    public APIResponse<Path> GET(final String slug, final PackType packType, final Path downloadTo)
            throws IOException, InterruptedException, SecurityException {
        final var file = resolveFileId(slug, packType);
        if (file.statusCode() != 200) {
            return new APIResponse<>(file.statusCode(), null);
        }
        return Endpoints.GET.mods.download(file.body().downloadUrl(), downloadTo);
    }

    private APIResponse<Responses.GET.mods.File> resolveFileId(final String slug, final PackType packType)
            throws IOException, InterruptedException, SecurityException {

        // Get the mod project data
        final var response = Endpoints.GET.mods.search(slug);
        if (response.statusCode() != 200) {
            return new APIResponse<>(response.statusCode(), null);
        }
        final var mods = response.body().data().stream()
                .filter(mod -> mod.matches(packType))
                .toList();
        if (mods.isEmpty()) {
            return new APIResponse<>(404, null);
        }
        final var mod = mods.getFirst(); // There's only ever going to be one
        if (!(mod.isAvailable() && mod.allowModDistribution())) {
            return new APIResponse<>(404, null);
        }

        // Filter for valid files
        final var files = mod.latestFiles().stream()
                .filter(Responses.GET.mods.File::isAvailable)
                .toList();
        if (files.isEmpty()) {
            return new APIResponse<>(404, null);
        }

        // Return the first (newest) file for the running game version
        final var matchesGameVersion = files.stream()
                .filter(file -> {
                    for (final String fileVersion : file.gameVersions()) {
                        for (final String mcVersion : Zipper.MC_VERSIONS) {
                            if (fileVersion.matches(mcVersion))
                                return true;
                        }
                    }
                    return false;
                }).toList();
        if (!matchesGameVersion.isEmpty()) {
            return new APIResponse<>(200, matchesGameVersion.getFirst());
        }

        // Fallback: return main file
        for (final var file : files) {
            if (file.id() == mod.mainFileId())
                return new APIResponse<>(200, file);
        }
        return new APIResponse<>(200, files.getFirst());
    }
}
