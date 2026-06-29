package com.runlog.capture;

import com.runlog.data.AppConfig;
import com.runlog.data.CandidateConfig;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class PcapTextParser {
    private PcapTextParser() {
    }

    public static CaptureParseResult parse(String text) {
        List<List<String>> messages = splitMessages(text == null ? "" : text);
        CaptureParseResult result = new CaptureParseResult();
        for (List<String> message : messages) {
            CandidateConfig candidate = parseMessage(message);
            if (candidate != null) {
                result.candidates.add(candidate);
                if (result.best == null || candidate.score > result.best.score) {
                    result.best = candidate;
                }
            }
        }
        if (result.best == null) {
            throw new IllegalArgumentException("没有在抓包文本中找到可用 HTTP 请求头");
        }
        return result;
    }

    private static List<List<String>> splitMessages(String text) {
        List<List<String>> messages = new ArrayList<>();
        List<String> current = new ArrayList<>();
        String[] lines = text.replace("\r\n", "\n").replace('\r', '\n').split("\n");
        for (String line : lines) {
            if (isRequestLine(line.trim()) && !current.isEmpty()) {
                messages.add(current);
                current = new ArrayList<>();
            }
            current.add(line);
        }
        if (!current.isEmpty()) messages.add(current);
        return messages;
    }

    private static CandidateConfig parseMessage(List<String> lines) {
        String requestLine = "";
        Map<String, String> headers = new HashMap<>();
        boolean inHeaders = false;
        for (String raw : lines) {
            String line = raw == null ? "" : raw.trim();
            if (line.startsWith("\uFEFF")) line = line.substring(1);
            if (line.isEmpty()) {
                if (inHeaders) break;
                continue;
            }
            if (isRequestLine(line)) {
                requestLine = line;
                inHeaders = true;
                continue;
            }
            if (!inHeaders) continue;
            int colon = line.indexOf(':');
            if (colon <= 0) continue;
            String key = line.substring(0, colon).trim().toLowerCase(Locale.US);
            String value = line.substring(colon + 1).trim();
            headers.put(key, value);
        }
        if (requestLine.isEmpty() || headers.isEmpty()) return null;

        String host = header(headers, "host");
        CandidateConfig config = new CandidateConfig();
        config.requestLine = requestLine;
        config.requestHost = host;
        config.schoolHost = normalizeSchoolHost(apiBaseFromRequest(requestLine, host), host);
        config.appEdition = header(headers, "version");
        config.platform = header(headers, "platform");
        config.token = header(headers, "token");
        config.deviceId = first(headers, "deviceid", "device-id", "device_id");
        config.deviceName = first(headers, "devicename", "device-name", "device_name");
        config.uuid = header(headers, "uuid");
        config.score = score(headers);

        if (blank(config.token) && blank(config.deviceId) && blank(config.deviceName)
                && blank(config.appEdition) && blank(config.platform) && blank(config.uuid) && blank(host)) {
            return null;
        }
        return config;
    }

    private static int score(Map<String, String> headers) {
        int score = 0;
        String[] keys = {"token", "deviceid", "devicename", "version", "platform", "uuid", "utc", "sign"};
        for (String key : keys) {
            if (!blank(headers.get(key))) score++;
        }
        String host = header(headers, "host");
        if (host.endsWith(":9001")) score += 2;
        if (host.contains("sports.aiyyd.com")) score += 1;
        return score;
    }

    private static String header(Map<String, String> headers, String key) {
        String value = headers.get(key);
        return value == null ? "" : value;
    }

    private static String first(Map<String, String> headers, String... keys) {
        for (String key : keys) {
            String value = headers.get(key);
            if (!blank(value)) return value;
        }
        return "";
    }

    private static String apiBaseFromRequest(String requestLine, String host) {
        if (blank(host)) return "";
        String scheme = host.endsWith(":443") ? "https" : "http";
        String[] parts = requestLine.split("\\s+");
        String path = parts.length >= 2 ? parts[1] : "";
        String base = scheme + "://" + host;
        if ("/api".equals(path) || path.startsWith("/api/")) {
            return base + "/api";
        }
        return base;
    }

    public static String normalizeSchoolHost(String base, String host) {
        String value = base == null ? "" : base.trim();
        while (value.endsWith("/")) value = value.substring(0, value.length() - 1);
        if (host.endsWith(":9001") || value.endsWith(":9001/api") || value.endsWith(":9001")) {
            return AppConfig.DEFAULT_SCHOOL_HOST;
        }
        return blank(value) ? AppConfig.DEFAULT_SCHOOL_HOST : value;
    }

    private static boolean isRequestLine(String line) {
        return line.matches("(?i)^(GET|POST|PUT|DELETE|PATCH|HEAD|OPTIONS)\\s+\\S+\\s+HTTP/\\d(\\.\\d)?$");
    }

    private static boolean blank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
