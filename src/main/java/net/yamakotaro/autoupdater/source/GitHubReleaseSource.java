package net.yamakotaro.autoupdater.source;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.yamakotaro.autoupdater.http.Http;
import net.yamakotaro.autoupdater.model.ManagedPlugin;
import net.yamakotaro.autoupdater.model.UpdateInfo;
import net.yamakotaro.autoupdater.version.Version;

import java.net.http.HttpResponse;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * GitHub Releases から最新版を取得する。
 * リリース本文に以下のような記述があれば、対応 Minecraft バージョンとして解釈する:
 *   minecraft:
 *     min: "26.2"
 *     max: "26.2"
 * また "SHA256: <hex>" のような記述、または "<jar名>.sha256" という名前の
 * 追加アセットがあればハッシュ検証に使用する。
 */
public final class GitHubReleaseSource implements UpdateSource {

    private static final String USER_AGENT = "AutoUpdater-Plugin";
    private static final Pattern MC_MIN = Pattern.compile("min:\\s*\"?([0-9A-Za-z.\\-]+)\"?");
    private static final Pattern MC_MAX = Pattern.compile("max:\\s*\"?([0-9A-Za-z.\\-]+)\"?");
    private static final Pattern SHA256_IN_BODY =
            Pattern.compile("sha-?256[:\\s]*([a-fA-F0-9]{64})", Pattern.CASE_INSENSITIVE);

    private final String apiBaseUrl;

    public GitHubReleaseSource(String apiBaseUrl) {
        this.apiBaseUrl = apiBaseUrl;
    }

    @Override
    public java.util.concurrent.CompletableFuture<UpdateInfo> fetchLatest(ManagedPlugin plugin) {
        String repo = plugin.getRepository();
        if (repo == null || repo.isBlank() || !repo.contains("/")) {
            return failed("GitHub repository が設定されていません (owner/repo 形式で指定してください)");
        }
        String url = apiBaseUrl + "/repos/" + repo + "/releases/latest";

        return Http.getText(url, USER_AGENT).thenCompose(response -> {
            if (response.statusCode() == 404) {
                return failed("リリースが見つかりません: " + repo);
            }
            if (response.statusCode() != 200) {
                return failed("GitHub API エラー (HTTP " + response.statusCode() + "): " + repo);
            }
            try {
                return parse(plugin, response);
            } catch (Exception e) {
                return failed("GitHub リリース情報の解析に失敗しました: " + e.getMessage());
            }
        });
    }

    private java.util.concurrent.CompletableFuture<UpdateInfo> parse(ManagedPlugin plugin,
                                                                       HttpResponse<String> response) {
        JsonObject root = JsonParser.parseString(response.body()).getAsJsonObject();
        String tag = getAsStringOrNull(root, "tag_name");
        if (tag == null) {
            return failed("tag_name が見つかりません");
        }
        Version version = Version.parse(tag);
        String body = getAsStringOrNull(root, "body");
        if (body == null) body = "";

        String minecraftMin = extract(MC_MIN, body);
        String minecraftMax = extract(MC_MAX, body);

        JsonArray assets = root.has("assets") && root.get("assets").isJsonArray()
                ? root.getAsJsonArray("assets") : new JsonArray();

        JsonObject jarAsset = null;
        String pattern = plugin.getAssetPattern();
        for (JsonElement el : assets) {
            JsonObject asset = el.getAsJsonObject();
            String name = getAsStringOrNull(asset, "name");
            if (name == null || !name.toLowerCase().endsWith(".jar")) continue;
            if (name.toLowerCase().contains("sources") || name.toLowerCase().contains("javadoc")) continue;
            if (pattern != null && !pattern.isBlank()) {
                if (matchesPattern(name, pattern)) {
                    jarAsset = asset;
                    break;
                }
            } else if (jarAsset == null) {
                jarAsset = asset;
            }
        }

        if (jarAsset == null) {
            return failed("リリース内に jar ファイルが見つかりません: " + plugin.getRepository());
        }

        String downloadUrl = getAsStringOrNull(jarAsset, "browser_download_url");
        long size = jarAsset.has("size") ? jarAsset.get("size").getAsLong() : -1;
        String jarName = getAsStringOrNull(jarAsset, "name");

        String sha256 = extract(SHA256_IN_BODY, body);
        if (sha256 == null && jarName != null) {
            for (JsonElement el : assets) {
                JsonObject asset = el.getAsJsonObject();
                String name = getAsStringOrNull(asset, "name");
                if (name != null && name.equalsIgnoreCase(jarName + ".sha256")) {
                    String hashUrl = getAsStringOrNull(asset, "browser_download_url");
                    // 小さなテキストファイルなので同期的に取得して問題ない (呼び出し元は非同期実行前提)
                    sha256 = fetchHashTextBestEffort(hashUrl);
                    break;
                }
            }
        }

        String infoPage = getAsStringOrNull(root, "html_url");

        return java.util.concurrent.CompletableFuture.completedFuture(
                new UpdateInfo(version, downloadUrl, size, sha256, "SHA-256",
                        minecraftMin, minecraftMax, infoPage, true));
    }

    private String fetchHashTextBestEffort(String hashUrl) {
        if (hashUrl == null) return null;
        try {
            HttpResponse<String> resp = Http.getText(hashUrl, USER_AGENT).get();
            if (resp.statusCode() == 200) {
                Matcher m = Pattern.compile("([a-fA-F0-9]{64})").matcher(resp.body());
                if (m.find()) return m.group(1);
            }
        } catch (Exception ignored) {
            // ハッシュ取得に失敗しても致命的ではない (require-hash 設定次第で後段が判断する)
        }
        return null;
    }

    private boolean matchesPattern(String name, String pattern) {
        String regex = Pattern.quote(pattern).replace("*", "\\E.*\\Q");
        return name.toLowerCase().contains(pattern.toLowerCase().replace("*", ""))
                || java.util.regex.Pattern.matches(regex, name);
    }

    private static String extract(Pattern p, String text) {
        Matcher m = p.matcher(text);
        return m.find() ? m.group(1) : null;
    }

    private static String getAsStringOrNull(JsonObject obj, String key) {
        if (!obj.has(key) || obj.get(key).isJsonNull()) return null;
        return obj.get(key).getAsString();
    }

    private static java.util.concurrent.CompletableFuture<UpdateInfo> failed(String message) {
        java.util.concurrent.CompletableFuture<UpdateInfo> f = new java.util.concurrent.CompletableFuture<>();
        f.completeExceptionally(new IllegalStateException(message));
        return f;
    }
}
