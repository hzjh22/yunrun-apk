package com.runlog.core

import java.security.MessageDigest

object SignUtils {
    fun md5Lower(text: String): String {
        val bytes = MessageDigest.getInstance("MD5").digest(text.toByteArray(Charsets.UTF_8))
        return bytes.joinToString("") { "%02x".format(it) }
    }

    fun requestSign(platform: String, utc: Long, uuid: String, md5Key: String): String {
        return md5Lower("platform=$platform&utc=$utc&uuid=$uuid&appsecret=$md5Key")
    }
}

