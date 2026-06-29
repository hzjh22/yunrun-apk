package com.runlog.core

data class TaskSummary(
    val fileName: String,
    val sourceDir: String,
    val distanceKm: Double,
    val durationSeconds: Int,
    val pace: Double,
    val pointsCount: Int,
    val manageCount: Int
) {
    val distanceText: String get() = "%.2f km".format(distanceKm)
    val durationText: String get() = "${durationSeconds / 60}分${durationSeconds % 60}秒"
    val paceText: String get() = if (pace > 0) "%.2f".format(pace) else "-"
}

object TaskSummaryParser {
    private val mileageRegex = Regex("\"recordMileage\"\\s*:\\s*([0-9.]+)")
    private val durationRegex = Regex("\"duration\"\\s*:\\s*(\\d+)")
    private val paceRegex = Regex("\"recodePace\"\\s*:\\s*([0-9.]+)")
    private val pointRegex = Regex("\"point\"\\s*:")
    private val manageListRegex = Regex("\"manageList\"\\s*:\\s*\\[(.*?)]", RegexOption.DOT_MATCHES_ALL)

    fun parse(fileName: String, sourceDir: String, json: String): TaskSummary {
        val distance = mileageRegex.find(json)?.groupValues?.get(1)?.toDoubleOrNull() ?: 0.0
        val duration = durationRegex.find(json)?.groupValues?.get(1)?.toIntOrNull() ?: 0
        val pace = paceRegex.find(json)?.groupValues?.get(1)?.toDoubleOrNull() ?: 0.0
        val points = pointRegex.findAll(json.substringBefore("\"manageList\"")).count()
        val manageBlock = manageListRegex.find(json)?.groupValues?.get(1).orEmpty()
        val manages = pointRegex.findAll(manageBlock).count()
        return TaskSummary(fileName, sourceDir, distance, duration, pace, points, manages)
    }
}
