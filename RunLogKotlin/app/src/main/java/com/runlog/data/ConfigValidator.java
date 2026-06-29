package com.runlog.data;

import java.util.ArrayList;
import java.util.List;

public final class ConfigValidator {
    private ConfigValidator() {
    }

    public static ConfigValidationResult validate(AppConfig config) {
        List<String> missing = new ArrayList<>();
        require(missing, "school_host", config.schoolHost, 1);
        require(missing, "platform", config.platform, 1);
        require(missing, "app_edition", config.appEdition, 1);
        require(missing, "token", config.token, 10);
        require(missing, "device_id", config.deviceId, 12);
        require(missing, "device_name", config.deviceName, 1);
        require(missing, "uuid", config.uuid, 8);

        List<String> warnings = new ArrayList<>();
        if (!"android".equalsIgnoreCase(trim(config.platform))) {
            warnings.add("platform 建议为 android");
        }
        if (!AppConfig.DEFAULT_APP_EDITION.equals(trim(config.appEdition))) {
            warnings.add("当前版本 " + trim(config.appEdition) + "，建议核对是否仍为 " + AppConfig.DEFAULT_APP_EDITION);
        }
        if (trim(config.schoolHost).contains(":9001")) {
            warnings.add("跑步接口不应使用 9001/api，已知可用值为 " + AppConfig.DEFAULT_SCHOOL_HOST);
        }
        if (!looksLikeUuid(config.uuid)) {
            warnings.add("uuid 格式不像标准 UUID，但仍允许继续检测接口");
        }
        return new ConfigValidationResult(missing.isEmpty(), missing, warnings);
    }

    public static ConfigValidationResult validate(CandidateConfig candidate) {
        AppConfig config = new AppConfig();
        config.schoolHost = candidate.schoolHost;
        config.appEdition = candidate.appEdition;
        config.platform = candidate.platform;
        config.token = candidate.token;
        config.deviceId = candidate.deviceId;
        config.deviceName = candidate.deviceName;
        config.uuid = candidate.uuid;
        return validate(config);
    }

    private static void require(List<String> missing, String name, String value, int minLength) {
        if (trim(value).length() < minLength) {
            missing.add(name);
        }
    }

    private static String trim(String value) {
        return value == null ? "" : value.trim();
    }

    private static boolean looksLikeUuid(String value) {
        String text = trim(value);
        int dashCount = 0;
        for (int i = 0; i < text.length(); i++) {
            if (text.charAt(i) == '-') dashCount++;
        }
        return text.length() >= 32 && dashCount <= 4;
    }
}
