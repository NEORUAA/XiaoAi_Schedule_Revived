package com.neoruaa.xiaoaischedule.importer

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CourseImportParserTest {
    @Test
    fun parseRangeListSupportsOddWeeks() {
        assertEquals(listOf(1, 3, 5), CourseImportParser.parseWeeks("1-6周 单周"))
    }

    @Test
    fun parseCoursesFromParserRes() {
        val raw = JSONObject()
            .put(
                "parserRes",
                """
                    [
                      {"name":"嵌入式系统原理及应用","teacher":"张三","position":"A101","day":4,"sections":"3-4","weeks":"1-16"}
                    ]
                """.trimIndent(),
            )
            .toString()
        val payload = CourseImportParser.parseToPreviewPayload(raw)
        assertEquals(1, payload.courses.size)
        assertEquals("嵌入式系统原理及应用", payload.courses.first().name)
        assertEquals(listOf(3, 4), payload.courses.first().sections)
        assertTrue(payload.courses.first().isValid)
    }
}
