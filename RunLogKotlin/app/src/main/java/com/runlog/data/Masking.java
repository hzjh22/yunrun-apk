package com.runlog.data;

public final class Masking {
    private Masking() {
    }

    public static String value(String raw) {
        if (raw == null || raw.trim().isEmpty()) return "";
        if (raw.length() <= 10) {
            return raw.substring(0, Math.min(2, raw.length())) + "***";
        }
        return raw.substring(0, 6) + "..." + raw.substring(raw.length() - 4);
    }
}
