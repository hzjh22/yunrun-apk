package com.runlog.network;

import com.runlog.crypto.CryptoUtils;
import com.runlog.data.AppConfig;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.UUID;
import java.util.zip.GZIPInputStream;

public class LoginClient {
    private static final int TIMEOUT_MS = 20000;

    public LoginResult login(AppConfig input) throws Exception {
        resolveSchoolIfNeeded(input);
        require(input.schoolId, "学校 ID");
        require(input.username, "账号");
        require(input.password, "密码");
        require(input.cipherKey, "cipherkey");
        require(input.cipherKeyEncrypted, "cipherKeyEncrypted");
        require(input.md5Key, "md5key");

        String platform = blank(input.platform) ? "android" : input.platform.toLowerCase(Locale.US);
        String appEdition = blank(input.appEdition) ? AppConfig.DEFAULT_APP_EDITION : input.appEdition;
        String schoolHost = normalizeSchoolHost(input.schoolHost);
        String route = resolveLoginRoute(input.schoolId);
        String url = schoolHost + "/login/" + route;

        String deviceId = blank(input.deviceId) ? randomDeviceId() : input.deviceId;
        String deviceName = blank(input.deviceName) ? "Android(" + android.os.Build.MODEL + ")" : input.deviceName;
        String savedUuid = blank(input.uuid) ? UUID.randomUUID().toString().toUpperCase(Locale.US) : input.uuid;
        String requestUuid = UUID.randomUUID().toString().toUpperCase(Locale.US);
        long utc = System.currentTimeMillis() / 1000L;
        String sign = CryptoUtils.requestSign(platform, utc, requestUuid, input.md5Key);
        String userType = blank(input.userType) ? "1" : input.userType;

        String loginJson = "{"
                + "\"password\":\"" + escape(input.password) + "\","
                + "\"schoolId\":\"" + escape(input.schoolId) + "\","
                + "\"userName\":\"" + escape(input.username) + "\","
                + "\"type\":\"" + escape(userType) + "\""
                + "}";
        String body = encryptedBody(loginJson, input);
        String decoded = postEncrypted(url, body, "", deviceId, deviceName, appEdition, platform, requestUuid, utc, sign, input.cipherKey);
        String token = extractToken(decoded);
        if (blank(token)) {
            throw new IllegalStateException("登录响应未找到 token: " + decoded);
        }

        LoginResult result = new LoginResult();
        result.token = token;
        result.deviceId = deviceId;
        result.deviceName = deviceName;
        result.uuid = savedUuid;
        result.sysEdition = String.valueOf(android.os.Build.VERSION.SDK_INT);
        result.schoolId = input.schoolId;
        result.schoolName = input.schoolName;
        result.schoolHost = schoolHost;
        result.rawResponse = decoded;
        return result;
    }

    private void resolveSchoolIfNeeded(AppConfig input) throws Exception {
        if (!blank(input.schoolId)) return;
        require(input.schoolName, "学校名称");
        require(input.cipherKey, "cipherkey");
        require(input.cipherKeyEncrypted, "cipherKeyEncrypted");
        require(input.md5Key, "md5key");

        String platform = blank(input.platform) ? "android" : input.platform.toLowerCase(Locale.US);
        String appEdition = blank(input.appEdition) ? AppConfig.DEFAULT_APP_EDITION : input.appEdition;
        String uuid = "2211725972932675";
        long utc = System.currentTimeMillis() / 1000L;
        String sign = CryptoUtils.requestSign(platform, utc, uuid, input.md5Key);
        String body = encryptedBody("", input);
        String decoded = postEncrypted(
                AppConfig.DEFAULT_API_HOST + "/app/schoolList",
                body,
                "",
                uuid,
                "Android",
                appEdition,
                platform,
                uuid,
                utc,
                sign,
                input.cipherKey
        );

        SchoolInfo school = findSchool(decoded, input.schoolName);
        if (school == null) {
            throw new IllegalStateException("未找到学校: " + input.schoolName + "，学校列表响应前缀: " + preview(decoded));
        }
        input.schoolId = school.schoolId;
        input.schoolName = school.schoolName;
        input.schoolHost = normalizeSchoolHost(school.schoolUrl);
    }

    private String encryptedBody(String contentPlain, AppConfig config) throws Exception {
        return "{"
                + "\"cipherKey\":\"" + escape(config.cipherKeyEncrypted) + "\","
                + "\"content\":\"" + escape(CryptoUtils.sm4EncryptBase64(contentPlain, config.cipherKey)) + "\""
                + "}";
    }

    private String postEncrypted(
            String url,
            String body,
            String token,
            String deviceId,
            String deviceName,
            String appEdition,
            String platform,
            String uuid,
            long utc,
            String sign,
            String cipherKey
    ) throws Exception {
        HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
        conn.setRequestMethod("POST");
        conn.setConnectTimeout(TIMEOUT_MS);
        conn.setReadTimeout(TIMEOUT_MS);
        conn.setDoOutput(true);
        conn.setRequestProperty("token", token == null ? "" : token);
        conn.setRequestProperty("isApp", "app");
        conn.setRequestProperty("deviceId", deviceId);
        conn.setRequestProperty("deviceName", deviceName);
        conn.setRequestProperty("version", appEdition);
        conn.setRequestProperty("platform", platform);
        conn.setRequestProperty("uuid", uuid);
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
        String trimmed = response.trim();
        if (trimmed.startsWith("{") || trimmed.startsWith("[")) {
            return trimmed;
        }
        if (trimmed.length() >= 2 && trimmed.startsWith("\"") && trimmed.endsWith("\"")) {
            trimmed = unquoteJsonString(trimmed);
        }
        if (!looksLikeBase64(trimmed)) {
            return trimmed;
        }
        return CryptoUtils.sm4DecryptBase64ToString(trimmed, cipherKey);
    }

    public static String resolveLoginRoute(String schoolId) {
        String id = schoolId == null ? "" : schoolId.trim();
        if ("100".equals(id)) return "appLoginHGD";
        if ("106".equals(id)) return "appLoginCHZU";
        return "appLogin";
    }

    public static String normalizeSchoolHost(String host) {
        String value = host == null ? "" : host.trim();
        while (value.endsWith("/")) value = value.substring(0, value.length() - 1);
        if (blank(value) || value.contains(":9001")) return AppConfig.DEFAULT_SCHOOL_HOST;
        return value;
    }

    private static SchoolInfo findSchool(String json, String schoolName) {
        String target = schoolName == null ? "" : schoolName.trim();
        int from = 0;
        while (true) {
            int nameAt = json.indexOf("\"schoolName\"", from);
            if (nameAt < 0) break;
            String object = objectAround(json, nameAt);
            if (blank(object)) {
                from = nameAt + 12;
                continue;
            }
            String name = extractString(object, "schoolName");
            String id = extractNumberOrString(object, "schoolId");
            String url = extractString(object, "schoolUrl");
            if (!blank(name) && (name.equals(target) || name.contains(target) || target.contains(name))) {
                SchoolInfo info = new SchoolInfo();
                info.schoolName = name;
                info.schoolId = id;
                info.schoolUrl = url;
                return info;
            }
            from = nameAt + 12;
        }
        return null;
    }

    private static String extractToken(String json) {
        return extractString(json, "token");
    }

    private static String extractString(String json, String key) {
        int valueStart = valueStart(json, key);
        if (valueStart < 0 || valueStart >= json.length() || json.charAt(valueStart) != '"') return "";
        StringBuilder out = new StringBuilder();
        boolean escape = false;
        for (int i = valueStart + 1; i < json.length(); i++) {
            char c = json.charAt(i);
            if (escape) {
                out.append(c);
                escape = false;
                continue;
            }
            if (c == '\\') {
                escape = true;
                continue;
            }
            if (c == '"') return out.toString();
            out.append(c);
        }
        return "";
    }

    private static String extractNumberOrString(String json, String key) {
        String string = extractString(json, key);
        if (!blank(string)) return string;
        int valueStart = valueStart(json, key);
        if (valueStart < 0) return "";
        int end = valueStart;
        while (end < json.length()) {
            char c = json.charAt(end);
            if ((c >= '0' && c <= '9') || c == '-') {
                end++;
            } else {
                break;
            }
        }
        return end > valueStart ? json.substring(valueStart, end) : "";
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

    @SuppressWarnings("unused")
    private static String read(InputStream in) throws Exception {
        if (in == null) return "";
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
            StringBuilder builder = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) builder.append(line);
            return builder.toString();
        }
    }

    private static void require(String value, String name) {
        if (blank(value)) throw new IllegalArgumentException("缺少 " + name);
    }

    private static String randomDeviceId() {
        return UUID.randomUUID().toString().replace("-", "") + UUID.randomUUID().toString().replace("-", "");
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

    private static String preview(String value) {
        if (value == null) return "";
        String flat = value.replace('\n', ' ').replace('\r', ' ');
        return flat.length() <= 120 ? flat : flat.substring(0, 120) + "...";
    }

    private static String objectAround(String text, int index) {
        int start = -1;
        for (int i = index; i >= 0; i--) {
            if (text.charAt(i) == '{') {
                start = i;
                break;
            }
        }
        if (start < 0) return "";
        int end = matchingBrace(text, start);
        if (end < index) return "";
        return text.substring(start, end + 1);
    }

    private static int matchingBrace(String text, int start) {
        int depth = 0;
        boolean inString = false;
        boolean escape = false;
        for (int i = start; i < text.length(); i++) {
            char c = text.charAt(i);
            if (escape) {
                escape = false;
                continue;
            }
            if (c == '\\') {
                escape = true;
                continue;
            }
            if (c == '"') {
                inString = !inString;
                continue;
            }
            if (inString) continue;
            if (c == '{') depth++;
            if (c == '}') {
                depth--;
                if (depth == 0) return i;
            }
        }
        return -1;
    }

    private static int valueStart(String json, String key) {
        String needle = "\"" + key + "\"";
        int keyAt = json.indexOf(needle);
        if (keyAt < 0) return -1;
        int colon = json.indexOf(':', keyAt + needle.length());
        if (colon < 0) return -1;
        int pos = colon + 1;
        while (pos < json.length() && Character.isWhitespace(json.charAt(pos))) pos++;
        return pos;
    }

    private static boolean blank(String value) {
        return value == null || value.trim().isEmpty();
    }

    private static class SchoolInfo {
        String schoolName;
        String schoolId;
        String schoolUrl;
    }
}
