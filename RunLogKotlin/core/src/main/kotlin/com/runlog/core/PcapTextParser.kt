package com.runlog.core

data class CaptureCandidate(
    val requestLine: String,
    val score: Int,
    val fields: CandidateConfig
)

data class CaptureParseResult(
    val best: CaptureCandidate,
    val candidates: List<CaptureCandidate>
)

object PcapTextParser {
    private val requestLineRegex =
        Regex("^(GET|POST|PUT|DELETE|PATCH|HEAD|OPTIONS)\\s+(\\S+)\\s+HTTP/\\d(?:\\.\\d)?$", RegexOption.IGNORE_CASE)
    private val headerRegex = Regex("^([A-Za-z][A-Za-z0-9_-]*):\\s*(.*)$")

    fun parse(text: String): CaptureParseResult {
        val candidates = iterHttpMessages(text)
            .mapNotNull { parseMessage(it) }
            .mapNotNull { (requestLine, headers) -> candidateFrom(requestLine, headers) }
            .sortedByDescending { it.score }

        require(candidates.isNotEmpty()) { "没有在抓包文本中找到可用 HTTP 请求头" }
        return CaptureParseResult(candidates.first(), candidates)
    }

    private fun iterHttpMessages(text: String): List<List<String>> {
        val messages = mutableListOf<List<String>>()
        var current = mutableListOf<String>()
        for (line in text.lineSequence()) {
            if (requestLineRegex.matches(line.trim()) && current.isNotEmpty()) {
                messages += current
                current = mutableListOf(line)
            } else {
                current += line
            }
        }
        if (current.isNotEmpty()) messages += current
        return messages
    }

    private fun parseMessage(lines: List<String>): Pair<String, Map<String, String>>? {
        var requestLine = ""
        val headers = linkedMapOf<String, String>()
        var inHeaders = false
        for (raw in lines) {
            val line = raw.trim('\uFEFF', '\r', '\n')
            if (line.isBlank()) {
                if (inHeaders) break
                continue
            }
            if (requestLineRegex.matches(line)) {
                requestLine = line
                inHeaders = true
                continue
            }
            if (!inHeaders) continue
            val match = headerRegex.matchEntire(line) ?: continue
            headers[match.groupValues[1].lowercase()] = match.groupValues[2].trim()
        }
        if (requestLine.isBlank() || headers.isEmpty()) return null
        return requestLine to headers
    }

    private fun candidateFrom(requestLine: String, headers: Map<String, String>): CaptureCandidate? {
        val host = headers["host"].orEmpty()
        val config = CandidateConfig(
            schoolHost = normalizeSchoolHost(apiBaseFromRequest(requestLine, host), host),
            appEdition = headers["version"].orEmpty(),
            platform = headers["platform"].orEmpty(),
            token = headers["token"].orEmpty(),
            deviceId = headers["deviceid"].orEmpty(),
            deviceName = headers["devicename"].orEmpty(),
            uuid = headers["uuid"].orEmpty(),
            requestHost = host,
            requestLine = requestLine
        )
        if (listOf(config.token, config.deviceId, config.deviceName, config.appEdition, config.platform, config.uuid, host).all { it.isBlank() }) {
            return null
        }
        return CaptureCandidate(requestLine, score(headers), config)
    }

    private fun score(headers: Map<String, String>): Int {
        var score = 0
        for (key in listOf("token", "deviceid", "devicename", "version", "platform", "uuid", "utc", "sign")) {
            if (!headers[key].isNullOrBlank()) score += 1
        }
        val host = headers["host"].orEmpty()
        if (host.endsWith(":9001")) score += 2
        if ("sports.aiyyd.com" in host) score += 1
        return score
    }

    private fun apiBaseFromRequest(requestLine: String, host: String): String {
        if (host.isBlank()) return ""
        val scheme = if (host.endsWith(":443")) "https" else "http"
        val path = requestLineRegex.matchEntire(requestLine)?.groupValues?.getOrNull(2).orEmpty()
        val base = "$scheme://$host"
        return if (path == "/api" || path.startsWith("/api/")) "$base/api" else base
    }

    fun normalizeSchoolHost(base: String, host: String = ""): String {
        val value = base.trim().trimEnd('/')
        if (host.endsWith(":9001") || value.endsWith(":9001/api") || value.endsWith(":9001")) {
            return DefaultSchoolHost
        }
        return value.ifBlank { DefaultSchoolHost }
    }
}
