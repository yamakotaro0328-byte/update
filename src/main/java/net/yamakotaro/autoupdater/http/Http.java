package net.yamakotaro.autoupdater.http;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

/**
 * 共有 HttpClient と、GET リクエストの共通ヘルパー。
 * すべての通信は非同期 (メインスレッドをブロックしない) で行う。
 */
public final class Http {

    private static volatile HttpClient client;
    private static volatile int connectTimeoutSeconds = 10;
    private static volatile int readTimeoutSeconds = 30;

    private Http() {}

    public static synchronized void configure(int connectTimeoutSec, int readTimeoutSec, Executor executor) {
        connectTimeoutSeconds = connectTimeoutSec;
        readTimeoutSeconds = readTimeoutSec;
        client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(connectTimeoutSec))
                .followRedirects(HttpClient.Redirect.NORMAL)
                .executor(executor)
                .build();
    }

    public static HttpClient client() {
        HttpClient c = client;
        if (c == null) {
            synchronized (Http.class) {
                if (client == null) {
                    client = HttpClient.newBuilder()
                            .connectTimeout(Duration.ofSeconds(connectTimeoutSeconds))
                            .followRedirects(HttpClient.Redirect.NORMAL)
                            .build();
                }
                c = client;
            }
        }
        return c;
    }

    public static int readTimeoutSeconds() {
        return readTimeoutSeconds;
    }

    /** HTTPS 以外の URL を拒否する。configから不正な URL がそのまま実行されないようにする。 */
    public static void requireHttps(String url) {
        if (url == null || !url.regionMatches(true, 0, "https://", 0, 8)) {
            throw new IllegalArgumentException("HTTPS 以外の URL は許可されていません: " + url);
        }
    }

    public static CompletableFuture<HttpResponse<String>> getText(String url, String userAgent) {
        requireHttps(url);
        HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                .timeout(Duration.ofSeconds(readTimeoutSeconds))
                .header("User-Agent", userAgent)
                .header("Accept", "application/json")
                .GET()
                .build();
        return client().sendAsync(request, HttpResponse.BodyHandlers.ofString());
    }
}
