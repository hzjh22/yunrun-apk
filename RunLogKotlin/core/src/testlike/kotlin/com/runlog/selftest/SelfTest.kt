package com.runlog.selftest

import com.runlog.core.AppConfig
import com.runlog.core.ConfigValidator
import com.runlog.core.DefaultSchoolHost
import com.runlog.core.Masking
import com.runlog.core.PcapTextParser
import com.runlog.core.SignUtils
import com.runlog.core.TaskSummaryParser

fun main() {
    val samplePcap = """
        POST /api/login/appLogin HTTP/1.1
        Host: sports.aiyyd.com:9001
        token: 1234567890abcdef
        deviceid: abcdef1234567890abcdef1234567890
        devicename: vivo(V2359A)
        version: 3.6.2
        platform: android
        uuid: BE4F7B9D-BA79-4046-A116-96B25CB95C43
        utc: 1781953823
        sign: abcdef

    """.trimIndent()

    val parsed = PcapTextParser.parse(samplePcap)
    check(parsed.candidates.size == 1) { "candidate count failed" }
    check(parsed.best.fields.schoolHost == DefaultSchoolHost) { "9001 host was not normalized" }
    check(parsed.best.fields.token == "1234567890abcdef") { "token extraction failed" }

    val validation = ConfigValidator.validateCandidate(parsed.best.fields)
    check(validation.valid) { "candidate should pass base validation: ${validation.summary}" }

    val bad = ConfigValidator.validate(AppConfig())
    check(!bad.valid && "token" in bad.missingFields) { "missing token should fail validation" }

    val masked = Masking.value("token", "1234567890abcdef")
    check(masked == "123456...cdef") { "masking failed: $masked" }

    val sign = SignUtils.requestSign("android", 1781953823, "ABC", "secret")
    check(sign.length == 32) { "md5 sign length failed" }

    val task = TaskSummaryParser.parse(
        "tasklist_0.json",
        "tasks_else",
        """{"data":{"recordMileage":2.84,"recodePace":5.61,"duration":956,"pointsList":[{"point":"1,2"},{"point":"3,4"}],"manageList":[{"point":"5,6"}]}}"""
    )
    check(task.distanceKm == 2.84) { "task distance failed" }
    check(task.durationSeconds == 956) { "task duration failed" }
    check(task.pointsCount == 2) { "task points failed: ${task.pointsCount}" }
    check(task.manageCount == 1) { "task manage count failed: ${task.manageCount}" }

    println("RunLog Kotlin selftest passed")
}

