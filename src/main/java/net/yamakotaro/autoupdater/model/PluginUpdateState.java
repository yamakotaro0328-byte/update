package net.yamakotaro.autoupdater.model;

import net.yamakotaro.autoupdater.version.Version;

import java.time.Instant;

/**
 * プラグイン1つ分の実行時状態。UpdateManager がキャッシュとして保持する。
 */
public final class PluginUpdateState {

    private final ManagedPlugin plugin;
    private volatile Version currentVersion;
    private volatile UpdateInfo latestInfo;
    private volatile UpdateStatus status = UpdateStatus.UNKNOWN;
    private volatile String lastError;
    private volatile Instant lastChecked;

    public PluginUpdateState(ManagedPlugin plugin, Version currentVersion) {
        this.plugin = plugin;
        this.currentVersion = currentVersion;
    }

    public ManagedPlugin getPlugin() { return plugin; }
    public Version getCurrentVersion() { return currentVersion; }
    public void setCurrentVersion(Version currentVersion) { this.currentVersion = currentVersion; }
    public UpdateInfo getLatestInfo() { return latestInfo; }
    public void setLatestInfo(UpdateInfo latestInfo) { this.latestInfo = latestInfo; }
    public UpdateStatus getStatus() { return status; }
    public void setStatus(UpdateStatus status) { this.status = status; }
    public String getLastError() { return lastError; }
    public void setLastError(String lastError) { this.lastError = lastError; }
    public Instant getLastChecked() { return lastChecked; }
    public void setLastChecked(Instant lastChecked) { this.lastChecked = lastChecked; }
}
