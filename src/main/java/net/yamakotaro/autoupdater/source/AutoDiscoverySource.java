package net.yamakotaro.autoupdater.source;

import net.yamakotaro.autoupdater.model.ManagedPlugin;
import net.yamakotaro.autoupdater.model.UpdateInfo;

import java.util.Locale;
import java.util.concurrent.CompletableFuture;

/**
 * plugins.yml に登録されておらず、プラグイン自身の plugin.yml にも
 * autoupdate 情報が無いプラグイン用の最終手段。
 *   1. プラグイン名から Modrinth の slug を推測 (例: "EssentialsX" -> "essentialsx")
 *   2. 見つからなければ Spiget の検索 API で SpigotMC のリソースIDを名前一致で推測
 * どちらも見つからない場合は検出不可として扱う (誤検出よりも未検出を優先する)。
 */
public final class AutoDiscoverySource implements UpdateSource {

    private final ModrinthSource modrinthSource;
    private final SpigotSource spigotSource;

    public AutoDiscoverySource(ModrinthSource modrinthSource, SpigotSource spigotSource) {
        this.modrinthSource = modrinthSource;
        this.spigotSource = spigotSource;
    }

    @Override
    public CompletableFuture<UpdateInfo> fetchLatest(ManagedPlugin plugin) {
        String guessedSlug = plugin.getName().toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "-");
        ManagedPlugin modrinthGuess = new ManagedPlugin(plugin.getName(), true, UpdateSourceType.MODRINTH,
                null, null, guessedSlug, "paper", "", -1);

        return modrinthSource.fetchLatest(modrinthGuess)
                .handle((info, ex) -> info) // Modrinth で失敗しても後段の Spigot 検索に委ねる
                .thenCompose(info -> info != null
                        ? CompletableFuture.completedFuture(info)
                        : trySpigot(plugin));
    }

    private CompletableFuture<UpdateInfo> trySpigot(ManagedPlugin plugin) {
        return SpigotResourceFinder.findResourceId(plugin.getName()).thenCompose(id -> {
            if (id == null || id <= 0) {
                CompletableFuture<UpdateInfo> failed = new CompletableFuture<>();
                failed.completeExceptionally(new IllegalStateException(
                        "更新元を自動検出できませんでした (Modrinth / SpigotMC いずれにも一致なし)。"
                                + " plugins.yml で手動登録するか、プラグインの plugin.yml に autoupdate セクションを追加してください。"));
                return failed;
            }
            ManagedPlugin spigotGuess = new ManagedPlugin(plugin.getName(), true, UpdateSourceType.SPIGOT,
                    null, null, null, null, "", id);
            return spigotSource.fetchLatest(spigotGuess);
        });
    }
}
