package com.runlog.core

object Masking {
    fun value(name: String, raw: String): String {
        if (raw.isBlank()) return ""
        val key = name.lowercase()
        if (key in setOf("token", "device_id", "deviceid", "uuid", "sign")) {
            return if (raw.length <= 10) raw.take(2) + "***" else raw.take(6) + "..." + raw.takeLast(4)
        }
        return raw
    }
}

