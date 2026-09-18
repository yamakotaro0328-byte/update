package net.yamakotaro.autoupdater.model;

/**
 * 管理対象プラグイン1つに対する現在のアップデート状況。
 */
public enum UpdateStatus {
    /** まだ一度もチェックしていない */
    UNKNOWN,
    /** チェック中 */
    CHECKING,
    /** 最新版を使用中 */
    UP_TO_DATE,
    /** 新しいバージョンが利用可能 */
    UPDATE_AVAILABLE,
    /** 新しいバージョンをダウンロード中 */
    DOWNLOADING,
    /** ダウンロード済み、次回サーバー起動時に適用される */
    DOWNLOADED,
    /** 配布側が現在のサーバーの Minecraft バージョンに対応していない */
    INCOMPATIBLE,
    /** サーバーにインストールされていない、または plugins.yml の設定に誤りがある */
    NOT_INSTALLED,
    /** 無効化されている (plugins.yml で enabled: false) */
    DISABLED,
    /** チェックまたはダウンロード中にエラーが発生した */
    ERROR
}
