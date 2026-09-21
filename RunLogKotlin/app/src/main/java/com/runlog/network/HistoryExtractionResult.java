package com.runlog.network;

import java.util.ArrayList;
import java.util.List;

public class HistoryExtractionResult {
    public int terms;
    public int listRecords;
    public int detailRecords;
    public final List<String> taskJsons = new ArrayList<>();

    /**
     * Statistics for the <em>current (newest) semester only</em>.
     *
     * <p>The school interface {@code run/listXnYearXqByStudentId} returns terms
     * newest-first, so these are taken from the first term table — the semester the
     * student is actually being graded in right now. They come from the
     * {@code sumNumber} / {@code sumKm} / {@code morningNum} fields of
     * {@code run/crsReocordInfoList}, which the extractor already calls, so reading
     * them costs no extra requests.
     */
    public int currentRuns;
    public double currentKm;
    public int currentMorningRuns;
    /** Human-readable label of the newest term, e.g. "2026-2027 学年 第一学期". */
    public String currentTermLabel = "";
    /** Table name backing {@link #currentTermLabel}, e.g. {@code crs_run_record113}. */
    public String currentTermTable = "";

    /** Target the school set for this semester, when the home endpoint reported one. */
    public int targetRuns;

    public String summaryLine() {
        return currentTermLabel + "：" + currentRuns + " 次 · "
                + formatKm(currentKm) + " km";
    }

    public static String formatKm(double km) {
        return String.format(java.util.Locale.US, "%.2f", km);
    }
}
