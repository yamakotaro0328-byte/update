package net.yamakotaro.autoupdater.model;

import net.yamakotaro.autoupdater.version.Version;

/**
 * 配布元から取得した「最新版」の情報。
 */
public final class UpdateInfo {

    private final Version version;
    private final String downloadUrl;
    private final long fileSize; // 不明な場合は -1
    private final String expectedHash; // 不明な場合は null
    private final String hashAlgorithm; // "SHA-256" / "SHA-512" / "SHA-1" (expectedHash が null なら無視)
    private final String minecraftMin; // 不明な場合は null
    private final String minecraftMax; // 不明な場合は null
    private final String infoPageUrl; // ブラウザで見れる配布ページ (通知用、null 可)
    private final boolean directDownloadSupported; // false の場合は自動ダウンロード不可 (通知のみ)

    public UpdateInfo(Version version, String downloadUrl, long fileSize, String expectedHash,
                       String hashAlgorithm, String minecraftMin, String minecraftMax, String infoPageUrl,
                       boolean directDownloadSupported) {
        this.version = version;
        this.downloadUrl = downloadUrl;
        this.fileSize = fileSize;
        this.expectedHash = expectedHash;
        this.hashAlgorithm = hashAlgorithm;
        this.minecraftMin = minecraftMin;
        this.minecraftMax = minecraftMax;
        this.infoPageUrl = infoPageUrl;
        this.directDownloadSupported = directDownloadSupported;
    }

    public Version getVersion() { return version; }
    public String getDownloadUrl() { return downloadUrl; }
    public long getFileSize() { return fileSize; }
    public String getExpectedHash() { return expectedHash; }
    public String getHashAlgorithm() { return hashAlgorithm == null ? "SHA-256" : hashAlgorithm; }
    public String getMinecraftMin() { return minecraftMin; }
    public String getMinecraftMax() { return minecraftMax; }
    public String getInfoPageUrl() { return infoPageUrl; }
    public boolean isDirectDownloadSupported() { return directDownloadSupported; }
}
