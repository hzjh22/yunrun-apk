package com.runlog.network;

import com.runlog.crypto.CryptoUtils;
import com.runlog.data.AppConfig;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.UUID;
import java.util.zip.GZIPOutputStream;
import java.util.zip.GZIPInputStream;

public class ApiClient {
    private static final int TIMEOUT_MS = 20000;
    private final AppConfig config;

    public ApiClient(AppConfig config) {
        this.config = config;
    }

    public String post(String path, String plainJson) throws Exception {
        return post(path, plainJson, false);
    }

    public String post(String path, String plainJson, boolean gzipAfterDecrypt) throws Exception {
        return postEncrypted(path, encryptedBodyPlain(plainJson == null ? "" : plainJson), gzipAfterDecrypt);
    }

    public String postGzipJson(String path, String plainJson) throws Exception {
        return postEncrypted(path, encryptedBodyBytes(gzip(plainJson == null ? "" : plainJson)), false);
    }

    private String postEncrypted(String path, String body, boolean gzipAfterDecrypt) throws Exception {
        require(config.schoolHost, "schoolHost");
        require(config.token, "token");
        require(config.deviceId, "deviceId");
        require(config.deviceName, "deviceName");
        require(config.cipherKey, "cipherkey");
        require(config.cipherKeyEncrypted, "cipherKeyEncrypted");
        require(config.md5Key, "md5key");

        String platform = blank(config.platform) ? "android" : config.platform.toLowerCase(Locale.US);
        String appEdition = blank(config.appEdition) ? AppConfig.DEFAULT_APP_EDITION : config.appEdition;
        String requestUuid = blank(config.uuid) ? UUID.randomUUID().toString().toUpperCase(Locale.US) : config.uuid;
        long utc = System.currentTimeMillis() / 1000L;
        String sign = CryptoUtils.requestSign(platform, utc, requestUuid, config.md5Key);
        String url = normalizeHost(config.schoolHost) + "/" + trimSlashes(path);

        HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
        conn.setRequestMethod("POST");
        conn.setConnectTimeout(TIMEOUT_MS);
        conn.setReadTimeout(TIMEOUT_MS);
        conn.setDoOutput(true);
        conn.setRequestProperty("token", config.token);
        conn.setRequestProperty("isApp", "app");
        conn.setRequestProperty("deviceId", config.deviceId);
        conn.setRequestProperty("deviceName", config.deviceName);
        conn.setRequestProperty("version", appEdition);
        conn.setRequestProperty("platform", platform);
        conn.setRequestProperty("uuid", requestUuid);
        conn.setRequestProperty("utc", String.valueOf(utc));
        conn.setRequestProperty("sign", sign);
        conn.setRequestProperty("Content-Type", "application/json; charset=utf-8");
        conn.setRequestProperty("Connection", "Keep-Alive");
        conn.setRequestProperty("Accept-Encoding", "identity");
        conn.setRequestProperty("User-Agent", "okhttp/4.9.1");

        try (OutputStream out = conn.getOutputStream()) {
            out.write(body.getBytes(StandardCharsets.UTF_8));
        }

        int code = conn.getResponseCode();
        String response = read(conn, code >= 200 && code < 400 ? conn.getInputStream() : conn.getErrorStream());
        if (code < 200 || code >= 300) {
            throw new IllegalStateException("HTTP " + code + ": " + response);
        }
        return decodeResponse(response, gzipAfterDecrypt);
    }

    private String encryptedBodyPlain(String contentPlain) throws Exception {
        return encryptedBodyFromBase64(CryptoUtils.sm4EncryptBase64(contentPlain, config.cipherKey));
    }

    private String encryptedBodyBytes(byte[] contentPlain) throws Exception {
        return encryptedBodyFromBase64(CryptoUtils.sm4EncryptBase64(contentPlain, config.cipherKey));
    }

    private String encryptedBodyFromBase64(String encryptedContent) {
        return "{"
                + "\"cipherKey\":\"" + escape(config.cipherKeyEncrypted) + "\","
                + "\"content\":\"" + escape(encryptedContent) + "\""
                + "}";
    }

    private String decodeResponse(String response, boolean gzipAfterDecrypt) throws Exception {
        String trimmed = response == null ? "" : response.trim();
        if (trimmed.startsWith("{") || trimmed.startsWith("[")) {
            return trimmed;
        }
        if (trimmed.length() >= 2 && trimmed.startsWith("\"") && trimmed.endsWith("\"")) {
            trimmed = unquoteJsonString(trimmed);
        }
        if (!looksLikeBase64(trimmed)) {
            return trimmed;
        }
        byte[] decrypted = CryptoUtils.sm4DecryptBase64(trimmed, config.cipherKey);
        if (!gzipAfterDecrypt) {
            return new String(decrypted, StandardCharsets.UTF_8);
        }
        return ungzip(decrypted);
    }

    private static String read(HttpURLConnection conn, InputStream raw) throws Exception {
        if (raw == null) return "";
        InputStream in = raw;
        String encoding = conn.getContentEncoding();
        if (encoding != null && encoding.toLowerCase(Locale.US).contains("gzip")) {
            in = new GZIPInputStream(raw);
        }
        try (InputStream closeable = in; ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[8192];
            int read;
            while ((read = closeable.read(buffer)) >= 0) {
                out.write(buffer, 0, read);
            }
            return new String(out.toByteArray(), StandardCharsets.UTF_8);
        }
    }

    private static String ungzip(byte[] bytes) throws Exception {
        try (GZIPInputStream in = new GZIPInputStream(new ByteArrayInputStream(bytes));
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[8192];
            int read;
            while ((read = in.read(buffer)) >= 0) {
                out.write(buffer, 0, read);
            }
            return new String(out.toByteArray(), StandardCharsets.UTF_8);
        }
    }

    private static byte[] gzip(String value) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (GZIPOutputStream gzip = new GZIPOutputStream(out)) {
            gzip.write(value.getBytes(StandardCharsets.UTF_8));
        }
        return out.toByteArray();
    }

    private static String normalizeHost(String host) {
        String value = host == null ? "" : host.trim();
        while (value.endsWith("/")) value = value.substring(0, value.length() - 1);
        if (blank(value) || value.contains(":9001")) return AppConfig.DEFAULT_SCHOOL_HOST;
        return value;
    }

    private static String trimSlashes(String path) {
        String value = path == null ? "" : path.trim();
        while (value.startsWith("/")) value = value.substring(1);
        return value;
    }

    private static void require(String value, String name) {
        if (blank(value)) throw new IllegalArgumentException("缺少 " + name);
    }

    private static String escape(String value) {
        return value == null ? "" : value.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private static String unquoteJsonString(String value) {
        String body = value.substring(1, value.length() - 1);
        return body.replace("\\\"", "\"")
                .replace("\\/", "/")
                .replace("\\n", "\n")
                .replace("\\r", "\r")
                .replace("\\t", "\t")
                .replace("\\\\", "\\");
    }

    private static boolean looksLikeBase64(String value) {
        if (value == null || value.length() < 16) return false;
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            boolean ok = (c >= 'A' && c <= 'Z')
                    || (c >= 'a' && c <= 'z')
                    || (c >= '0' && c <= '9')
                    || c == '+' || c == '/' || c == '=' || c == '\r' || c == '\n';
            if (!ok) return false;
        }
        return true;
    }

    private static boolean blank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
