package com.runlog.data;

import java.util.ArrayList;
import java.util.List;

public class ConfigValidationResult {
    public final boolean valid;
    public final List<String> missingFields;
    public final List<String> warnings;

    public ConfigValidationResult(boolean valid, List<String> missingFields, List<String> warnings) {
        this.valid = valid;
        this.missingFields = missingFields;
        this.warnings = warnings;
    }

    public String summary() {
        StringBuilder builder = new StringBuilder();
        builder.append(valid ? "基础配置通过" : "基础配置未通过");
        if (!missingFields.isEmpty()) {
            builder.append("\n缺失: ").append(join(missingFields));
        }
        if (!warnings.isEmpty()) {
            builder.append("\n提示: ").append(join(warnings));
        }
        return builder.toString();
    }

    private static String join(List<String> values) {
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < values.size(); i++) {
            if (i > 0) builder.append(", ");
            builder.append(values.get(i));
        }
        return builder.toString();
    }

    static ConfigValidationResult of(boolean valid) {
        return new ConfigValidationResult(valid, new ArrayList<>(), new ArrayList<>());
    }
}
