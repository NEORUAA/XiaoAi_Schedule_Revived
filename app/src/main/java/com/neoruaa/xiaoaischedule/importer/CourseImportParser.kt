package com.neoruaa.xiaoaischedule.importer

import org.json.JSONArray
import org.json.JSONObject
import java.net.URLDecoder
import java.time.LocalDate
import java.time.ZoneId

object CourseImportParser {
    fun parseToPreviewPayload(
        raw: String,
        schoolName: String = "小爱课程表",
        source: String = "xiaoaischedule",
        projectStatus: String = "native",
        projectId: String = "0",
    ): ImportPreviewPayload {
        val root = normalizeRoot(raw)
        val coursesArray = extractCoursesArray(root)
        val courses = buildList {
            for (index in 0 until coursesArray.length()) {
                val item = coursesArray.optJSONObject(index) ?: continue
                add(parseCourse(item))
            }
        }
        val timerRes = extractTimerRes(root)
        val courseConfig = normalizeCourseConfig(root.optJSONObject("schedule") ?: root.optJSONObject("config")).orEmpty()
        return ImportPreviewPayload(
            schoolName = root.optString("schoolName").ifBlank { schoolName },
            courses = courses,
            timerRes = timerRes,
            courseConfig = courseConfig,
            source = root.optString("source").ifBlank { source },
            projectStatus = root.optString("status").ifBlank { projectStatus },
            projectId = root.optString("id").ifBlank { projectId },
        )
    }

    fun parseCourse(json: JSONObject): ImportCourseDraft {
        val name = firstString(json, "name", "courseName", "title", "subject").trim()
        val teacher = firstString(json, "teacher", "instructor", "teachers", "teacherName")
        val position = firstString(json, "position", "location", "classroom", "room", "address")
        val day = parseDay(firstAny(json, "day", "weekday", "weekDay", "week", "xqj"))
        val sectionsResult = parseSections(json)
        val weeks = parseWeeks(firstAny(json, "weeks", "week", "weekList", "weekIndexes", "rawWeeks", "zcd"))
        val reasons = mutableListOf<String>()
        if (name.isBlank()) reasons += "课程名为空"
        if (day !in 1..7) reasons += "星期无效"
        if (sectionsResult.sections.isEmpty()) reasons += "节次为空"
        if (weeks.isEmpty()) reasons += "周次为空"
        return ImportCourseDraft(
            name = name,
            teacher = teacher,
            position = position,
            day = day,
            sections = sectionsResult.sections.distinct().sorted(),
            weeks = weeks.distinct().sorted(),
            isCustomTime = json.optBoolean("isCustomTime", false),
            customStartTime = firstString(json, "customStartTime", "startTime"),
            customEndTime = firstString(json, "customEndTime", "endTime"),
            hasExplicitSectionRange = sectionsResult.explicitRange,
            repaired = sectionsResult.repaired || weeks.size != weeks.distinct().size,
            invalidReason = reasons.joinToString("；"),
        )
    }

    fun normalizeCourseConfig(json: JSONObject?): String? {
        json ?: return null
        val result = JSONObject()
        val start = firstString(json, "startSemester", "semesterStartDate", "startDate", "termStartDate")
        if (start.isNotBlank()) {
            val millis = parseDateMillis(start)
            if (millis > 0L) result.put("startSemester", millis)
        }
        val totalWeek = firstInt(json, "totalWeek", "semesterTotalWeeks", "weeks", "weekCount")
        if (totalWeek > 0) result.put("totalWeek", totalWeek)
        val weekStart = firstInt(json, "weekStart", "firstDayOfWeek")
        if (weekStart > 0) result.put("weekStart", weekStart)
        val presentWeek = firstInt(json, "presentWeek", "currentWeek")
        if (presentWeek > 0) result.put("presentWeek", presentWeek)
        return result.takeIf { it.length() > 0 }?.toString()
    }

    fun normalizeTimeSlots(raw: String): String {
        val value = runCatching { JSONObject(raw) }.getOrNull()?.let(::extractTimeSlotsArray)
            ?: runCatching { JSONArray(raw) }.getOrNull()
            ?: return raw
        val normalized = JSONArray()
        for (index in 0 until value.length()) {
            val item = value.optJSONObject(index) ?: continue
            val section = firstInt(item, "section", "number", "i", "index")
            val start = firstString(item, "startTime", "start", "s")
            val end = firstString(item, "endTime", "end", "e")
            if (section > 0 && start.isNotBlank() && end.isNotBlank()) {
                normalized.put(JSONObject().put("section", section).put("startTime", start).put("endTime", end))
            }
        }
        return normalized.toString()
    }

    private fun normalizeRoot(raw: String): JSONObject {
        val trimmed = raw.trim()
        val decoded = if (trimmed.startsWith("%7B") || trimmed.startsWith("%5B")) {
            runCatching { URLDecoder.decode(trimmed, Charsets.UTF_8.name()) }.getOrDefault(trimmed)
        } else {
            trimmed
        }
        val directObject = runCatching { JSONObject(decoded) }.getOrNull()
        if (directObject != null) {
            val storage = directObject.optJSONObject("storage")
            if (storage?.optString("key") == "presetData") {
                val preset = URLDecoder.decode(storage.optString("value"), Charsets.UTF_8.name())
                return normalizeRoot(preset)
            }
            val importDataString = directObject.optString("importData")
            if (importDataString.isNotBlank()) return normalizeRoot(importDataString)
            val parserResString = directObject.optString("parserRes")
            if (parserResString.trim().startsWith("[") || parserResString.trim().startsWith("{")) {
                return JSONObject().put("courses", toCourseArray(parserResString))
            }
            return directObject
        }
        val directArray = runCatching { JSONArray(decoded) }.getOrNull()
        if (directArray != null) return JSONObject().put("courses", directArray)
        throw IllegalArgumentException("无法解析课程 JSON")
    }

    private fun extractCoursesArray(root: JSONObject): JSONArray {
        root.optJSONArray("courses")?.let { return it }
        root.optJSONArray("parserRes")?.let { return it }
        root.optJSONObject("importData")?.let { return extractCoursesArray(it) }
        root.optString("parserRes").takeIf { it.isNotBlank() }?.let { return toCourseArray(it) }
        root.optString("courses").takeIf { it.isNotBlank() }?.let { return toCourseArray(it) }
        return JSONArray()
    }

    private fun toCourseArray(raw: String): JSONArray {
        val trimmed = raw.trim()
        return when {
            trimmed.startsWith("[") -> JSONArray(trimmed)
            trimmed.startsWith("{") -> extractCoursesArray(JSONObject(trimmed))
            else -> JSONArray()
        }
    }

    private fun extractTimerRes(root: JSONObject): String {
        val timer = root.opt("timerRes")
        return when (timer) {
            is JSONArray -> normalizeTimeSlots(timer.toString())
            is JSONObject -> normalizeTimeSlots(timer.toString())
            is String -> timer.takeIf { it.isNotBlank() }?.let { normalizeTimeSlots(it) }.orEmpty()
            else -> root.optJSONObject("schedule")?.let { normalizeScheduleTimer(it) }.orEmpty()
        }
    }

    private fun normalizeScheduleTimer(schedule: JSONObject): String {
        val sections = extractTimeSlotsArray(schedule) ?: return ""
        return normalizeTimeSlots(sections.toString())
    }

    private fun extractTimeSlotsArray(json: JSONObject): JSONArray? {
        json.optJSONArray("sections")?.let { return it }
        val schedules = json.optJSONArray("schedules")
        if (schedules != null && schedules.length() > 0) {
            schedules.optJSONObject(0)?.optJSONArray("sections")?.let { return it }
        }
        return null
    }

    private data class SectionResult(
        val sections: List<Int>,
        val explicitRange: Boolean,
        val repaired: Boolean,
    )

    private fun parseSections(json: JSONObject): SectionResult {
        val start = firstInt(json, "startSection", "sectionStart", "start")
        val end = firstInt(json, "endSection", "sectionEnd", "end")
        if (start > 0 && end >= start) {
            return SectionResult((start..end).toList(), explicitRange = true, repaired = false)
        }
        val value = firstAny(json, "sections", "section", "sectionList", "jc", "time")
        val parsed = parseRangeList(value, max = 30)
        return SectionResult(parsed, explicitRange = false, repaired = parsed.isNotEmpty() && value is String)
    }

    fun parseWeeks(value: Any?): List<Int> {
        return parseRangeList(value, max = 60, supportOddEven = true)
    }

    fun parseRangeList(value: Any?, max: Int, supportOddEven: Boolean = false): List<Int> {
        return when (value) {
            is JSONArray -> buildList {
                for (index in 0 until value.length()) addAll(parseRangeList(value.opt(index), max, supportOddEven))
            }
            is Number -> listOf(value.toInt()).filter { it in 1..max }
            is String -> parseRangeString(value, max, supportOddEven)
            else -> emptyList()
        }.distinct().sorted()
    }

    private fun parseRangeString(raw: String, max: Int, supportOddEven: Boolean): List<Int> {
        val text = raw
            .replace("第", "")
            .replace("周", "")
            .replace("节", "")
            .replace("星期", "")
            .replace("，", ",")
            .replace("、", ",")
            .replace("至", "-")
            .replace("到", "-")
            .replace("~", "-")
            .replace("－", "-")
        val oddOnly = supportOddEven && text.contains("单")
        val evenOnly = supportOddEven && text.contains("双")
        val result = mutableListOf<Int>()
        Regex("""\d+\s*(?:-\s*\d+)?""").findAll(text).forEach { match ->
            val token = match.value.replace(" ", "")
            if (token.contains("-")) {
                val parts = token.split("-")
                val start = parts.getOrNull(0)?.toIntOrNull() ?: return@forEach
                val end = parts.getOrNull(1)?.toIntOrNull() ?: return@forEach
                val range = if (start <= end) start..end else end..start
                result += range.filter { it in 1..max }
            } else {
                token.toIntOrNull()?.takeIf { it in 1..max }?.let(result::add)
            }
        }
        return result.filter {
            (!oddOnly || it % 2 == 1) && (!evenOnly || it % 2 == 0)
        }
    }

    private fun parseDay(value: Any?): Int {
        if (value is Number) return value.toInt()
        val text = value?.toString().orEmpty()
        val direct = text.toIntOrNull()
        if (direct != null) return direct
        return when {
            text.contains("一") || text.equals("mon", true) -> 1
            text.contains("二") || text.equals("tue", true) -> 2
            text.contains("三") || text.equals("wed", true) -> 3
            text.contains("四") || text.equals("thu", true) -> 4
            text.contains("五") || text.equals("fri", true) -> 5
            text.contains("六") || text.equals("sat", true) -> 6
            text.contains("日") || text.contains("天") || text.equals("sun", true) -> 7
            else -> 0
        }
    }

    private fun parseDateMillis(value: String): Long {
        value.toLongOrNull()?.let {
            return if (it < 10_000_000_000L) it * 1000 else it
        }
        return runCatching {
            LocalDate.parse(value.take(10))
                .atTime(8, 0)
                .atZone(ZoneId.systemDefault())
                .toInstant()
                .toEpochMilli()
        }.getOrDefault(0L)
    }

    private fun firstString(json: JSONObject, vararg keys: String): String {
        return keys.firstNotNullOfOrNull { key ->
            json.opt(key)?.takeIf { it != JSONObject.NULL }?.toString()?.takeIf { it.isNotBlank() }
        }.orEmpty()
    }

    private fun firstAny(json: JSONObject, vararg keys: String): Any? {
        return keys.firstNotNullOfOrNull { key ->
            json.opt(key)?.takeIf { it != JSONObject.NULL }
        }
    }

    private fun firstInt(json: JSONObject, vararg keys: String): Int {
        return keys.firstNotNullOfOrNull { key ->
            val value = json.opt(key)?.takeIf { it != JSONObject.NULL }
            when (value) {
                is Number -> value.toInt()
                is String -> value.toIntOrNull()
                else -> null
            }?.takeIf { it > 0 }
        } ?: 0
    }
}
