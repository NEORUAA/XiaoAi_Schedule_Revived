package com.neoruaa.xiaoaischedule.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
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
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

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
        val deviceId = URLEncoder.encode(privacyStore.deviceId(), Charsets.UTF_8.name())
        val url = "${XiaoAiConstants.CourseInfoUrl}?deviceId=$deviceId&time=${System.currentTimeMillis() / 1000}"
        return runCatching {
            val connection = (URL(url).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 8000
                readTimeout = 8000
                accountRepository.authorizationWithScopeData().takeIf { it.isNotBlank() }?.let {
                    setRequestProperty("Authorization", it)
                }
            }
            connection.inputStream.bufferedReader().use { parseCourses(it.readText()) }
        }.getOrDefault(emptyList())
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
                views.setTextViewText(row.time, course.time)
                views.setTextViewText(row.title, course.title)
                views.setTextViewText(row.hint, course.hint)
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

    private fun parseCourses(raw: String): List<WidgetCourse> {
        val result = linkedMapOf<String, WidgetCourse>()

        fun JSONObject.firstString(vararg keys: String): String {
            return keys.firstNotNullOfOrNull { key ->
                optString(key).takeIf { it.isNotBlank() && it != "null" }
            }.orEmpty()
        }

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

    private data class RowIds(val root: Int, val time: Int, val title: Int, val hint: Int)
    private data class WidgetCourse(val title: String, val hint: String, val time: String)

    companion object {
        const val ActionRefresh = "com.neoruaa.xiaoaischedule.action.REFRESH_WIDGET"
    }
}
