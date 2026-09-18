package net.yamakotaro.autoupdater.http;

import net.yamakotaro.autoupdater.security.PackageVerifier;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;

/**
 * URL からファイルを安全にダウンロードする。
 * - HTTPS のみ許可
 * - タイムアウト設定
 * - ファイルサイズ上限を Content-Length と実ストリーム両方でチェック
 * - ストリーミング中にハッシュを計算 (二度読みしない)
 * - 失敗時は一時ファイルを必ず削除する
 */
public final class JarDownloader {

    private static final String USER_AGENT = "AutoUpdater-Plugin";

    private final long maxFileSizeBytes;

    public JarDownloader(long maxFileSizeBytes) {
        this.maxFileSizeBytes = maxFileSizeBytes;
    }

    public DownloadResult download(String url, Path targetTempFile, String hashAlgorithm) throws IOException {
        Http.requireHttps(url);
        Files.createDirectories(targetTempFile.getParent());

        HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                .timeout(Duration.ofSeconds(Http.readTimeoutSeconds()))
                .header("User-Agent", USER_AGENT)
                .GET()
                .build();

        HttpResponse<InputStream> response;
        try {
            response = Http.client().send(request, HttpResponse.BodyHandlers.ofInputStream());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("ダウンロードが中断されました", e);
        }

        if (response.statusCode() == 401 || response.statusCode() == 403) {
            throw new IOException("配布元がダウンロードを許可していません (HTTP " + response.statusCode() + ")");
        }
        if (response.statusCode() != 200) {
            throw new IOException("ダウンロードに失敗しました (HTTP " + response.statusCode() + ")");
        }

        long declaredLength = response.headers().firstValueAsLong("Content-Length").orElse(-1);
        if (declaredLength > maxFileSizeBytes) {
            response.body().close();
            throw new IOException("ファイルサイズが上限を超えています (" + declaredLength + " > " + maxFileSizeBytes + " bytes)");
        }

        MessageDigest digest;
        try {
            digest = MessageDigest.getInstance(hashAlgorithm == null ? "SHA-256" : hashAlgorithm);
        } catch (NoSuchAlgorithmException e) {
            digest = null; // ハッシュアルゴリズム不明時は検証をスキップ (呼び出し元ポリシー次第)
        }

        long total = 0;
        byte[] buffer = new byte[8192];
        try (InputStream in = response.body();
             OutputStream out = Files.newOutputStream(targetTempFile)) {
            int read;
            while ((read = in.read(buffer)) != -1) {
                total += read;
                if (total > maxFileSizeBytes) {
                    throw new IOException("ファイルサイズが上限を超えました (" + maxFileSizeBytes + " bytes)");
                }
                out.write(buffer, 0, read);
                if (digest != null) digest.update(buffer, 0, read);
            }
        } catch (IOException e) {
            deleteQuietly(targetTempFile);
            throw e;
        }

        if (total == 0) {
            deleteQuietly(targetTempFile);
            throw new IOException("ダウンロードされたファイルが空です");
        }

        String hashHex = digest != null ? PackageVerifier.toHex(digest.digest()) : null;
        return new DownloadResult(targetTempFile, total, hashHex);
    }

    public static void atomicReplace(Path source, Path destination) throws IOException {
        Files.createDirectories(destination.getParent());
        try {
            Files.move(source, destination, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (java.nio.file.AtomicMoveNotSupportedException notSupported) {
            Files.move(source, destination, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private static void deleteQuietly(Path path) {
        try {
            Files.deleteIfExists(path);
        } catch (IOException ignored) {
        }
    }
}
