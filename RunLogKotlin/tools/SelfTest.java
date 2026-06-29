import java.security.MessageDigest;
import java.util.*;
import java.util.regex.*;

public class SelfTest {
    static final String DEFAULT_SCHOOL_HOST = "http://sports.aiyyd.com:8080";

    record Candidate(String requestLine, int score, Map<String, String> headers) {}

    public static void main(String[] args) throws Exception {
        String pcap = String.join("\n",
                "POST /api/login/appLogin HTTP/1.1",
                "Host: sports.aiyyd.com:9001",
                "token: 1234567890abcdef",
                "deviceid: abcdef1234567890abcdef1234567890",
                "devicename: vivo(V2359A)",
                "version: 3.6.2",
                "platform: android",
                "uuid: BE4F7B9D-BA79-4046-A116-96B25CB95C43",
                "");

        Candidate candidate = parseCandidate(pcap);
        check(candidate != null, "candidate not found");
        check(DEFAULT_SCHOOL_HOST.equals(normalizeHost(candidate.requestLine, candidate.headers.get("host"))), "host normalize failed");
        check("1234567890abcdef".equals(candidate.headers.get("token")), "token extract failed");
        check(mask("1234567890abcdef").equals("123456...cdef"), "mask failed");
        check(md5("platform=android&utc=1781953823&uuid=ABC&appsecret=secret").length() == 32, "md5 failed");

        String taskJson = "{\"data\":{\"recordMileage\":2.84,\"recodePace\":5.61,\"duration\":956,\"pointsList\":[{\"point\":\"1,2\"},{\"point\":\"3,4\"}],\"manageList\":[{\"point\":\"5,6\"}]}}";
        check(extractDouble(taskJson, "recordMileage") == 2.84, "task distance failed");
        check(extractInt(taskJson, "duration") == 956, "task duration failed");
        check(countPoints(taskJson.substring(0, taskJson.indexOf("\"manageList\""))) == 2, "point count failed");

        System.out.println("RunLog Java/Kotlin phase-1 selftest passed");
    }

    static Candidate parseCandidate(String text) {
        String[] lines = text.split("\\R");
        Pattern requestPattern = Pattern.compile("^(GET|POST|PUT|DELETE|PATCH|HEAD|OPTIONS)\\s+(\\S+)\\s+HTTP/\\d(?:\\.\\d)?$", Pattern.CASE_INSENSITIVE);
        Pattern headerPattern = Pattern.compile("^([A-Za-z][A-Za-z0-9_-]*):\\s*(.*)$");
        String request = "";
        Map<String, String> headers = new LinkedHashMap<>();
        boolean inHeaders = false;
        for (String raw : lines) {
            String line = raw.trim();
            if (line.isEmpty() && inHeaders) break;
            if (requestPattern.matcher(line).matches()) {
                request = line;
                inHeaders = true;
                continue;
            }
            if (!inHeaders) continue;
            Matcher m = headerPattern.matcher(line);
            if (m.matches()) headers.put(m.group(1).toLowerCase(Locale.ROOT), m.group(2).trim());
        }
        if (request.isBlank() || headers.isEmpty()) return null;
        int score = 0;
        for (String k : List.of("token", "deviceid", "devicename", "version", "platform", "uuid", "utc", "sign")) {
            if (headers.containsKey(k) && !headers.get(k).isBlank()) score++;
        }
        String host = headers.getOrDefault("host", "");
        if (host.endsWith(":9001")) score += 2;
        if (host.contains("sports.aiyyd.com")) score++;
        return new Candidate(request, score, headers);
    }

    static String normalizeHost(String requestLine, String host) {
        if (host == null || host.isBlank()) return DEFAULT_SCHOOL_HOST;
        if (host.endsWith(":9001")) return DEFAULT_SCHOOL_HOST;
        return (host.endsWith(":443") ? "https://" : "http://") + host;
    }

    static String mask(String value) {
        return value.length() <= 10 ? value.substring(0, 2) + "***" : value.substring(0, 6) + "..." + value.substring(value.length() - 4);
    }

    static String md5(String value) throws Exception {
        byte[] digest = MessageDigest.getInstance("MD5").digest(value.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        StringBuilder sb = new StringBuilder();
        for (byte b : digest) sb.append(String.format("%02x", b));
        return sb.toString();
    }

    static double extractDouble(String json, String key) {
        Matcher m = Pattern.compile("\"" + key + "\"\\s*:\\s*([0-9.]+)").matcher(json);
        return m.find() ? Double.parseDouble(m.group(1)) : 0;
    }

    static int extractInt(String json, String key) {
        Matcher m = Pattern.compile("\"" + key + "\"\\s*:\\s*(\\d+)").matcher(json);
        return m.find() ? Integer.parseInt(m.group(1)) : 0;
    }

    static int countPoints(String json) {
        Matcher m = Pattern.compile("\"point\"\\s*:").matcher(json);
        int count = 0;
        while (m.find()) count++;
        return count;
    }

    static void check(boolean value, String message) {
        if (!value) throw new IllegalStateException(message);
    }
}

