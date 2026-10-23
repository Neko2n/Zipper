package dev.nekotune.zipper.definition.web.api;

import java.io.IOException;
import java.net.http.HttpResponse.BodyHandlers;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.annotations.SerializedName;

import dev.nekotune.zipper.Zipper;

/**
 * A portion of the Modrinth developer API translated into Java.
 * This does not contain the full Modrinth API; it only contains what's needed
 * for the mod to work.
 */
public interface ModrinthAPI extends WebAPI {

    public static final String VERSIONS_URL = "https://api.modrinth.com/v2/project/%s/version";

    public static final Gson GSON = new GsonBuilder().create();

    public static interface Endpoints {

        public static final String[] HEADERS = new String[] {
                "User-Agent", "dev.nekotune." + Zipper.MOD_ID
                        + " (https://github.com/Neko2n/Zipper)"
        };

        public static interface GET {

            public static APIResponse<Versions> versions(final String slug)
                    throws IOException, InterruptedException, SecurityException {
                final String url = ModrinthAPI.VERSIONS_URL.formatted(slug);
                final APIResponse<String> response = WebAPI.request(url, ModrinthAPI.Endpoints.HEADERS,
                        BodyHandlers.ofString());
                if (response.statusCode() != 200)
                    return new APIResponse<>(response.statusCode(), null);
                return new APIResponse<>(200, GSON.fromJson(response.body(), Versions.class));
            }

            public static APIResponse<Path> download(final String fileUrl, final Path downloadTo)
                    throws IOException, InterruptedException, SecurityException {
                return WebAPI.download(fileUrl, Endpoints.HEADERS, downloadTo);
            }
        }

    }

    public static class Versions extends HashSet<Version> {}

    public static record Version(
            String name,
            String version_number,
            Set<Dependency> dependencies,
            Set<String> game_versions,
            Version.VersionType version_type,
            Set<Version.Loader> loaders,
            boolean featured,
            Version.Status status,
            Version.RequestedStatus requested_status,
            String id,
            String project_id,
            String author_id,
            String date_published,
            int downloads,
            String changelog_url,
            Set<File> files) {

        public static enum RequestedStatus {
            listed,
            archived,
            draft,
            unlisted;
        }

        public static enum Status {
            listed,
            archived,
            draft,
            unlisted,
            scheduled,
            unknown;
        }

        public static enum VersionType {
            release,
            beta,
            alpha;
        }

        public static enum Loader {
            datapack,
            minecraft,
            fabric,
            forge,
            neoforge,
            quilt;
        }

        public Version {
            loaders = new HashSet<>(loaders.stream().filter(v -> v != null).toList());
        }
    }

    public static record Dependency(
            String version_id,
            String project_id,
            String file_name,
            Dependency.DependencyType dependency_type) {

        public static enum DependencyType {
            required,
            optional,
            incompatible,
            embedded;
        }
    }

    public static record File(
            File.Hashes hashes,
            String url,
            String filename,
            boolean primary,
            int size,
            File.FileType file_type) {

        public static enum FileType {
        
            @SerializedName("required-resource-pack")
            required_resource_pack,
        
            @SerializedName("optional-resource-pack")
            optional_resource_pack,
        
            @SerializedName("sources-jar")
            sources_jar,
        
            @SerializedName("dev-jar")
            dev_jar,
        
            @SerializedName("javadoc-jar")
            javadoc_jar,
        
            unknown,
            signature;
        }

        public File {
            file_type = file_type != null ? file_type : File.FileType.unknown;
        }

        public static record Hashes(
                String sha512,
                String sha1) {
        }
    }
}
