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
 * Spiget の検索 API (プラグイン名と関連度順にソートされる) から候補を探す。
 * 誤ったプラグインを自動ダウンロードしてしまうリスクを避けるため、
 * 名前が十分近いと判断できる場合のみ採用し、それ以外は諦めて -1 を返す。
 *
 * 優先順位:
 *   1. 完全一致 (記号・大小文字を無視して比較)
 *   2. リソース名がプラグイン名で始まる (例: プラグイン名 "EssentialsX" に対し
 *      リソース名 "EssentialsX - Unlock the essentials for your server!" 等)
 *      検索結果は関連度順なので、この条件に最初に合致したものを採用する
 */
public final class SpigotResourceFinder {

    private static final String USER_AGENT = "AutoUpdater-Plugin";
    private static final String API_BASE = "https://api.spiget.org/v2";
    private static final int MIN_PREFIX_MATCH_LENGTH = 4;

    private SpigotResourceFinder() {}

    public static CompletableFuture<Integer> findResourceId(String pluginName) {
        String query = URLEncoder.encode(pluginName, StandardCharsets.UTF_8);
        String url = API_BASE + "/search/resources/" + query + "?field=name&size=20";

        return Http.getText(url, USER_AGENT).thenApply(response -> {
            if (response.statusCode() != 200) return -1;
            try {
                return pickBestMatch(pluginName, JsonParser.parseString(response.body()).getAsJsonArray());
            } catch (Exception e) {
                return -1;
            }
        }).exceptionally(e -> -1);
    }

    private static int pickBestMatch(String pluginName, JsonArray results) {
        String target = normalize(pluginName);
        Integer firstExact = null;
        Integer firstPrefix = null;

        for (JsonElement el : results) {
            JsonObject obj = el.getAsJsonObject();
            if (!obj.has("id") || !obj.has("name")) continue;
            String normalizedName = normalize(obj.get("name").getAsString());
            int id = obj.get("id").getAsInt();

            if (normalizedName.equals(target)) {
                if (firstExact == null) firstExact = id;
            } else if (firstPrefix == null
                    && target.length() >= MIN_PREFIX_MATCH_LENGTH
                    && normalizedName.startsWith(target)) {
                firstPrefix = id;
            }
        }

        if (firstExact != null) return firstExact;
        if (firstPrefix != null) return firstPrefix;
        return -1;
    }

    private static String normalize(String s) {
        return s.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "");
    }
}
