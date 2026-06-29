package com.runlog.task;

public class TaskSummary {
    public String fileName;
    public String sourceDir;
    public double distanceKm;
    public int durationSeconds;
    public double pace;
    public int pointsCount;
    public int manageCount;
    public int duplicateCount = 1;
    public int useCount = 0;
    public String fingerprint = "";

    public String distanceText() {
        return String.format(java.util.Locale.US, "%.2f km", distanceKm);
    }

    public String durationText() {
        return (durationSeconds / 60) + "分" + (durationSeconds % 60) + "秒";
    }

    public String paceText() {
        return pace > 0 ? String.format(java.util.Locale.US, "%.2f", pace) : "-";
    }
}
