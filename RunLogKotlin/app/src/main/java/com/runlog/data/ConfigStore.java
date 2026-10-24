package com.runlog.data;

import android.content.Context;
import android.content.SharedPreferences;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class ConfigStore {
    private static final String PREFS = "runlog_config";
    private final SharedPreferences prefs;
    private final File backupDir;

    public ConfigStore(Context context) {
        prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        backupDir = new File(context.getFilesDir(), "config_backups");
        if (!backupDir.exists()) backupDir.mkdirs();
    }

    public AppConfig load() {
        AppConfig config = new AppConfig();
        config.schoolId = prefs.getString("school_id", "");
        config.schoolName = prefs.getString("school_name", "");
        config.schoolHost = prefs.getString("school_host", AppConfig.DEFAULT_SCHOOL_HOST);
        // The server periodically raises its minimum accepted app edition and answers
        // code=911 "当前版本过低，请升级！" to anything older. A stored value therefore
        // goes stale on its own, so never let a saved edition older than the build's
        // default win — otherwise an upgraded APK keeps sending the rejected version.
        // The corrected value is written back so the stored config matches what is
        // actually being sent, which keeps the settings screen honest.
        String storedEdition = prefs.getString("app_edition", AppConfig.DEFAULT_APP_EDITION);
        config.appEdition = maxEdition(storedEdition, AppConfig.DEFAULT_APP_EDITION);
        if (!config.appEdition.equals(storedEdition)) {
            prefs.edit().putString("app_edition", config.appEdition).apply();
        }
        config.platform = prefs.getString("platform", "android");
        config.publicKey = getOrDefault("public_key", AppConfig.DEFAULT_PUBLIC_KEY);
        config.cipherKey = getOrDefault("cipher_key", AppConfig.DEFAULT_CIPHER_KEY);
        config.cipherKeyEncrypted = getOrDefault("cipher_key_encrypted", AppConfig.DEFAULT_CIPHER_KEY_ENCRYPTED);
        config.md5Key = getOrDefault("md5_key", AppConfig.DEFAULT_MD5_KEY);
        config.token = prefs.getString("token", "");
        config.deviceId = prefs.getString("device_id", "");
        config.deviceName = prefs.getString("device_name", "");
        config.uuid = prefs.getString("uuid", "");
        config.username = prefs.getString("username", "");
        config.password = prefs.getString("password", "");
        config.userType = prefs.getString("user_type", "1");
        config.runMode = normalizeRunMode(prefs.getString("run_mode", "DATA"));
        config.pickMode = prefs.getString("pick_mode", "RANDOM");
        config.driftEnabled = prefs.getBoolean("drift_enabled", false);
        config.selectedTask = prefs.getString("selected_task", "");
        config.draft = prefs.getBoolean("draft", true);
        return config;
    }

    public File save(AppConfig config, boolean backup) throws Exception {
        File backupFile = null;
        if (backup) {
            backupFile = backupCurrent();
        }
        prefs.edit()
                .putString("school_id", nn(config.schoolId))
                .putString("school_name", nn(config.schoolName))
                .putString("school_host", nn(config.schoolHost))
                .putString("app_edition", nn(config.appEdition))
                .putString("platform", nn(config.platform))
                .putString("public_key", nn(config.publicKey))
                .putString("cipher_key", nn(config.cipherKey))
                .putString("cipher_key_encrypted", nn(config.cipherKeyEncrypted))
                .putString("md5_key", nn(config.md5Key))
                .putString("token", nn(config.token))
                .putString("device_id", nn(config.deviceId))
                .putString("device_name", nn(config.deviceName))
                .putString("uuid", nn(config.uuid))
                .putString("username", nn(config.username))
                .putString("password", nn(config.password))
                .putString("user_type", nn(config.userType))
                .putString("run_mode", normalizeRunMode(config.runMode))
                .putString("pick_mode", nn(config.pickMode))
                .putBoolean("drift_enabled", config.driftEnabled)
                .putString("selected_task", nn(config.selectedTask))
                .putBoolean("draft", config.draft)
                .apply();
        return backupFile;
    }

    public File backupCurrent() throws Exception {
        AppConfig current = load();
        String name = "config_backup_" + new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(new Date()) + ".json";
        File file = new File(backupDir, name);
        try (FileOutputStream out = new FileOutputStream(file)) {
            out.write(toJson(current).getBytes(StandardCharsets.UTF_8));
        }
        return file;
    }

    public void clearAll() {
        prefs.edit().clear().apply();
        deleteChildren(backupDir);
    }

    public String toDisplay(AppConfig config) {
        return "schoolId: " + nn(config.schoolId) +
                "\nschoolName: " + nn(config.schoolName) +
                "\nappEdition: " + nn(config.appEdition) +
                "\nplatform: " + nn(config.platform) +
                "\ntoken: " + Masking.value(config.token) +
                "\ndeviceId: " + Masking.value(config.deviceId) +
                "\ndeviceName: " + nn(config.deviceName) +
                "\nuuid: " + Masking.value(config.uuid) +
                "\n运行模式: " + runModeText(config.runMode) +
                "\n数据选择: " + pickModeText(config.pickMode) +
                "\ndraft: " + config.draft;
    }

    private String toJson(AppConfig c) {
        return "{\n" +
                "  \"school_id\": \"" + esc(c.schoolId) + "\",\n" +
                "  \"school_name\": \"" + esc(c.schoolName) + "\",\n" +
                "  \"school_host\": \"" + esc(c.schoolHost) + "\",\n" +
                "  \"app_edition\": \"" + esc(c.appEdition) + "\",\n" +
                "  \"platform\": \"" + esc(c.platform) + "\",\n" +
                "  \"token\": \"" + esc(c.token) + "\",\n" +
                "  \"device_id\": \"" + esc(c.deviceId) + "\",\n" +
                "  \"device_name\": \"" + esc(c.deviceName) + "\",\n" +
                "  \"uuid\": \"" + esc(c.uuid) + "\",\n" +
                "  \"run_mode\": \"" + esc(c.runMode) + "\",\n" +
                "  \"pick_mode\": \"" + esc(c.pickMode) + "\",\n" +
                "  \"draft\": " + c.draft + "\n" +
                "}\n";
    }

    private static String esc(String value) {
        return nn(value).replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private String getOrDefault(String key, String defaultValue) {
        String value = prefs.getString(key, defaultValue);
        if (value == null || value.trim().isEmpty()) {
            return defaultValue;
        }
        return value;
    }

    /**
     * Returns whichever dotted version string is numerically newer.
     *
     * <p>Used so a stored app edition cannot regress below the one this build was
     * compiled against. Compares segment by segment, padding the shorter side with
     * zeros, so "3.6.10" correctly beats "3.6.7". Falls back to the build default
     * whenever either side is not a plain dotted number.
     */
    private static String maxEdition(String stored, String buildDefault) {
        if (stored == null || stored.trim().isEmpty()) return buildDefault;
        String a = stored.trim();
        String b = buildDefault == null ? "" : buildDefault.trim();
        if (b.isEmpty()) return a;
        String[] pa = a.split("\\.");
        String[] pb = b.split("\\.");
        int len = Math.max(pa.length, pb.length);
        for (int i = 0; i < len; i++) {
            int va = segment(pa, i);
            int vb = segment(pb, i);
            if (va < 0 || vb < 0) return b;   // not a dotted numeric version -> trust build
            if (va > vb) return a;
            if (va < vb) return b;
        }
        return b;   // equal -> build default
    }

    private static int segment(String[] parts, int index) {
        if (index >= parts.length) return 0;
        String p = parts[index].trim();
        if (p.isEmpty()) return 0;
        for (int i = 0; i < p.length(); i++) {
            if (!Character.isDigit(p.charAt(i))) return -1;
        }
        try {
            return Integer.parseInt(p);
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    private static String normalizeRunMode(String value) {
        if ("TABLE".equalsIgnoreCase(value) || "QUICK".equalsIgnoreCase(value)) {
            return "DATA";
        }
        return value == null || value.trim().isEmpty() ? "DATA" : value;
    }

    private static String runModeText(String value) {
        return "DATA".equalsIgnoreCase(value) ? "历史数据模式" : nn(value);
    }

    private static String pickModeText(String value) {
        return "SPECIFIED".equalsIgnoreCase(value) ? "指定数据" : "随机数据";
    }

    private static void deleteChildren(File dir) {
        if (dir == null || !dir.exists() || !dir.isDirectory()) return;
        File[] files = dir.listFiles();
        if (files == null) return;
        for (File file : files) {
            if (file.isDirectory()) deleteChildren(file);
            //noinspection ResultOfMethodCallIgnored
            file.delete();
        }
    }

    private static String nn(String value) {
        return value == null ? "" : value;
    }
}
