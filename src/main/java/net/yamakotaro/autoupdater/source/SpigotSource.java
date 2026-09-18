package net.yamakotaro.autoupdater.source;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.yamakotaro.autoupdater.http.Http;
import net.yamakotaro.autoupdater.model.ManagedPlugin;
import net.yamakotaro.autoupdater.model.UpdateInfo;
import net.yamakotaro.autoupdater.version.Version;

import java.util.concurrent.CompletableFuture;

/**
 * SpigotMC は公式ダウンロード API を提供していないため、
 * 広く使われているミラー API である Spiget (api.spiget.org) 経由でバージョン情報を取得する。
 * リソースが外部ダウンロードを許可していない場合は自動ダウンロードできないため、
 * その場合は directDownloadSupported=false とし、通知のみ行う。
 */
public final class SpigotSource implements UpdateSource {

    private static final String USER_AGENT = "AutoUpdater-Plugin";
    private static final String API_BASE = "https://api.spiget.org/v2";

    @Override
    public CompletableFuture<UpdateInfo> fetchLatest(ManagedPlugin plugin) {
        int resourceId = plugin.getSpigotResourceId();
        if (resourceId <= 0) {
            return failed("Spigot resource-id が設定されていません");
        }

        String versionUrl = API_BASE + "/resources/" + resourceId + "/versions/latest";
        return Http.getText(versionUrl, USER_AGENT).thenCompose(response -> {
            if (response.statusCode() != 200) {
                return failed("Spiget API エラー (HTTP " + response.statusCode() + "): resource " + resourceId);
            }
            try {
                JsonObject json = JsonParser.parseString(response.body()).getAsJsonObject();
                String name = json.has("name") && !json.get("name").isJsonNull()
                        ? json.get("name").getAsString() : "0";
                Version version = Version.parse(name);

                String downloadUrl = API_BASE + "/resources/" + resourceId + "/download";
                String infoPage = "https://www.spigotmc.org/resources/" + resourceId;

                // Spiget はこの時点では実ダウンロード可否を保証しないため、
                // 実際の可否は JarDownloader 側のレスポンスコード (403 等) で最終判断する。
                return CompletableFuture.completedFuture(
                        new UpdateInfo(version, downloadUrl, -1, null, null, null, null, infoPage, true));
            } catch (Exception e) {
                return failed("Spiget レスポンスの解析に失敗しました: " + e.getMessage());
            }
        });
    }

    private static CompletableFuture<UpdateInfo> failed(String message) {
        CompletableFuture<UpdateInfo> f = new CompletableFuture<>();
        f.completeExceptionally(new IllegalStateException(message));
        return f;
    }
}
