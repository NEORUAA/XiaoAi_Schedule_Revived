package com.neoruaa.xiaoaischedule.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.util.Log
import android.view.View
import android.widget.RemoteViews
import com.neoruaa.xiaoaischedule.MainActivity
import com.neoruaa.xiaoaischedule.R
import com.neoruaa.xiaoaischedule.account.AccountRepository
import com.neoruaa.xiaoaischedule.core.XiaoAiConstants
import com.neoruaa.xiaoaischedule.data.PrivacyStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.UUID

class CourseWidgetProvider : AppWidgetProvider() {
    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        appWidgetIds.forEach { widgetId ->
            appWidgetManager.updateAppWidget(widgetId, buildViews(context, emptyList(), loading = true))
            CoroutineScope(Dispatchers.IO).launch {
                val courses = fetchTodayCourses(context)
                appWidgetManager.updateAppWidget(widgetId, buildViews(context, courses, loading = false))
            }
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        if (intent.action == ActionRefresh) {
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(ComponentName(context, CourseWidgetProvider::class.java))
            onUpdate(context, manager, ids)
        }
    }

    private suspend fun fetchTodayCourses(context: Context): List<WidgetCourse> {
        val privacyStore = PrivacyStore(context)
        val accountRepository = AccountRepository(context, privacyStore)
        val authorization = accountRepository.authorizationWithScopeData()
        if (authorization.isBlank()) return emptyList()

        val multiTableCourses = runCatching {
            val tablesBody = requestBody(XiaoAiConstants.CourseMultiAuthTablesUrl, authorization)
            val tableId = currentTableId(tablesBody) ?: return@runCatching emptyList()
            val tableBody = requestBody(XiaoAiConstants.CourseMultiAuthTableUrl, authorization, "ctId" to tableId.toString())
            parseCurrentTableCourses(tableBody).also {
                Log.d(Tag, "multi table widget courses=${it.size}")
            }
        }.getOrElse {
            Log.w(Tag, "multi table widget refresh failed: ${it.javaClass.simpleName}")
            null
        }
        if (multiTableCourses != null) return multiTableCourses

        val deviceId = URLEncoder.encode(privacyStore.deviceId(), Charsets.UTF_8.name())
        val url = "${XiaoAiConstants.CourseInfoUrl}?deviceId=$deviceId&time=${System.currentTimeMillis() / 1000}"
        return runCatching {
            val connection = (URL(url).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 8000
                readTimeout = 8000
                setRequestProperty("Authorization", authorization)
            }
            connection.inputStream.bufferedReader().use { parseCourses(it.readText()) }
        }.getOrElse {
            Log.w(Tag, "legacy widget refresh failed: ${it.javaClass.simpleName}")
            emptyList()
        }
    }

    private fun buildViews(context: Context, courses: List<WidgetCourse>, loading: Boolean): RemoteViews {
        val views = RemoteViews(context.packageName, R.layout.layout_course_widget)
        views.setTextViewText(R.id.course_widget_head_title, context.getString(R.string.course_widget_title))
        views.setTextViewText(R.id.course_widget_head_time, SimpleDateFormat("M月d日", Locale.CHINA).format(Date()))
        views.setOnClickPendingIntent(R.id.course_widget_layout, mainPendingIntent(context))

        val displayCourses = when {
            loading -> listOf(WidgetCourse("", context.getString(R.string.course_widget_refresh), ""))
            courses.isEmpty() -> listOf(WidgetCourse(context.getString(R.string.course_no_course_title), context.getString(R.string.course_no_course_summary), ""))
            else -> courses.take(3)
        }

        val rows = listOf(
            RowIds(R.id.widget_row_1, R.id.widget_row_1_time, R.id.widget_row_1_title, R.id.widget_row_1_hint),
            RowIds(R.id.widget_row_2, R.id.widget_row_2_time, R.id.widget_row_2_title, R.id.widget_row_2_hint),
            RowIds(R.id.widget_row_3, R.id.widget_row_3_time, R.id.widget_row_3_title, R.id.widget_row_3_hint),
        )

        rows.forEachIndexed { index, row ->
            val course = displayCourses.getOrNull(index)
            views.setViewVisibility(row.root, if (course == null) View.GONE else View.VISIBLE)
            if (course != null) {
                val titleColor = if (course.inCurrentWeek) TitleColor else DisabledTextColor
                val secondaryColor = if (course.inCurrentWeek) SecondaryTextColor else DisabledTextColor
                views.setTextViewText(row.time, course.time)
                views.setTextViewText(row.title, course.title)
                views.setTextViewText(row.hint, course.hint)
                views.setTextColor(row.time, secondaryColor)
                views.setTextColor(row.title, titleColor)
                views.setTextColor(row.hint, secondaryColor)
            }
        }
        return views
    }

    private fun mainPendingIntent(context: Context): PendingIntent {
        val intent = Intent(context, MainActivity::class.java)
        return PendingIntent.getActivity(
            context,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun requestBody(url: String, authorization: String, vararg params: Pair<String, String>): String {
        val query = buildList {
            add("requestId" to UUID.randomUUID().toString().replace("-", ""))
            add("sourceName" to "course-app-lite")
            addAll(params)
        }.joinToString("&") { (key, value) ->
            "${URLEncoder.encode(key, Charsets.UTF_8.name())}=${URLEncoder.encode(value, Charsets.UTF_8.name())}"
        }
        val target = "$url?$query"
        val connection = (URL(target).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 8000
            readTimeout = 8000
            setRequestProperty("Authorization", authorization)
        }
        val code = connection.responseCode
        val stream = if (code in 200..299) connection.inputStream else connection.errorStream
        val body = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
        if (code !in 200..299) throw IOException("HTTP $code")
        return body
    }

    private fun currentTableId(raw: String): Long? {
        val root = JSONObject(raw)
        val data = root.optJSONArray("data") ?: root.optJSONArray("tables") ?: return null
        if (data.length() == 0) return null
        for (index in 0 until data.length()) {
            val table = data.optJSONObject(index) ?: continue
            if (table.optInt("current", 0) == 1) return table.optLong("id").takeIf { it > 0 }
        }
        return data.optJSONObject(0)?.optLong("id")?.takeIf { it > 0 }
    }

    private fun parseCurrentTableCourses(raw: String): List<WidgetCourse> {
        val root = JSONObject(raw)
        val data = root.optJSONObject("data") ?: root
        val setting = data.optJSONObject("setting") ?: JSONObject()
        val presentWeek = effectivePresentWeek(setting)
        val today = todayIndex()
        val sectionTimes = parseSectionTimes(setting.opt("sectionTimes"))
        val coursesJson = data.optJSONArray("courses") ?: data.optJSONArray("courseInfos") ?: JSONArray()
        val courses = buildList {
            for (index in 0 until coursesJson.length()) {
                val item = coursesJson.optJSONObject(index) ?: continue
                val day = item.optInt("day", item.optInt("weekDay", -1))
                if (day != today) continue

                val weeks = parseIntList(item.opt("weeks"))
                val inCurrentWeek = presentWeek <= 0 || weeks.isEmpty() || presentWeek in weeks

                val title = item.firstString("name", "courseName", "title", "lessonName", "subject")
                if (title.isBlank()) continue
                val sections = parseIntList(item.opt("sections"))
                val room = item.firstString("position", "classroom", "room", "place", "location")
                val teacher = item.firstString("teacher", "teacherName")
                val hint = listOf(room, teacher).filter { it.isNotBlank() }.joinToString(" ")
                add(
                    WidgetCourse(
                        title = title,
                        hint = hint,
                        time = formatSections(sections, sectionTimes),
                        sortSection = sections.minOrNull() ?: Int.MAX_VALUE,
                        inCurrentWeek = inCurrentWeek,
                    ),
                )
            }
        }.sortedBy { it.sortSection }
        Log.d(
            Tag,
            "parsed widget day=$today week=$presentWeek total=${coursesJson.length()} today=${courses.size} current=${courses.count { it.inCurrentWeek }}",
        )
        return courses
    }

    private fun todayIndex(): Int {
        val calendar = Calendar.getInstance(Locale.CHINA)
        return when (calendar.get(Calendar.DAY_OF_WEEK)) {
            Calendar.SUNDAY -> 7
            else -> calendar.get(Calendar.DAY_OF_WEEK) - 1
        }
    }

    private fun effectivePresentWeek(setting: JSONObject): Int {
        val rawPresentWeek = setting.optInt("presentWeek", 0)
        val semesterStart = semesterStartMillis(setting)
        if (semesterStart <= 0L) return rawPresentWeek

        val weekStart = setting.optInt("weekStart", 1).let { if (it == 7) 7 else 1 }
        val start = startOfWeekMillis(semesterStart, weekStart)
        val now = startOfWeekMillis(System.currentTimeMillis(), weekStart)
        val calculated = ((now - start) / WeekMillis).toInt() + 1
        val totalWeek = setting.optInt("totalWeek", 0)
        return if (totalWeek > 0 && calculated > totalWeek) totalWeek + 1 else calculated
    }

    private fun semesterStartMillis(setting: JSONObject): Long {
        val direct = setting.optString("startSemester")
            .trim()
            .trim('"')
            .toLongOrNull()
            ?: 0L
        if (direct > 0L) return direct
        val extend = setting.optString("extend")
        return runCatching {
            JSONObject(extend).optString("startSemester").trim().trim('"').toLongOrNull() ?: 0L
        }.getOrDefault(0L)
    }

    private fun startOfWeekMillis(timeMillis: Long, weekStart: Int): Long {
        val calendar = Calendar.getInstance(Locale.CHINA).apply {
            timeInMillis = timeMillis
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val currentDay = when (calendar.get(Calendar.DAY_OF_WEEK)) {
            Calendar.SUNDAY -> 7
            else -> calendar.get(Calendar.DAY_OF_WEEK) - 1
        }
        val daysBack = if (currentDay >= weekStart) {
            currentDay - weekStart
        } else {
            7 - (weekStart - currentDay)
        }
        calendar.add(Calendar.DAY_OF_MONTH, -daysBack)
        return calendar.timeInMillis
    }

    private fun parseSectionTimes(value: Any?): Map<Int, SectionTime> {
        val array = when (value) {
            is JSONArray -> value
            is String -> value.takeIf { it.trim().startsWith("[") }?.let { runCatching { JSONArray(it) }.getOrNull() }
            else -> null
        } ?: return emptyMap()
        return buildMap {
            for (index in 0 until array.length()) {
                val item = array.optJSONObject(index) ?: continue
                val section = item.optInt("i", item.optInt("index", item.optInt("section", 0)))
                val start = item.firstString("s", "start", "startTime")
                val end = item.firstString("e", "end", "endTime")
                if (section > 0 && start.isNotBlank() && end.isNotBlank()) {
                    put(section, SectionTime(start, end))
                }
            }
        }
    }

    private fun parseIntList(value: Any?): List<Int> {
        return when (value) {
            is JSONArray -> buildList {
                for (index in 0 until value.length()) value.optInt(index, -1).takeIf { it > 0 }?.let(::add)
            }
            is String -> value.split(",").mapNotNull { it.trim().toIntOrNull() }.filter { it > 0 }
            is Number -> listOf(value.toInt()).filter { it > 0 }
            else -> emptyList()
        }
    }

    private fun formatSections(sections: List<Int>, sectionTimes: Map<Int, SectionTime>): String {
        if (sections.isEmpty()) return ""
        val sorted = sections.sorted()
        val first = sorted.first()
        val last = sorted.last()
        val firstTime = sectionTimes[first]
        val lastTime = sectionTimes[last]
        if (firstTime != null && lastTime != null) return "${firstTime.start}-${lastTime.end}"
        return if (first == last) "第${first}节" else "第${first}-${last}节"
    }

    private fun parseCourses(raw: String): List<WidgetCourse> {
        val result = linkedMapOf<String, WidgetCourse>()

        fun walk(value: Any?) {
            when (value) {
                is JSONObject -> {
                    val title = value.firstString("courseName", "name", "title", "lessonName", "subject")
                    val time = value.firstString("timeQuantum", "courseTime", "time", "section", "lessonTime")
                    val room = value.firstString("classroom", "room", "place", "location")
                    val teacher = value.firstString("teacher", "teacherName")
                    if (title.isNotBlank()) {
                        val hint = listOf(room, teacher).filter { it.isNotBlank() }.joinToString(" ")
                            .ifBlank { value.firstString("summary", "desc") }
                        result.putIfAbsent("$time$title$hint", WidgetCourse(title, hint, time))
                    }
                    value.keys().forEach { walk(value.opt(it)) }
                }
                is JSONArray -> {
                    for (index in 0 until value.length()) walk(value.opt(index))
                }
            }
        }

        runCatching { walk(JSONObject(raw)) }
        return result.values.toList()
    }

    private fun JSONObject.firstString(vararg keys: String): String {
        return keys.firstNotNullOfOrNull { key ->
            optString(key).takeIf { it.isNotBlank() && it != "null" }
        }.orEmpty()
    }

    private data class RowIds(val root: Int, val time: Int, val title: Int, val hint: Int)
    private data class WidgetCourse(
        val title: String,
        val hint: String,
        val time: String,
        val sortSection: Int = Int.MAX_VALUE,
        val inCurrentWeek: Boolean = true,
    )
    private data class SectionTime(val start: String, val end: String)

    companion object {
        private const val Tag = "CourseWidget"
        const val ActionRefresh = "com.neoruaa.xiaoaischedule.action.REFRESH_WIDGET"
        private const val WeekMillis = 7L * 24L * 60L * 60L * 1000L
        private val TitleColor = Color.argb(230, 0, 0, 0)
        private val SecondaryTextColor = Color.rgb(140, 147, 176)
        private val DisabledTextColor = Color.argb(128, 140, 147, 176)

        fun requestRefresh(context: Context) {
            context.applicationContext.sendBroadcast(
                Intent(context.applicationContext, CourseWidgetProvider::class.java).setAction(ActionRefresh),
            )
        }
    }
}
