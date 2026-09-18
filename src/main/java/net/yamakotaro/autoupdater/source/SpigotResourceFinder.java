package net.yamakotaro.autoupdater.source;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.yamakotaro.autoupdater.http.Http;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;

/**
 * SpigotMC のリソースIDはプラグイン名から規則的に推測できないため、
 * Spiget の検索 API でプラグイン名と完全一致 (記号・大小文字を無視) するリソースを探す。
 * 複数該当・該当無しの場合は諦めて -1 を返す (誤検出よりも未検出を優先する)。
 */
public final class SpigotResourceFinder {

    private static final String USER_AGENT = "AutoUpdater-Plugin";
    private static final String API_BASE = "https://api.spiget.org/v2";

    private SpigotResourceFinder() {}

    public static CompletableFuture<Integer> findResourceId(String pluginName) {
        String query = URLEncoder.encode(pluginName, StandardCharsets.UTF_8);
        String url = API_BASE + "/search/resources/" + query + "?field=name&size=20";

        return Http.getText(url, USER_AGENT).thenApply(response -> {
            if (response.statusCode() != 200) return -1;
            try {
                JsonArray results = JsonParser.parseString(response.body()).getAsJsonArray();
                String target = normalize(pluginName);
                Integer match = null;
                for (JsonElement el : results) {
                    JsonObject obj = el.getAsJsonObject();
                    if (!obj.has("id") || !obj.has("name")) continue;
                    if (normalize(obj.get("name").getAsString()).equals(target)) {
                        if (match != null) return -1; // 複数一致は誤検出のリスクが高いので採用しない
                        match = obj.get("id").getAsInt();
                    }
                }
                return match == null ? -1 : match;
            } catch (Exception e) {
                return -1;
            }
        }).exceptionally(e -> -1);
    }

    private static String normalize(String s) {
        return s.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "");
    }
}
