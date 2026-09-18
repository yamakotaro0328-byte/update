package net.yamakotaro.autoupdater.security;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * ダウンロードしたファイルの整合性・正当性チェック。
 */
public final class PackageVerifier {

    private PackageVerifier() {}

    public static boolean hashMatches(String actualHex, String expectedHex) {
        if (expectedHex == null || expectedHex.isBlank()) return true; // 検証情報が無い場合は呼び出し元のポリシーに委ねる
        return actualHex != null && actualHex.equalsIgnoreCase(expectedHex.trim());
    }

    public static String toHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            sb.append(String.format(Locale.ROOT, "%02x", b));
        }
        return sb.toString();
    }

    /** ダウンロードされたファイルが破損していない有効な jar (plugin.yml を含む) かを確認する。 */
    public static boolean isValidPluginJar(Path path) {
        if (path == null || !Files.isRegularFile(path)) return false;
        try (ZipFile zip = new ZipFile(path.toFile())) {
            ZipEntry entry = zip.getEntry("plugin.yml");
            return entry != null;
        } catch (IOException e) {
            return false;
        }
    }
}
