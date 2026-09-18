package net.yamakotaro.autoupdater.backup;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

/**
 * 更新前の jar を plugins/backups/ に退避し、必要ならロールバックできるようにする。
 */
public final class BackupManager {

    private static final DateTimeFormatter TS = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");

    private final Path backupsDir;
    private final int keepCount;

    public BackupManager(Path backupsDir, int keepCount) {
        this.backupsDir = backupsDir;
        this.keepCount = Math.max(1, keepCount);
    }

    /** 現在稼働中の jar をバックアップする。存在しない場合は何もしない。 */
    public Path backup(String pluginName, String currentVersionRaw, Path currentJarFile) throws IOException {
        if (currentJarFile == null || !Files.isRegularFile(currentJarFile)) {
            return null;
        }
        Files.createDirectories(backupsDir);
        String safeVersion = currentVersionRaw == null ? "unknown" : currentVersionRaw.replaceAll("[^A-Za-z0-9._-]", "_");
        String fileName = pluginName + "-" + safeVersion + "-" + java.time.LocalDateTime.now().format(TS) + ".jar";
        Path dest = backupsDir.resolve(fileName);
        Files.copy(currentJarFile, dest, StandardCopyOption.REPLACE_EXISTING);
        prune(pluginName);
        return dest;
    }

    /** 指定プラグインの最新バックアップを取得する (無ければ null)。 */
    public Path latestBackup(String pluginName) {
        List<Path> backups = listBackups(pluginName);
        return backups.isEmpty() ? null : backups.get(0);
    }

    private List<Path> listBackups(String pluginName) {
        if (!Files.isDirectory(backupsDir)) return List.of();
        try (Stream<Path> s = Files.list(backupsDir)) {
            return s.filter(p -> p.getFileName().toString().startsWith(pluginName + "-"))
                    .sorted(Comparator.comparingLong((Path p) -> {
                        try {
                            return Files.getLastModifiedTime(p).toMillis();
                        } catch (IOException e) {
                            return 0L;
                        }
                    }).reversed())
                    .toList();
        } catch (IOException e) {
            return List.of();
        }
    }

    private void prune(String pluginName) {
        List<Path> backups = listBackups(pluginName);
        for (int i = keepCount; i < backups.size(); i++) {
            try {
                Files.deleteIfExists(backups.get(i));
            } catch (IOException ignored) {
            }
        }
    }
}
