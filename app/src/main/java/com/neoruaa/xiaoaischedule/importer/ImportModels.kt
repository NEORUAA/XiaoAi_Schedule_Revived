package com.neoruaa.xiaoaischedule.importer

import org.json.JSONArray
import org.json.JSONObject

enum class ImportSourceType {
    Common,
    School,
    Shiguang,
    Json,
    Ai,
}

data class ImportSourceItem(
    val name: String,
    val url: String,
    val type: ImportSourceType,
    val extra: String = "",
    val sortKey: String = "#",
    val adapters: List<ShiguangAdapter> = emptyList(),
)

data class ShiguangSchool(
    val id: String,
    val name: String,
    val initial: String,
    val resourceFolder: String,
    val adapters: List<ShiguangAdapter>,
) {
    val isPinned: Boolean
        get() = id == "GLOBAL_TOOLS" || name.contains("通用")
}

data class ShiguangAdapter(
    val adapterId: String,
    val adapterName: String,
    val importType: Int,
    val assetJsPath: String,
    val importUrl: String,
    val description: String,
    val maintainer: String,
)

data class ImportCourseDraft(
    val name: String,
    val teacher: String = "",
    val position: String = "",
    val day: Int,
    val sections: List<Int>,
    val weeks: List<Int>,
    val isCustomTime: Boolean = false,
    val customStartTime: String = "",
    val customEndTime: String = "",
    val hasExplicitSectionRange: Boolean = false,
    val repaired: Boolean = false,
    val invalidReason: String = "",
) {
    val isValid: Boolean
        get() = invalidReason.isBlank()

    fun toParserJson(): JSONObject {
        return JSONObject()
            .put("name", name)
            .put("teacher", teacher)
            .put("position", position)
            .put("day", day)
            .put("sections", sections.joinToString(","))
            .put("weeks", weeks.joinToString(","))
    }

    fun toPreviewJson(): JSONObject {
        return toParserJson()
            .put("isCustomTime", isCustomTime)
            .put("customStartTime", customStartTime)
            .put("customEndTime", customEndTime)
            .put("hasExplicitSectionRange", hasExplicitSectionRange)
            .put("repaired", repaired)
            .put("invalidReason", invalidReason)
    }

    companion object {
        fun fromPreviewJson(json: JSONObject): ImportCourseDraft {
            return ImportCourseDraft(
                name = json.optString("name"),
                teacher = json.optString("teacher"),
                position = json.optString("position"),
                day = json.optInt("day"),
                sections = parseIntList(json.opt("sections")),
                weeks = parseIntList(json.opt("weeks")),
                isCustomTime = json.optBoolean("isCustomTime"),
                customStartTime = json.optString("customStartTime"),
                customEndTime = json.optString("customEndTime"),
                hasExplicitSectionRange = json.optBoolean("hasExplicitSectionRange"),
                repaired = json.optBoolean("repaired"),
                invalidReason = json.optString("invalidReason"),
            )
        }
    }
}

data class ImportPreviewPayload(
    val schoolName: String = "小爱课程表",
    val courses: List<ImportCourseDraft>,
    val timerRes: String = "",
    val courseConfig: String = "",
    val source: String = "xiaoaischedule",
    val projectStatus: String = "native",
    val projectId: String = "0",
) {
    fun toJsonString(): String {
        val coursesArray = JSONArray()
        courses.forEach { coursesArray.put(it.toPreviewJson()) }
        return JSONObject()
            .put("schoolName", schoolName)
            .put("courses", coursesArray)
            .put("timerRes", timerRes)
            .put("courseConfig", courseConfig)
            .put("source", source)
            .put("projectStatus", projectStatus)
            .put("projectId", projectId)
            .toString()
    }

    fun toPresetData(): String {
        val parserRes = JSONArray()
        courses.filter { it.isValid }.forEach { parserRes.put(it.toParserJson()) }
        val importData = JSONObject()
            .put("isV2", true)
            .put("errorCode", 0)
            .put("schoolName", schoolName)
            .put("parserRes", parserRes)
            .put("timerRes", timerRes)
            .put("feedbackId", JSONObject.NULL)
            .put("source", source)
            .put("status", projectStatus)
            .put("id", projectId)
            .put("t", System.currentTimeMillis())
        return java.net.URLEncoder.encode(
            JSONObject().put("importData", importData.toString()).toString(),
            Charsets.UTF_8.name(),
        )
    }

    companion object {
        fun fromJsonString(raw: String): ImportPreviewPayload {
            val json = JSONObject(raw)
            val array = json.optJSONArray("courses") ?: JSONArray()
            val courses = buildList {
                for (index in 0 until array.length()) {
                    val item = array.optJSONObject(index) ?: continue
                    add(ImportCourseDraft.fromPreviewJson(item))
                }
            }
            return ImportPreviewPayload(
                schoolName = json.optString("schoolName", "小爱课程表"),
                courses = courses,
                timerRes = json.optString("timerRes"),
                courseConfig = json.optString("courseConfig"),
                source = json.optString("source", "xiaoaischedule"),
                projectStatus = json.optString("projectStatus", "native"),
                projectId = json.optString("projectId", "0"),
            )
        }
    }
}

private fun parseIntList(value: Any?): List<Int> {
    return when (value) {
        is JSONArray -> buildList {
            for (index in 0 until value.length()) value.optInt(index).takeIf { it > 0 }?.let(::add)
        }
        is String -> value.split(",", "，", " ")
            .mapNotNull { it.trim().toIntOrNull() }
        is Number -> listOf(value.toInt())
        else -> emptyList()
    }
}
