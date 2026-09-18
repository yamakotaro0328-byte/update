package net.yamakotaro.autoupdater.http;

import java.nio.file.Path;

public record DownloadResult(Path path, long sizeBytes, String hashHex) {}
