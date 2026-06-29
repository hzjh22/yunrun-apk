package com.runlog.data;

public class AppConfig {
    public static final String DEFAULT_SCHOOL_HOST = "http://sports.aiyyd.com:8080";
    public static final String DEFAULT_API_HOST = "http://sports.aiyyd.com:9001/api";
    public static final String DEFAULT_APP_EDITION = "3.6.2";
    public static final String DEFAULT_PUBLIC_KEY = "BDdKFsuBf51UObke1pEgfER17biBg/5r8slqE4s8oOa8lVesWgIUxsRc+AmZ72GcuJ56f7avnyJe3CJY4n00LU4=";
    public static final String DEFAULT_CIPHER_KEY = "JXhWGZjmhhXN+nt8nLpNxA==";
    public static final String DEFAULT_CIPHER_KEY_ENCRYPTED = "BGfbsG9EkXz5KeCva8E0MisBeS6bhBEDId3VXeIuBoiBMZU0Mosv7PqKsvqxZ3PjkUlsjzh09Se629SWW45XP4TIUeXoLpYzgk5fAMbg0VNVnXuLH9xVzdHAeM+1qJrgvwwkwio85/DnrP1aArvVQrw3N4xd5tugqQ==";
    public static final String DEFAULT_MD5_KEY = "pie0hDSfMRINRXc7s1UIXfkE";

    public String schoolId = "";
    public String schoolName = "";
    public String schoolHost = DEFAULT_SCHOOL_HOST;
    public String appEdition = DEFAULT_APP_EDITION;
    public String platform = "android";
    public String publicKey = DEFAULT_PUBLIC_KEY;
    public String cipherKey = DEFAULT_CIPHER_KEY;
    public String cipherKeyEncrypted = DEFAULT_CIPHER_KEY_ENCRYPTED;
    public String md5Key = DEFAULT_MD5_KEY;
    public String token = "";
    public String deviceId = "";
    public String deviceName = "";
    public String uuid = "";
    public String username = "";
    public String password = "";
    public String userType = "1";
    public String runMode = "DATA";
    public String pickMode = "RANDOM";
    public boolean driftEnabled = false;
    public String selectedTask = "";
    public boolean draft = true;

    public AppConfig copy() {
        AppConfig out = new AppConfig();
        out.schoolId = schoolId;
        out.schoolName = schoolName;
        out.schoolHost = schoolHost;
        out.appEdition = appEdition;
        out.platform = platform;
        out.publicKey = publicKey;
        out.cipherKey = cipherKey;
        out.cipherKeyEncrypted = cipherKeyEncrypted;
        out.md5Key = md5Key;
        out.token = token;
        out.deviceId = deviceId;
        out.deviceName = deviceName;
        out.uuid = uuid;
        out.username = username;
        out.password = password;
        out.userType = userType;
        out.runMode = runMode;
        out.pickMode = pickMode;
        out.driftEnabled = driftEnabled;
        out.selectedTask = selectedTask;
        out.draft = draft;
        return out;
    }

    public void merge(CandidateConfig candidate) {
        if (!blank(candidate.schoolId)) schoolId = candidate.schoolId;
        if (!blank(candidate.schoolName)) schoolName = candidate.schoolName;
        if (!blank(candidate.schoolHost)) schoolHost = candidate.schoolHost;
        if (!blank(candidate.appEdition)) appEdition = candidate.appEdition;
        if (!blank(candidate.platform)) platform = candidate.platform;
        if (!blank(candidate.token)) token = candidate.token;
        if (!blank(candidate.deviceId)) deviceId = candidate.deviceId;
        if (!blank(candidate.deviceName)) deviceName = candidate.deviceName;
        if (!blank(candidate.uuid)) uuid = candidate.uuid;
    }

    static boolean blank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
