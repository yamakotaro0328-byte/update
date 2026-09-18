package net.yamakotaro.autoupdater.source;

import net.yamakotaro.autoupdater.model.ManagedPlugin;
import net.yamakotaro.autoupdater.model.UpdateInfo;

import java.util.concurrent.CompletableFuture;

/**
 * 更新元 (GitHub Releases / Modrinth / SpigotMC / 独自サーバー) の共通インターフェース。
 * 独自アップデートサーバーを追加する場合はこれを実装すればよい。
 */
public interface UpdateSource {

    /**
     * 最新版の情報を取得する。見つからない・失敗した場合は例外で completeExceptionally する。
     */
    CompletableFuture<UpdateInfo> fetchLatest(ManagedPlugin plugin);
}
