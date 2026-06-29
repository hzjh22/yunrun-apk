package com.runlog.task;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class TaskRepository {
    private static final Pattern MILEAGE = Pattern.compile("\"recordMileage\"\\s*:\\s*([0-9.]+)");
    private static final Pattern DURATION = Pattern.compile("\"duration\"\\s*:\\s*(\\d+)");
    private static final Pattern PACE = Pattern.compile("\"recodePace\"\\s*:\\s*([0-9.]+)");
    private static final String LEGACY_MIGRATED = "legacy_migrated";

    private final Context context;
    private final File extractedDir;
    private final File metaFile;
    private final TaskDbHelper dbHelper;

    public TaskRepository(Context context) {
        this.context = context.getApplicationContext();
        this.extractedDir = new File(this.context.getFilesDir(), "extracted_tasks");
        if (!extractedDir.exists()) extractedDir.mkdirs();
        this.metaFile = new File(extractedDir, ".task_meta");
        this.dbHelper = new TaskDbHelper(this.context);
    }

    public synchronized List<TaskSummary> loadAll() {
        migrateLegacyIfNeeded();
        List<TaskSummary> out = new ArrayList<>();
        SQLiteDatabase db = dbHelper.getReadableDatabase();
        try (Cursor cursor = db.query("tasks", null, null, null, null, null, "updated_at DESC, id DESC")) {
            while (cursor.moveToNext()) {
                out.add(summaryFromCursor(cursor));
            }
        }
        return out;
    }

    public List<TaskSummary> loadExtracted() {
        return loadAll();
    }

    public synchronized String readTaskJson(TaskSummary summary) throws Exception {
        migrateLegacyIfNeeded();
        if (summary == null || summary.fileName == null) {
            throw new IllegalArgumentException("task is empty");
        }
        SQLiteDatabase db = dbHelper.getReadableDatabase();
        try (Cursor cursor = db.query("tasks", new String[]{"task_json"}, "file_name=?",
                new String[]{summary.fileName}, null, null, null, "1")) {
            if (cursor.moveToFirst()) {
                return cursor.getString(0);
            }
        }
        return readLegacyTaskJson(summary);
    }

    public ImportResult importHistoryText(String sourceName, String text) throws Exception {
        List<String> candidates = extractTaskJsons(text);
        if (candidates.isEmpty()) {
            return new ImportResult(0, 0, 0, storagePath(), "未识别到 tasklist 跑步数据");
        }
        return saveTaskJsons(sourceName, candidates, "tasklist_import_", "跑步数据", "识别到候选数据，但未通过 tasklist 字段校验");
    }

    public ImportResult saveExtractedTasks(String sourceName, List<String> taskJsons) throws Exception {
        if (taskJsons == null || taskJsons.isEmpty()) {
            return new ImportResult(0, 0, 0, storagePath(), "没有可保存的历史跑步详情");
        }
        return saveTaskJsons(sourceName, taskJsons, "tasklist_online_", "历史跑步数据", "读取到历史详情，但没有通过 tasklist 字段校验");
    }

    public synchronized void clearExtracted() {
        SQLiteDatabase db = dbHelper.getWritableDatabase();
        db.beginTransaction();
        try {
            db.delete("tasks", null, null);
            putKv(db, LEGACY_MIGRATED, "1");
            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }
        File[] files = extractedDir.listFiles();
        if (files == null) return;
        for (File file : files) {
            if (file.isDirectory()) clearDir(file);
            //noinspection ResultOfMethodCallIgnored
            file.delete();
        }
    }

    public synchronized int extractedCount() {
        migrateLegacyIfNeeded();
        SQLiteDatabase db = dbHelper.getReadableDatabase();
        try (Cursor cursor = db.rawQuery("SELECT COUNT(*) FROM tasks", null)) {
            return cursor.moveToFirst() ? cursor.getInt(0) : 0;
        }
    }

    public synchronized void markUsed(TaskSummary summary) {
        migrateLegacyIfNeeded();
        if (summary == null || summary.fileName == null) return;
        SQLiteDatabase db = dbHelper.getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put("updated_at", System.currentTimeMillis());
        String where;
        String[] args;
        if (summary.fingerprint != null && !summary.fingerprint.trim().isEmpty()) {
            where = "fingerprint=?";
            args = new String[]{summary.fingerprint};
        } else {
            where = "file_name=?";
            args = new String[]{summary.fileName};
        }
        db.execSQL("UPDATE tasks SET use_count=use_count+1, updated_at=? WHERE " + where,
                new Object[]{System.currentTimeMillis(), args[0]});
    }

    private synchronized ImportResult saveTaskJsons(String sourceName, List<String> taskJsons, String prefix, String noun, String emptyMessage) throws Exception {
        migrateLegacyIfNeeded();
        int saved = 0;
        int merged = 0;
        int usable = 0;
        String stamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(new Date());
        SQLiteDatabase db = dbHelper.getWritableDatabase();
        db.beginTransaction();
        try {
            for (String json : taskJsons) {
                TaskSummary summary = parse("preview.json", sourceName, json);
                if (!isUsable(summary)) continue;
                usable++;
                TaskSummary existing = findByFingerprint(db, summary.fingerprint);
                if (existing != null) {
                    mergeTask(db, existing.fileName, summary.duplicateCount, summary.useCount);
                    merged++;
                    continue;
                }
                String fileName = "extracted/" + prefix + stamp + "_" + String.format(Locale.US, "%02d", saved + 1) + ".json";
                summary.fileName = fileName;
                summary.sourceDir = sourceName;
                insertTask(db, summary, json);
                saved++;
            }
            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }
        String message;
        if (saved > 0) {
            message = "已从 " + sourceName + " 保存 " + saved + " 条" + noun + (merged > 0 ? "，合并重复 " + merged + " 条" : "");
        } else if (merged > 0) {
            message = "没有新增" + noun + "，已合并重复 " + merged + " 条";
        } else {
            message = usable > 0 ? "识别到可用数据，但保存失败" : emptyMessage;
        }
        return new ImportResult(taskJsons.size(), saved, merged, storagePath(), message);
    }

    private void migrateLegacyIfNeeded() {
        SQLiteDatabase db = dbHelper.getWritableDatabase();
        if ("1".equals(getKv(db, LEGACY_MIGRATED))) return;
        JSONObject meta = readMeta();
        File[] files = extractedDir.listFiles();
        db.beginTransaction();
        try {
            if (files != null) {
                for (File file : files) {
                    if (!file.isFile() || !file.getName().endsWith(".json")) continue;
                    try (InputStream in = new FileInputStream(file)) {
                        String json = new String(readAll(in), StandardCharsets.UTF_8);
                        TaskSummary summary = parse("extracted/" + file.getName(), "本地提取", json);
                        applyMeta(summary, file.getName(), meta);
                        if (!isUsable(summary)) continue;
                        TaskSummary existing = findByFingerprint(db, summary.fingerprint);
                        if (existing != null) {
                            mergeTask(db, existing.fileName, summary.duplicateCount, summary.useCount);
                        } else {
                            insertTask(db, summary, json);
                        }
                    } catch (Exception ignored) {
                    }
                }
            }
            putKv(db, LEGACY_MIGRATED, "1");
            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }
    }

    private TaskSummary findByFingerprint(SQLiteDatabase db, String fingerprint) {
        if (fingerprint == null || fingerprint.trim().isEmpty()) return null;
        try (Cursor cursor = db.query("tasks", null, "fingerprint=?", new String[]{fingerprint}, null, null, null, "1")) {
            return cursor.moveToFirst() ? summaryFromCursor(cursor) : null;
        }
    }

    private void insertTask(SQLiteDatabase db, TaskSummary summary, String json) {
        long now = System.currentTimeMillis();
        ContentValues values = new ContentValues();
        values.put("file_name", summary.fileName);
        values.put("source_dir", blank(summary.sourceDir) ? "本地提取" : summary.sourceDir);
        values.put("task_json", json == null ? "" : json);
        values.put("distance_km", summary.distanceKm);
        values.put("duration_seconds", summary.durationSeconds);
        values.put("pace", summary.pace);
        values.put("points_count", summary.pointsCount);
        values.put("manage_count", summary.manageCount);
        values.put("fingerprint", summary.fingerprint);
        values.put("duplicate_count", Math.max(1, summary.duplicateCount));
        values.put("use_count", Math.max(0, summary.useCount));
        values.put("created_at", now);
        values.put("updated_at", now);
        db.insertWithOnConflict("tasks", null, values, SQLiteDatabase.CONFLICT_IGNORE);
    }

    private void mergeTask(SQLiteDatabase db, String fileName, int duplicateCount, int useCount) {
        db.execSQL("UPDATE tasks SET duplicate_count=duplicate_count+?, use_count=use_count+?, updated_at=? WHERE file_name=?",
                new Object[]{Math.max(1, duplicateCount), Math.max(0, useCount), System.currentTimeMillis(), fileName});
    }

    private TaskSummary summaryFromCursor(Cursor cursor) {
        TaskSummary summary = new TaskSummary();
        summary.fileName = cursor.getString(cursor.getColumnIndexOrThrow("file_name"));
        summary.sourceDir = cursor.getString(cursor.getColumnIndexOrThrow("source_dir"));
        summary.distanceKm = cursor.getDouble(cursor.getColumnIndexOrThrow("distance_km"));
        summary.durationSeconds = cursor.getInt(cursor.getColumnIndexOrThrow("duration_seconds"));
        summary.pace = cursor.getDouble(cursor.getColumnIndexOrThrow("pace"));
        summary.pointsCount = cursor.getInt(cursor.getColumnIndexOrThrow("points_count"));
        summary.manageCount = cursor.getInt(cursor.getColumnIndexOrThrow("manage_count"));
        summary.fingerprint = cursor.getString(cursor.getColumnIndexOrThrow("fingerprint"));
        summary.duplicateCount = cursor.getInt(cursor.getColumnIndexOrThrow("duplicate_count"));
        summary.useCount = cursor.getInt(cursor.getColumnIndexOrThrow("use_count"));
        return summary;
    }

    private String readLegacyTaskJson(TaskSummary summary) throws Exception {
        String name = storageName(summary.fileName);
        File file = new File(extractedDir, name);
        if (!file.isFile()) {
            throw new IllegalArgumentException("task file not found: " + summary.fileName);
        }
        try (InputStream in = new FileInputStream(file)) {
            return new String(readAll(in), StandardCharsets.UTF_8);
        }
    }

    private String getKv(SQLiteDatabase db, String key) {
        try (Cursor cursor = db.query("kv", new String[]{"value"}, "key=?", new String[]{key}, null, null, null, "1")) {
            return cursor.moveToFirst() ? cursor.getString(0) : "";
        }
    }

    private void putKv(SQLiteDatabase db, String key, String value) {
        ContentValues values = new ContentValues();
        values.put("key", key);
        values.put("value", value == null ? "" : value);
        db.insertWithOnConflict("kv", null, values, SQLiteDatabase.CONFLICT_REPLACE);
    }

    private String storagePath() {
        return context.getDatabasePath(TaskDbHelper.DB_NAME).getAbsolutePath();
    }

    private TaskSummary parse(String fileName, String sourceDir, String json) {
        TaskSummary summary = new TaskSummary();
        summary.fileName = fileName;
        summary.sourceDir = sourceDir;
        summary.distanceKm = findDouble(MILEAGE, json);
        summary.durationSeconds = (int) findDouble(DURATION, json);
        summary.pace = findDouble(PACE, json);
        String beforeManage = json.contains("\"manageList\"") ? json.substring(0, json.indexOf("\"manageList\"")) : json;
        summary.pointsCount = count(beforeManage, "\"point\"");
        String manage = "";
        int start = json.indexOf("\"manageList\"");
        if (start >= 0) manage = json.substring(start);
        summary.manageCount = count(manage, "\"point\"");
        summary.fingerprint = fingerprint(json, summary);
        return summary;
    }

    private void applyMeta(TaskSummary summary, String fileName, JSONObject meta) {
        if (summary == null) return;
        JSONObject entry = filesMeta(meta).optJSONObject(fileName);
        if (entry == null) return;
        summary.duplicateCount = Math.max(1, entry.optInt("duplicateCount", 1));
        summary.useCount = Math.max(0, entry.optInt("useCount", 0));
        summary.fingerprint = entry.optString("fingerprint", summary.fingerprint);
    }

    private JSONObject readMeta() {
        if (!metaFile.isFile()) return emptyMeta();
        try (InputStream in = new FileInputStream(metaFile)) {
            return new JSONObject(new String(readAll(in), StandardCharsets.UTF_8));
        } catch (Exception e) {
            return emptyMeta();
        }
    }

    private JSONObject emptyMeta() {
        JSONObject meta = new JSONObject();
        try {
            meta.put("files", new JSONObject());
        } catch (Exception ignored) {
        }
        return meta;
    }

    private JSONObject filesMeta(JSONObject meta) {
        JSONObject files = meta.optJSONObject("files");
        if (files == null) {
            files = new JSONObject();
            try {
                meta.put("files", files);
            } catch (Exception ignored) {
            }
        }
        return files;
    }

    private String storageName(String fileName) {
        if (fileName == null) return "";
        return fileName.startsWith("extracted/") ? fileName.substring("extracted/".length()) : fileName;
    }

    private static String fingerprint(String json, TaskSummary summary) {
        String stable = stableTaskKey(json, summary);
        return sha256(stable == null || stable.trim().isEmpty() ? json : stable);
    }

    private static String stableTaskKey(String json, TaskSummary summary) {
        StringBuilder sb = new StringBuilder();
        if (summary != null) {
            sb.append(String.format(Locale.US, "m=%.3f;", summary.distanceKm));
            sb.append("d=").append(summary.durationSeconds).append(';');
            sb.append(String.format(Locale.US, "p=%.3f;", summary.pace));
            sb.append("pc=").append(summary.pointsCount).append(';');
            sb.append("mc=").append(summary.manageCount).append(';');
        }
        try {
            JSONObject root = new JSONObject(json);
            JSONObject data = root.optJSONObject("data");
            if (data == null) data = root;
            appendPoints(sb, data.optJSONArray("pointsList"), "points");
            appendPoints(sb, data.optJSONArray("manageList"), "manage");
            String managePoints = data.optString("managePoints", "");
            if (!managePoints.isEmpty()) sb.append("managePoints=").append(managePoints.trim()).append(';');
        } catch (Exception ignored) {
            appendRegexPoints(sb, json);
        }
        return sb.toString();
    }

    private static void appendPoints(StringBuilder sb, JSONArray points, String label) {
        if (points == null) return;
        sb.append(label).append('=');
        for (int i = 0; i < points.length(); i++) {
            JSONObject item = points.optJSONObject(i);
            if (item == null) continue;
            sb.append(normalizePoint(item.optString("point", ""))).append('|');
        }
        sb.append(';');
    }

    private static void appendRegexPoints(StringBuilder sb, String json) {
        if (json == null) return;
        Matcher matcher = Pattern.compile("\"point\"\\s*:\\s*\"([^\"]+)\"").matcher(json);
        sb.append("points=");
        while (matcher.find()) {
            sb.append(normalizePoint(matcher.group(1))).append('|');
        }
        sb.append(';');
    }

    private static String normalizePoint(String point) {
        if (point == null) return "";
        String[] parts = point.trim().split(",");
        if (parts.length < 2) return point.trim();
        try {
            double lng = Double.parseDouble(parts[0].trim());
            double lat = Double.parseDouble(parts[1].trim());
            return String.format(Locale.US, "%.6f,%.6f", lng, lat);
        } catch (Exception ignored) {
            return point.trim();
        }
    }

    private static String sha256(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest((value == null ? "" : value.trim()).getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : digest) sb.append(String.format(Locale.US, "%02x", b));
            return sb.toString();
        } catch (Exception e) {
            return Integer.toHexString((value == null ? "" : value.trim()).hashCode());
        }
    }

    private List<String> extractTaskJsons(String text) {
        Map<String, String> unique = new LinkedHashMap<>();
        if (text == null) return new ArrayList<>();
        List<String> variants = new ArrayList<>();
        variants.add(text);
        if (text.contains("\\\"recordMileage\\\"")) {
            variants.add(text.replace("\\\"", "\"").replace("\\n", "\n").replace("\\/", "/"));
        }
        for (String variant : variants) {
            collectWholeIfTask(variant, unique);
            collectObjectsAround("recordMileage", variant, unique);
            collectObjectsAround("\"recordMileage\"", variant, unique);
        }
        return new ArrayList<>(unique.values());
    }

    private void collectWholeIfTask(String text, Map<String, String> unique) {
        String trimmed = text == null ? "" : text.trim();
        if (!trimmed.startsWith("{") || !looksLikeTask(trimmed)) return;
        TaskSummary summary = parse("candidate.json", "导入候选", trimmed);
        if (isUsable(summary)) unique.put(summary.fingerprint, trimmed);
    }

    private void collectObjectsAround(String needle, String text, Map<String, String> unique) {
        int from = 0;
        while (true) {
            int at = text.indexOf(needle, from);
            if (at < 0) break;
            String object = smallestObjectContaining(text, at);
            if (object != null && looksLikeTask(object)) {
                TaskSummary summary = parse("candidate.json", "导入候选", object);
                if (isUsable(summary)) unique.put(summary.fingerprint, object);
            }
            from = at + needle.length();
        }
    }

    private String smallestObjectContaining(String text, int index) {
        for (int start = index; start >= 0; start--) {
            if (text.charAt(start) != '{') continue;
            int end = matchingBrace(text, start);
            if (end >= index) return text.substring(start, end + 1);
        }
        return null;
    }

    private int matchingBrace(String text, int start) {
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

    private boolean looksLikeTask(String json) {
        return json.contains("recordMileage")
                && json.contains("duration")
                && (json.contains("pointsList") || json.contains("\"point\""));
    }

    private boolean isUsable(TaskSummary summary) {
        return summary != null && summary.distanceKm > 0 && summary.durationSeconds > 0 && summary.pointsCount > 0;
    }

    private static double findDouble(Pattern pattern, String text) {
        Matcher matcher = pattern.matcher(text);
        if (!matcher.find()) return 0;
        try {
            return Double.parseDouble(matcher.group(1));
        } catch (Exception e) {
            return 0;
        }
    }

    private static int count(String text, String needle) {
        int count = 0;
        int from = 0;
        while (true) {
            int at = text.indexOf(needle, from);
            if (at < 0) break;
            count++;
            from = at + needle.length();
        }
        return count;
    }

    private static byte[] readAll(InputStream in) throws Exception {
        java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        int read;
        while ((read = in.read(buffer)) >= 0) {
            out.write(buffer, 0, read);
        }
        return out.toByteArray();
    }

    private static void clearDir(File dir) {
        File[] files = dir.listFiles();
        if (files == null) return;
        for (File file : files) {
            if (file.isDirectory()) clearDir(file);
            //noinspection ResultOfMethodCallIgnored
            file.delete();
        }
    }

    private static boolean blank(String value) {
        return value == null || value.trim().isEmpty();
    }

    public static class ImportResult {
        public final int candidates;
        public final int saved;
        public final int merged;
        public final String directory;
        public final String message;

        public ImportResult(int candidates, int saved, String directory, String message) {
            this(candidates, saved, 0, directory, message);
        }

        public ImportResult(int candidates, int saved, int merged, String directory, String message) {
            this.candidates = candidates;
            this.saved = saved;
            this.merged = merged;
            this.directory = directory;
            this.message = message;
        }
    }
}
