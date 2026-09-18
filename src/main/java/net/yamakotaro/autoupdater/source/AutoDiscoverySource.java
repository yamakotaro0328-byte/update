package net.yamakotaro.autoupdater.source;

import net.yamakotaro.autoupdater.model.ManagedPlugin;
import net.yamakotaro.autoupdater.model.UpdateInfo;

import java.util.Locale;
import java.util.concurrent.CompletableFuture;

/**
 * plugins.yml に登録されておらず、プラグイン自身の plugin.yml にも
 * autoupdate 情報が無いプラグイン用の最終手段。
 * プラグイン名から Modrinth の slug を推測して直接プロジェクトを探す
 * (例: "EssentialsX" -> "essentialsx")。見つからない場合は検出不可として扱う。
 */
public final class AutoDiscoverySource implements UpdateSource {

    private final ModrinthSource modrinthSource;

    public AutoDiscoverySource(ModrinthSource modrinthSource) {
        this.modrinthSource = modrinthSource;
    }

    @Override
    public CompletableFuture<UpdateInfo> fetchLatest(ManagedPlugin plugin) {
        String guessedSlug = plugin.getName().toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "-");
        ManagedPlugin guess = new ManagedPlugin(plugin.getName(), true, UpdateSourceType.MODRINTH,
                null, null, guessedSlug, "paper", "", -1);

        return modrinthSource.fetchLatest(guess).handle((info, ex) -> {
            if (ex != null) {
                throw new java.util.concurrent.CompletionException(new IllegalStateException(
                        "更新元を自動検出できませんでした (Modrinth slug 候補: " + guessedSlug + ")。"
                                + " plugins.yml で手動登録するか、プラグインの plugin.yml に autoupdate セクションを追加してください。"));
            }
            return info;
        });
    }
}
