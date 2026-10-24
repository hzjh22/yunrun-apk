package com.runlog.core

data class AppConfig(
    val schoolId: String = "",
    val schoolName: String = "",
    val schoolHost: String = DefaultSchoolHost,
    val appEdition: String = DefaultAppEdition,
    val platform: String = "android",
    val publicKey: String = "",
    val cipherKey: String = "",
    val md5Key: String = "",
    val token: String = "",
    val deviceId: String = "",
    val deviceName: String = "",
    val uuid: String = ""
) {
    fun merged(candidate: CandidateConfig): AppConfig = copy(
        schoolHost = candidate.schoolHost.ifBlank { schoolHost },
        appEdition = candidate.appEdition.ifBlank { appEdition },
        platform = candidate.platform.ifBlank { platform },
        token = candidate.token.ifBlank { token },
        deviceId = candidate.deviceId.ifBlank { deviceId },
        deviceName = candidate.deviceName.ifBlank { deviceName },
        uuid = candidate.uuid.ifBlank { uuid }
    )
}

// Fixed 2026-09-19 by local runtime verification: :8080 and :9001 are dead
// (HTTP 502 / TLS fail) and appEdition 3.6.2 is rejected by the server
// ("版本过低，请前往应用市场更新！"). Verified live values below.
//
// Updated 2026-10-24: the server started enforcing a newer minimum on
// 2026-10-20 and replies code=911 "当前版本过低，请升级！" to 3.6.6. The upgrade
// payload reports appEdtitionNumber=65, which is the official 3.6.7 build.
const val DefaultSchoolHost = "https://sports.aiyyd.com:8000"
const val DefaultApiHost = "https://sports.aiyyd.com:9011/api"
const val DefaultAppEdition = "3.6.7"

enum class RunMode { QUICK, TABLE }
enum class PickMode { RANDOM, SPECIFIED }

data class CandidateConfig(
    val schoolHost: String = "",
    val appEdition: String = "",
    val platform: String = "",
    val token: String = "",
    val deviceId: String = "",
    val deviceName: String = "",
    val uuid: String = "",
    val requestHost: String = "",
    val requestLine: String = ""
)

data class ConfigValidationResult(
    val valid: Boolean,
    val missingFields: List<String>,
    val warnings: List<String>
) {
    val summary: String
        get() = buildString {
            append(if (valid) "基础配置通过" else "基础配置未通过")
            if (missingFields.isNotEmpty()) append("\n缺失: ${missingFields.joinToString()}")
            if (warnings.isNotEmpty()) append("\n提示: ${warnings.joinToString()}")
        }
}

object ConfigValidator {
    fun validate(config: AppConfig): ConfigValidationResult {
        val missing = mutableListOf<String>()
        fun require(name: String, value: String, minLength: Int = 1) {
            if (value.trim().length < minLength) missing += name
        }

        require("school_host", config.schoolHost)
        require("platform", config.platform)
        require("app_edition", config.appEdition)
        require("token", config.token, 10)
        require("device_id", config.deviceId, 12)
        require("device_name", config.deviceName)
        require("uuid", config.uuid, 8)

        val warnings = mutableListOf<String>()
        if (!config.platform.equals("android", ignoreCase = true)) {
            warnings += "platform 建议为 android"
        }
        if (config.appEdition != DefaultAppEdition) {
            warnings += "当前版本 ${config.appEdition}，建议核对是否仍为 $DefaultAppEdition"
        }
        if (config.schoolHost.contains(":9001") || config.schoolHost.contains(":9011")) {
            warnings += "school_host 不应指向 API 网关端口，已知可用值为 $DefaultSchoolHost"
        }
        if (!looksLikeUuid(config.uuid)) {
            warnings += "uuid 格式不像标准 UUID，但仍允许继续检测接口"
        }

        return ConfigValidationResult(missing.isEmpty(), missing, warnings)
    }

    fun validateCandidate(candidate: CandidateConfig): ConfigValidationResult =
        validate(
            AppConfig(
                schoolHost = candidate.schoolHost,
                appEdition = candidate.appEdition,
                platform = candidate.platform,
                token = candidate.token,
                deviceId = candidate.deviceId,
                deviceName = candidate.deviceName,
                uuid = candidate.uuid
            )
        )

    private fun looksLikeUuid(value: String): Boolean {
        val text = value.trim()
        return text.length >= 32 && text.count { it == '-' } in 0..4
    }
}
