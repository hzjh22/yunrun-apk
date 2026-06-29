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

    public HistoryExtractionResult extract(ProgressCallback callback) throws Exception {
        HistoryExtractionResult result = new HistoryExtractionResult();
        progress(callback, "正在读取历史学期");
        String yearsJson = api.post("run/listXnYearXqByStudentId", "");
        List<String> tableNames = parseTableNames(yearsJson);
        if (tableNames.isEmpty()) tableNames.add("");
        result.terms = tableNames.size();

        Set<String> seenIds = new LinkedHashSet<>();
        for (String tableName : tableNames) {
            progress(callback, "正在读取记录列表: " + (tableName.isEmpty() ? "默认表" : tableName));
            String listJson = api.post("run/crsReocordInfoList", "{\"tableName\":\"" + escape(tableName) + "\"}");
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
