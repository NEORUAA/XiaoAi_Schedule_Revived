package com.neoruaa.xiaoaischedule.importer

import org.junit.Assert.assertEquals
import org.junit.Test

class ShiguangIndexParserTest {
    @Test
    fun parseRoundTripSchoolIndex() {
        val school = ShiguangSchool(
            id = "GLOBAL_TOOLS",
            name = "通用工具",
            initial = "#",
            resourceFolder = "GLOBAL_TOOLS",
            adapters = listOf(
                ShiguangAdapter(
                    adapterId = "json",
                    adapterName = "JSON导入",
                    importType = 1,
                    assetJsPath = "GLOBAL_TOOLS/json.js",
                    importUrl = "https://example.com",
                    description = "测试",
                    maintainer = "Mercury",
                ),
            ),
        )
        val parsed = ShiguangIndexParser.parse(ShiguangIndexParser.encodeForTest(listOf(school)))
        assertEquals(1, parsed.size)
        assertEquals("通用工具", parsed.first().name)
        assertEquals("JSON导入", parsed.first().adapters.first().adapterName)
    }
}
