package net.yamakotaro.autoupdater.source;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.yamakotaro.autoupdater.http.Http;
import net.yamakotaro.autoupdater.model.ManagedPlugin;
import net.yamakotaro.autoupdater.model.UpdateInfo;
import net.yamakotaro.autoupdater.version.Version;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CompletableFuture;

/**
 * Modrinth (一般に配布されているプラグインの主要な配布先の一つ) から最新版を取得する。
 * ハッシュは Modrinth が SHA-512 を提供するのでそれを利用する。
 */
public final class ModrinthSource implements UpdateSource {

    private static final String USER_AGENT = "AutoUpdater-Plugin";
    private static final String API_BASE = "https://api.modrinth.com/v2";

    @Override
    public CompletableFuture<UpdateInfo> fetchLatest(ManagedPlugin plugin) {
        String project = plugin.getModrinthProject();
        if (project == null || project.isBlank()) {
            return failed("Modrinth project が設定されていません");
        }

        StringBuilder url = new StringBuilder(API_BASE).append("/project/")
                .append(encode(project)).append("/version?");
        if (plugin.getModrinthLoader() != null && !plugin.getModrinthLoader().isBlank()) {
            url.append("loaders=").append(encode("[\"" + plugin.getModrinthLoader() + "\"]")).append("&");
        }
        if (plugin.getModrinthGameVersion() != null && !plugin.getModrinthGameVersion().isBlank()) {
            url.append("game_versions=").append(encode("[\"" + plugin.getModrinthGameVersion() + "\"]"));
        }

        return Http.getText(url.toString(), USER_AGENT).thenCompose(response -> {
            if (response.statusCode() == 404) {
                return failed("Modrinth プロジェクトが見つかりません: " + project);
            }
            if (response.statusCode() != 200) {
                return failed("Modrinth API エラー (HTTP " + response.statusCode() + "): " + project);
            }
            try {
                JsonElement parsed = JsonParser.parseString(response.body());
                JsonArray versions = parsed.getAsJsonArray();
                if (versions.isEmpty()) {
                    return failed("対応するバージョンが見つかりません: " + project);
                }
                JsonObject latest = versions.get(0).getAsJsonObject();
                String versionNumber = latest.get("version_number").getAsString();
                Version version = Version.parse(versionNumber);

                JsonArray files = latest.getAsJsonArray("files");
                JsonObject primaryFile = null;
                for (JsonElement fEl : files) {
                    JsonObject f = fEl.getAsJsonObject();
                    if (f.has("primary") && f.get("primary").getAsBoolean()) {
                        primaryFile = f;
                        break;
                    }
                }
                if (primaryFile == null && !files.isEmpty()) {
                    primaryFile = files.get(0).getAsJsonObject();
                }
                if (primaryFile == null) {
                    return failed("ダウンロード可能なファイルが見つかりません: " + project);
                }

                String downloadUrl = primaryFile.get("url").getAsString();
                long size = primaryFile.has("size") ? primaryFile.get("size").getAsLong() : -1;
                String hash = null;
                if (primaryFile.has("hashes") && primaryFile.get("hashes").isJsonObject()) {
                    JsonObject hashes = primaryFile.getAsJsonObject("hashes");
                    if (hashes.has("sha512")) hash = hashes.get("sha512").getAsString();
                    else if (hashes.has("sha1")) hash = hashes.get("sha1").getAsString();
                }
                String hashAlgo = hash != null && hash.length() == 128 ? "SHA-512" : "SHA-1";

                String infoPage = "https://modrinth.com/plugin/" + project + "/version/" + versionNumber;

                return CompletableFuture.completedFuture(
                        new UpdateInfo(version, downloadUrl, size, hash, hashAlgo, null, null, infoPage, true));
            } catch (Exception e) {
                return failed("Modrinth レスポンスの解析に失敗しました: " + e.getMessage());
            }
        });
    }

    private static String encode(String s) {
        return URLEncoder.encode(s, StandardCharsets.UTF_8);
    }

    private static CompletableFuture<UpdateInfo> failed(String message) {
        CompletableFuture<UpdateInfo> f = new CompletableFuture<>();
        f.completeExceptionally(new IllegalStateException(message));
        return f;
    }
}
