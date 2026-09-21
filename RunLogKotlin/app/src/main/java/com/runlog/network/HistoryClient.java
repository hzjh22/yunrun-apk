package com.runlog.network;

import com.runlog.data.AppConfig;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public class HistoryClient {
    private static final int MAX_DETAIL_COUNT = 50;
    private final ApiClient api;

    public HistoryClient(AppConfig config) {
        this.api = new ApiClient(config);
    }

    /**
     * Lightweight current-semester stats refresh: two requests total, no detail
     * downloads and nothing written to the task database.
     *
     * <p>Used to keep the "本学期跑步次数" card live without re-running the full
     * history extraction.
     */
    public HistoryExtractionResult fetchTermStats() throws Exception {
        HistoryExtractionResult result = new HistoryExtractionResult();
        String yearsJson = api.post("run/listXnYearXqByStudentId", "");
        List<String> tableNames = parseTableNames(yearsJson);
        result.terms = tableNames.size();
        result.currentTermLabel = parseFirstTermLabel(yearsJson);
        readTermTarget(result);
        String newestTable = tableNames.isEmpty() ? "" : tableNames.get(0);
        String listJson = api.post("run/crsReocordInfoList",
                "{\"tableName\":\"" + escape(newestTable) + "\"}");
        readCurrentTermStats(listJson, newestTable, result);
        return result;
    }

    public HistoryExtractionResult extract(ProgressCallback callback) throws Exception {
        HistoryExtractionResult result = new HistoryExtractionResult();
        progress(callback, "正在读取历史学期");
        String yearsJson = api.post("run/listXnYearXqByStudentId", "");
        List<String> tableNames = parseTableNames(yearsJson);
        if (tableNames.isEmpty()) tableNames.add("");
        result.terms = tableNames.size();
        // The term list already carries a proper label for the newest semester.
        result.currentTermLabel = parseFirstTermLabel(yearsJson);
        readTermTarget(result);

        Set<String> seenIds = new LinkedHashSet<>();
        for (int termIndex = 0; termIndex < tableNames.size(); termIndex++) {
            String tableName = tableNames.get(termIndex);
            progress(callback, "正在读取记录列表: " + (tableName.isEmpty() ? "默认表" : tableName));
            String listJson = api.post("run/crsReocordInfoList", "{\"tableName\":\"" + escape(tableName) + "\"}");
            // The term response already carries the school's own counters
            // (sumNumber / sumKm / morningNum). Terms come back newest-first, so
            // index 0 is the semester currently being graded.
            if (termIndex == 0) {
                readCurrentTermStats(listJson, tableName, result);
            }
            List<RecordRef> refs = parseRecordRefs(listJson, tableName);
            result.listRecords += refs.size();
            for (RecordRef ref : refs) {
                if (result.detailRecords >= MAX_DETAIL_COUNT) return result;
                if (ref.id.isEmpty() || seenIds.contains(ref.id)) continue;
                seenIds.add(ref.id);
                progress(callback, "正在读取详情: " + ref.id);
                String path = "R1".equalsIgnoreCase(ref.isMorning) ? "run/crsIsMoringReocordInfo" : "run/crsReocordInfo";
                boolean gzipDetail = "run/crsReocordInfo".equals(path);
                String detailJson = api.post(path, "{\"id\":\"" + escape(ref.id) + "\",\"tableName\":\"" + escape(ref.tableName) + "\"}", gzipDetail);
                String taskJson = extractDataObject(detailJson);
                if (looksLikeTask(taskJson)) {
                    result.taskJsons.add(taskJson);
                    result.detailRecords++;
                }
            }
        }
        return result;
    }

    private static List<String> parseTableNames(String json) throws Exception {
        List<String> out = new ArrayList<>();
        Object data = new JSONObject(json).opt("data");
        if (!(data instanceof JSONArray)) return out;
        JSONArray array = (JSONArray) data;
        for (int i = 0; i < array.length(); i++) {
            JSONObject item = array.optJSONObject(i);
            if (item == null) continue;
            String value = item.optString("value", "").trim();
            if (!value.isEmpty() && !out.contains(value)) out.add(value);
        }
        return out;
    }

    /** Reads the display label of the newest term, e.g. "2026-2027 学年 第一学期". */
    private static String parseFirstTermLabel(String json) {
        try {
            Object data = new JSONObject(json).opt("data");
            if (!(data instanceof JSONArray) || ((JSONArray) data).length() == 0) return "";
            JSONObject first = ((JSONArray) data).optJSONObject(0);
            if (first == null) return "";
            String key = first.optString("key", "").trim();
            if (!key.isEmpty()) return key;
        } catch (Exception ignored) {
            // Label is decorative.
        }
        return "";
    }

    /**
     * Reads the school's counters for the current semester out of a term's
     * record-list response.
     *
     * <p>Fields used: {@code sumNumber} (runs that count toward the term target),
     * {@code sumKm} (kilometres) and {@code morningNum} (morning runs). They are
     * absent on some empty terms, in which case everything stays zero.
     */
    private static void readCurrentTermStats(String json, String tableName,
                                             HistoryExtractionResult result) {
        result.currentTermTable = tableName == null ? "" : tableName;
        // Prefer a real term name derived from the table name; keep whatever
        // readTermTarget() already found (e.g. the semester date range) otherwise.
        String derived = label(tableName);
        if (!derived.isEmpty()) result.currentTermLabel = derived;
        try {
            JSONObject data = new JSONObject(json).optJSONObject("data");
            if (data == null) return;
            result.currentRuns = parseInt(data.opt("sumNumber"));
            result.currentKm = parseDouble(data.opt("sumKm"));
            result.currentMorningRuns = parseInt(data.opt("morningNum"));
        } catch (Exception ignored) {
            // A malformed term response must not abort the whole extraction.
        }
    }

    /**
     * Reads the semester target ({@code raTargetNum}) from the home endpoint, so the
     * card can show "2 / 40 次". Best effort: a failure here must not fail extraction.
     */
    private void readTermTarget(HistoryExtractionResult result) {
        try {
            String homeJson = api.post("run/getHomeRunInfo", "");
            JSONObject data = new JSONObject(homeJson).optJSONObject("data");
            if (data == null) return;
            result.targetRuns = parseInt(data.opt("raTargetNum"));
            if (result.targetRuns <= 0) {
                JSONArray list = data.optJSONArray("cralist");
                JSONObject first = list == null ? null : list.optJSONObject(0);
                if (first != null) result.targetRuns = parseInt(first.opt("raTargetNum"));
            }
            // Only fall back to the task name + date range when the term list gave
            // us nothing better.
            if (result.currentTermLabel.isEmpty()) {
                JSONArray list = data.optJSONArray("cralist");
                if (list != null && list.length() > 0) {
                    JSONObject first = list.optJSONObject(0);
                    if (first != null) {
                        String name = first.optString("raName", "");
                        String start = first.optString("raStartTime", "");
                        String end = first.optString("raEndTime", "");
                        if (!name.isEmpty()) {
                            result.currentTermLabel = name
                                    + (start.isEmpty() ? "" : "（" + start + " ~ " + end + "）");
                        }
                    }
                }
            }
        } catch (Exception ignored) {
            // Target is decorative; never let it break the extraction.
        }
    }

    /**
     * Best-effort display label derived from a term table name.
     * Returns an empty string when the table name carries no year info, so a caller
     * keeps a better label it already has.
     */
    private static String label(String tableName) {
        if (tableName == null || tableName.isEmpty()) return "";
        // crs_run_record113_20251 -> 2025-2026 学年 第一学期
        int idx = tableName.lastIndexOf('_');
        if (idx > 0 && idx + 1 < tableName.length()) {
            String tail = tableName.substring(idx + 1);
            if (tail.length() == 5 && Character.isDigit(tail.charAt(0))) {
                String year = tail.substring(0, 4);
                String half = tail.substring(4);
                int start = Integer.parseInt(year) - 1;
                return start + "-" + year + " 学年 " + ("1".equals(half) ? "第一学期" : "第二学期");
            }
        }
        // crs_run_record113 (the newest table) has no suffix -> no label to derive.
        return "";
    }

    private static int parseInt(Object value) {
        if (value == null) return 0;
        if (value instanceof Number) return ((Number) value).intValue();
        try {
            return (int) Double.parseDouble(String.valueOf(value).trim());
        } catch (Exception e) {
            return 0;
        }
    }

    private static double parseDouble(Object value) {
        if (value == null) return 0d;
        if (value instanceof Number) return ((Number) value).doubleValue();
        try {
            return Double.parseDouble(String.valueOf(value).trim());
        } catch (Exception e) {
            return 0d;
        }
    }

    private static List<RecordRef> parseRecordRefs(String json, String tableName) throws Exception {
        List<RecordRef> out = new ArrayList<>();
        JSONObject root = new JSONObject(json);
        JSONObject data = root.optJSONObject("data");
        if (data == null) return out;
        JSONArray rank = data.optJSONArray("rank");
        if (rank == null) return out;
        for (int i = 0; i < rank.length(); i++) {
            JSONObject month = rank.optJSONObject(i);
            if (month == null) continue;
            JSONArray rankList = month.optJSONArray("rankList");
            if (rankList == null) continue;
            for (int j = 0; j < rankList.length(); j++) {
                JSONObject item = rankList.optJSONObject(j);
                if (item == null) continue;
                String sportType = item.optString("sportType", "");
                if ("日常".equals(sportType) || "随堂".equals(sportType)) continue;
                String id = item.optString("id", "").trim();
                if (id.isEmpty()) continue;
                RecordRef ref = new RecordRef();
                ref.id = id;
                ref.tableName = tableName;
                ref.isMorning = item.optString("isMorning", "");
                out.add(ref);
            }
        }
        return out;
    }

    private static String extractDataObject(String json) throws Exception {
        JSONObject root = new JSONObject(json);
        Object data = root.opt("data");
        return data == null ? json : data.toString();
    }

    private static boolean looksLikeTask(String json) {
        return json != null
                && json.contains("recordMileage")
                && json.contains("duration")
                && (json.contains("pointsList") || json.contains("\"point\""));
    }

    private static void progress(ProgressCallback callback, String message) {
        if (callback != null) callback.onProgress(message);
    }

    private static String escape(String value) {
        return value == null ? "" : value.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    public interface ProgressCallback {
        void onProgress(String message);
    }

    private static class RecordRef {
        String id = "";
        String tableName = "";
        String isMorning = "";
    }
}
