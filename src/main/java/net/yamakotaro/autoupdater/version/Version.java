package net.yamakotaro.autoupdater.version;

import java.util.Locale;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * セマンティックバージョンに近い形式 ("1.2.3", "v2.0.0-beta" など) を
 * 解析・比較するための不変クラス。
 */
public final class Version implements Comparable<Version> {

    private static final Pattern LEADING_V = Pattern.compile("^[vV]");

    private final int[] numericParts;
    private final String preRelease;
    private final String raw;

    private Version(int[] numericParts, String preRelease, String raw) {
        this.numericParts = numericParts;
        this.preRelease = preRelease;
        this.raw = raw;
    }

    public static Version parse(String input) {
        Objects.requireNonNull(input, "input");
        String trimmed = input.trim();
        Matcher m = LEADING_V.matcher(trimmed);
        String withoutV = m.replaceFirst("");

        // ビルドメタデータ (+xxx) は比較に無関係なので切り捨てる
        int plusIndex = withoutV.indexOf('+');
        if (plusIndex >= 0) {
            withoutV = withoutV.substring(0, plusIndex);
        }

        String numericPart = withoutV;
        String preRelease = null;
        int dashIndex = withoutV.indexOf('-');
        if (dashIndex >= 0) {
            numericPart = withoutV.substring(0, dashIndex);
            preRelease = withoutV.substring(dashIndex + 1);
        }

        String[] segments = numericPart.isEmpty() ? new String[]{"0"} : numericPart.split("\\.");
        int[] parts = new int[segments.length];
        for (int i = 0; i < segments.length; i++) {
            parts[i] = parseIntSafe(segments[i]);
        }

        return new Version(parts, preRelease, trimmed);
    }

    private static int parseIntSafe(String s) {
        StringBuilder digits = new StringBuilder();
        for (char c : s.toCharArray()) {
            if (Character.isDigit(c)) {
                digits.append(c);
            } else {
                break;
            }
        }
        if (digits.length() == 0) {
            return 0;
        }
        try {
            return Integer.parseInt(digits.toString());
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    @Override
    public int compareTo(Version other) {
        int len = Math.max(this.numericParts.length, other.numericParts.length);
        for (int i = 0; i < len; i++) {
            int a = i < this.numericParts.length ? this.numericParts[i] : 0;
            int b = i < other.numericParts.length ? other.numericParts[i] : 0;
            if (a != b) {
                return Integer.compare(a, b);
            }
        }

        // プレリリースが無い方が「正式版」として新しいとみなす (semver 準拠)
        boolean thisHasPre = this.preRelease != null && !this.preRelease.isEmpty();
        boolean otherHasPre = other.preRelease != null && !other.preRelease.isEmpty();
        if (thisHasPre && !otherHasPre) {
            return -1;
        }
        if (!thisHasPre && otherHasPre) {
            return 1;
        }
        if (thisHasPre) {
            return this.preRelease.toLowerCase(Locale.ROOT)
                    .compareTo(other.preRelease.toLowerCase(Locale.ROOT));
        }
        return 0;
    }

    public boolean isNewerThan(Version other) {
        return this.compareTo(other) > 0;
    }

    public boolean isOlderThan(Version other) {
        return this.compareTo(other) < 0;
    }

    public String getRaw() {
        return raw;
    }

    @Override
    public String toString() {
        return raw;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Version other)) return false;
        return this.compareTo(other) == 0;
    }

    @Override
    public int hashCode() {
        int result = 1;
        for (int part : numericParts) {
            result = 31 * result + part;
        }
        return result;
    }
}
