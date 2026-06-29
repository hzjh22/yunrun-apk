package com.runlog.network;

import com.runlog.data.AppConfig;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.Locale;
import java.util.Random;

public class RunClient {
    private static final double STRIDES = 0.8;
    private static final int SPLIT_COUNT = 10;
    private final AppConfig config;
    private final ApiClient api;
    private final Random random = new Random();

    public RunClient(AppConfig config) {
        this.config = config;
        this.api = new ApiClient(config);
    }

    public RunResult runTable(String taskJson, ProgressCallback callback, StopCheck stopCheck) throws Exception {
        if (taskJson == null || taskJson.trim().isEmpty()) {
            throw new IllegalArgumentException("data mode requires one imported task JSON");
        }
        progress(callback, 8, "requesting /run/getHomeRunInfo");
        HomeInfo home = loadHomeInfo();
        checkStop(stopCheck);
        progress(callback, 22, "starting run record");
        StartInfo start = start(home);
        JSONObject data = taskData(taskJson);
        JSONArray points = data.optJSONArray("pointsList");
        if (points == null || points.length() == 0) {
            throw new IllegalArgumentException("task JSON has no pointsList");
        }
        int sent = 0;
        for (int from = 0; from < points.length(); from += SPLIT_COUNT) {
            checkStop(stopCheck);
            JSONArray group = new JSONArray();
            int to = Math.min(points.length(), from + SPLIT_COUNT);
            for (int i = from; i < to; i++) {
                JSONObject point = new JSONObject(points.getJSONObject(i).toString());
                point.put("runStatus", "1");
                point.put("isFence", "Y");
                point.put("isMock", false);
                point.put("ts", String.valueOf(System.currentTimeMillis() / 1000L));
                group.put(point);
            }
            if (group.length() < 2) continue;
            api.postGzipJson("run/splitPointCheating", buildSplit(home, start, data, group).toString());
            sent++;
            int percent = 25 + (int) (60.0 * to / points.length());
            progress(callback, percent, "submitted split " + sent + " (" + to + "/" + points.length() + ")");
            Thread.sleep(180);
        }
        checkStop(stopCheck);
        progress(callback, 90, "submitting data finish");
        String finish = api.post("run/finish", buildTableFinish(home, start, data).toString());
        progress(callback, 100, "data run finished, splits=" + sent);
        return new RunResult(start.recordId, sent, preview(finish));
    }

    private HomeInfo loadHomeInfo() throws Exception {
        String json = api.post("run/getHomeRunInfo", "");
        JSONObject root = new JSONObject(json);
        JSONObject data = root.optJSONObject("data");
        JSONArray cralist = data == null ? null : data.optJSONArray("cralist");
        if (cralist == null || cralist.length() == 0) {
            throw new IllegalStateException("getHomeRunInfo has no cralist: " + preview(json));
        }
        JSONObject item = cralist.getJSONObject(0);
        HomeInfo out = new HomeInfo();
        out.raType = item.optString("raType");
        out.raId = item.optString("id");
        out.schoolId = item.optString("schoolId", config.schoolId);
        out.raRunArea = item.optString("raRunArea");
        out.raMinDislikes = Math.max(1, item.optInt("raDislikes", 1));
        out.raCadenceMin = item.optInt("raCadenceMin", 160) + 30;
        out.raCadenceMax = item.optInt("raCadenceMax", 260) - 150;
        if (out.raCadenceMax < out.raCadenceMin) out.raCadenceMax = out.raCadenceMin + 20;
        String rawPoints = item.optString("points", "");
        out.homePoints = rawPoints.isEmpty() ? new String[0] : rawPoints.split("\\|");
        return out;
    }

    private StartInfo start(HomeInfo home) throws Exception {
        JSONObject body = new JSONObject();
        body.put("raRunArea", home.raRunArea);
        body.put("raType", home.raType);
        body.put("raId", home.raId);
        String json = api.post("run/start", body.toString());
        JSONObject root = new JSONObject(json);
        if (root.optInt("code") != 200) {
            throw new IllegalStateException("run/start failed: " + preview(json));
        }
        JSONObject data = root.optJSONObject("data");
        if (data == null) throw new IllegalStateException("run/start has no data: " + preview(json));
        StartInfo out = new StartInfo();
        out.recordStartTime = data.optString("recordStartTime");
        out.recordId = data.optString("id");
        out.userName = data.optString("studentId");
        return out;
    }

    private JSONObject buildTableFinish(HomeInfo home, StartInfo start, JSONObject data) throws Exception {
        JSONObject body = commonFinish(home, start);
        body.put("recordMileage", data.opt("recordMileage"));
        body.put("recodeCadence", String.valueOf(data.opt("recodeCadence")));
        body.put("recodePace", String.valueOf(data.opt("recodePace")));
        body.put("recodeDislikes", String.valueOf(data.opt("recodeDislikes")));
        body.put("duration", String.valueOf(data.opt("duration")));
        body.put("manageList", data.optJSONArray("manageList") == null ? new JSONArray() : data.optJSONArray("manageList"));
        return body;
    }

    private JSONObject commonFinish(HomeInfo home, StartInfo start) throws Exception {
        JSONObject body = new JSONObject();
        body.put("deviceName", config.deviceName);
        body.put("sysEdition", android.os.Build.VERSION.RELEASE);
        body.put("appEdition", config.appEdition);
        body.put("raIsStartPoint", "Y");
        body.put("raIsEndPoint", "Y");
        body.put("raRunArea", home.raRunArea);
        body.put("raId", String.valueOf(home.raId));
        body.put("raType", home.raType);
        body.put("id", String.valueOf(start.recordId));
        body.put("recordStartTime", start.recordStartTime);
        body.put("remake", "1");
        return body;
    }

    private JSONObject buildSplit(HomeInfo home, StartInfo start, JSONObject taskData, JSONArray points) throws Exception {
        JSONObject first = points.getJSONObject(0);
        JSONObject last = points.getJSONObject(points.length() - 1);
        double mileage = last.optDouble("runMileage") - first.optDouble("runMileage");
        double time = last.optDouble("runTime") - first.optDouble("runTime");
        JSONObject body = new JSONObject();
        body.put("StepNumber", ((int) mileage) / STRIDES);
        body.put("a", 0);
        body.put("b", JSONObject.NULL);
        body.put("c", JSONObject.NULL);
        body.put("mileage", mileage);
        body.put("orientationNum", 0);
        body.put("runSteps", randInt(home.raCadenceMin, home.raCadenceMax));
        body.put("cardPointList", points);
        body.put("simulateNum", 0);
        body.put("time", time);
        body.put("crsRunRecordId", start.recordId);
        body.put("speeds", String.valueOf(taskData.opt("recodePace")));
        body.put("schoolId", home.schoolId);
        body.put("strides", STRIDES);
        body.put("userName", start.userName);
        return body;
    }

    private static JSONObject taskData(String json) throws Exception {
        JSONObject root = new JSONObject(json);
        JSONObject data = root.optJSONObject("data");
        return data == null ? root : data;
    }

    private static void progress(ProgressCallback callback, int percent, String message) {
        if (callback != null) callback.onProgress(percent, message);
    }

    private static void checkStop(StopCheck stopCheck) throws InterruptedException {
        if (stopCheck != null && stopCheck.shouldStop()) throw new InterruptedException("run stopped");
    }

    private int randInt(int min, int max) {
        if (max <= min) return min;
        return min + random.nextInt(max - min + 1);
    }

    private static String format(double value) {
        return String.format(Locale.US, "%.2f", value);
    }

    private static String preview(String value) {
        if (value == null) return "";
        String flat = value.replace('\n', ' ').replace('\r', ' ');
        return flat.length() > 240 ? flat.substring(0, 240) + "..." : flat;
    }

    public interface ProgressCallback {
        void onProgress(int percent, String message);
    }

    public interface StopCheck {
        boolean shouldStop();
    }

    public static class RunResult {
        public final String recordId;
        public final int splitCount;
        public final String responsePreview;

        public RunResult(String recordId, int splitCount, String responsePreview) {
            this.recordId = recordId;
            this.splitCount = splitCount;
            this.responsePreview = responsePreview;
        }
    }

    private static class HomeInfo {
        String raType = "";
        String raId = "";
        String schoolId = "";
        String raRunArea = "";
        int raMinDislikes = 1;
        int raCadenceMin = 160;
        int raCadenceMax = 180;
        String[] homePoints = new String[0];
    }

    private static class StartInfo {
        String recordStartTime = "";
        String recordId = "";
        String userName = "";
    }
}
