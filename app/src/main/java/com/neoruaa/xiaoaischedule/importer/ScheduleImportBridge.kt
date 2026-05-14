package com.neoruaa.xiaoaischedule.importer

import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.webkit.JavascriptInterface
import android.webkit.WebView
import android.widget.EditText
import android.widget.Toast
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLDecoder
import java.util.concurrent.CountDownLatch
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference

class ScheduleImportBridge(
    private val context: Context,
    private val webView: WebView,
    private val repository: ScheduleImportRepository,
    private val scope: CoroutineScope,
    private val defaultSchoolName: String,
    private val defaultSource: String,
    private val onStatus: (String) -> Unit,
) {
    private val mainHandler = Handler(Looper.getMainLooper())
    private var courseConfig: String = ""
    private var presetTimeSlots: String = ""

    @JavascriptInterface
    fun postData(raw: String?) {
        if (raw.isNullOrBlank()) return
        runCatching {
            val json = JSONObject(raw)
            if (json.has("courses") || json.has("parserRes") || json.has("importData")) {
                saveImportedCourses(raw, "postData_${System.currentTimeMillis()}")
                return
            }
            val storage = json.optJSONObject("storage")
            if (storage?.optString("key") == "presetData") {
                val decoded = URLDecoder.decode(storage.optString("value"), Charsets.UTF_8.name())
                saveImportedCourses(decoded, "postData_${System.currentTimeMillis()}")
                return
            }
            showToast("脚本返回的数据格式无法识别")
        }.onFailure {
            reportError("postData parse failed: ${it.message}")
        }
    }

    @JavascriptInterface
    fun postHtml(html: String?) {
        if (html.isNullOrBlank()) {
            showToast("页面源码为空")
            return
        }
        onStatus("接收到源码，正在 AI 解析...")
        scope.launch(Dispatchers.Main) {
            runCatching {
                AiScheduleParser().parseHtml(
                    html = html,
                    apiUrl = repository.aiApiUrl(),
                    model = repository.aiModel(),
                    apiKey = repository.aiApiKey(),
                )
            }.onSuccess {
                openPreview(it)
            }.onFailure {
                onStatus("AI 解析失败：${it.message.orEmpty()}")
                showToast("AI 解析失败：${it.message.orEmpty()}")
            }
        }
    }

    @JavascriptInterface
    fun saveImportedCourses(raw: String?) {
        saveImportedCourses(raw, "")
    }

    @JavascriptInterface
    fun saveImportedCourses(raw: String?, promiseId: String?) {
        if (raw.isNullOrBlank()) {
            reject(promiseId, "课程数据为空")
            return
        }
        onStatus("正在解析课程数据...")
        runCatching {
            CourseImportParser.parseToPreviewPayload(
                raw = raw,
                schoolName = defaultSchoolName,
                source = defaultSource,
            ).copy(
                timerRes = presetTimeSlots.ifBlank {
                    CourseImportParser.parseToPreviewPayload(raw, defaultSchoolName, defaultSource).timerRes
                },
                courseConfig = courseConfig,
            )
        }.onSuccess {
            resolve(promiseId, "true", quote = false)
            openPreview(it)
        }.onFailure {
            onStatus("课程解析失败：${it.message.orEmpty()}")
            reject(promiseId, it.message ?: "课程解析失败")
        }
    }

    @JavascriptInterface
    fun saveCourseConfig(raw: String?) {
        saveCourseConfig(raw, "")
    }

    @JavascriptInterface
    fun saveCourseConfig(raw: String?, promiseId: String?) {
        courseConfig = runCatching { CourseImportParser.normalizeCourseConfig(JSONObject(raw.orEmpty())) }
            .getOrNull()
            .orEmpty()
            .ifBlank { raw.orEmpty() }
        resolve(promiseId, "true", quote = false)
    }

    @JavascriptInterface
    fun savePresetTimeSlots(raw: String?) {
        savePresetTimeSlots(raw, "")
    }

    @JavascriptInterface
    fun savePresetTimeSlots(raw: String?, promiseId: String?) {
        presetTimeSlots = runCatching { CourseImportParser.normalizeTimeSlots(raw.orEmpty()) }
            .getOrDefault(raw.orEmpty())
        resolve(promiseId, "true", quote = false)
    }

    @JavascriptInterface
    fun reportError(error: String?) {
        Log.w(Tag, error.orEmpty())
        onStatus(error.orEmpty().ifBlank { "脚本执行失败" })
    }

    @JavascriptInterface
    fun notifyTaskCompletion() {
        onStatus("脚本执行完成")
    }

    @JavascriptInterface
    fun closeWebView() {
        mainHandler.postDelayed({ (context as? Activity)?.finish() }, 300)
    }

    @JavascriptInterface
    fun showToast(message: String?) {
        mainHandler.post {
            Toast.makeText(context, message.orEmpty(), Toast.LENGTH_SHORT).show()
        }
    }

    @JavascriptInterface
    fun showLog(message: String?) {
        Log.d(Tag, message.orEmpty())
    }

    @JavascriptInterface
    fun showAlert(title: String?): Boolean = showAlert(title, "", "确定")

    @JavascriptInterface
    fun showAlert(title: String?, content: String?): Boolean = showAlert(title, content, "确定")

    @JavascriptInterface
    fun showAlert(title: String?, content: String?, button: String?): Boolean {
        val latch = CountDownLatch(1)
        val result = AtomicReference(false)
        mainHandler.post {
            AlertDialog.Builder(context)
                .setTitle(title.orEmpty())
                .setMessage(content.orEmpty())
                .setPositiveButton(button.orEmpty().ifBlank { "确定" }) { _, _ ->
                    result.set(true)
                    latch.countDown()
                }
                .setOnCancelListener { latch.countDown() }
                .show()
        }
        latch.awaitSafely()
        return result.get()
    }

    @JavascriptInterface
    fun showAlertAsync(title: String?, content: String?, button: String?, promiseId: String?) {
        mainHandler.post {
            AlertDialog.Builder(context)
                .setTitle(title.orEmpty())
                .setMessage(content.orEmpty())
                .setPositiveButton(button.orEmpty().ifBlank { "确定" }) { _, _ ->
                    resolve(promiseId, "true", quote = false)
                }
                .setOnCancelListener { resolve(promiseId, "false", quote = false) }
                .show()
        }
    }

    @JavascriptInterface
    fun showPrompt(title: String?, message: String?): String = showPrompt(title, message, "", "")

    @JavascriptInterface
    fun showPrompt(title: String?, message: String?, defaultValue: String?): String {
        return showPrompt(title, message, defaultValue, "")
    }

    @JavascriptInterface
    fun showPrompt(title: String?, message: String?, defaultValue: String?, value: String?): String {
        val latch = CountDownLatch(1)
        val result = AtomicReference("")
        mainHandler.post {
            val input = EditText(context).apply {
                setText(value?.takeIf { it.isNotBlank() } ?: defaultValue.orEmpty())
                setSingleLine(false)
                minLines = 3
            }
            AlertDialog.Builder(context)
                .setTitle(title.orEmpty())
                .setMessage(message.orEmpty())
                .setView(input)
                .setPositiveButton("确定") { _, _ ->
                    result.set(input.text?.toString().orEmpty())
                    latch.countDown()
                }
                .setNegativeButton("取消") { _, _ -> latch.countDown() }
                .setOnCancelListener { latch.countDown() }
                .show()
        }
        latch.awaitSafely()
        return result.get()
    }

    @JavascriptInterface
    fun showPromptAsync(title: String?, message: String?, defaultValue: String?, value: String?, promiseId: String?) {
        mainHandler.post {
            val input = EditText(context).apply {
                setText(value?.takeIf { it.isNotBlank() } ?: defaultValue.orEmpty())
                setSingleLine(false)
                minLines = 3
            }
            AlertDialog.Builder(context)
                .setTitle(title.orEmpty())
                .setMessage(message.orEmpty())
                .setView(input)
                .setPositiveButton("确定") { _, _ -> resolve(promiseId, input.text?.toString().orEmpty()) }
                .setNegativeButton("取消") { _, _ -> reject(promiseId, "cancel") }
                .setOnCancelListener { reject(promiseId, "cancel") }
                .show()
        }
    }

    @JavascriptInterface
    fun showSingleSelection(title: String?, itemsJson: String?): Int = showSingleSelection(title, itemsJson, -1)

    @JavascriptInterface
    fun showSingleSelection(title: String?, itemsJson: String?, defaultIndex: Int): Int {
        val items = parseSelectionItems(itemsJson)
        val latch = CountDownLatch(1)
        val result = AtomicInteger(defaultIndex)
        mainHandler.post {
            AlertDialog.Builder(context)
                .setTitle(title.orEmpty())
                .setSingleChoiceItems(items.toTypedArray(), defaultIndex) { dialog, which ->
                    result.set(which)
                    dialog.dismiss()
                    latch.countDown()
                }
                .setOnCancelListener { latch.countDown() }
                .show()
        }
        latch.awaitSafely()
        return result.get()
    }

    @JavascriptInterface
    fun showSingleSelectionAsync(title: String?, itemsJson: String?, defaultIndex: Int, promiseId: String?) {
        val items = parseSelectionItems(itemsJson)
        mainHandler.post {
            AlertDialog.Builder(context)
                .setTitle(title.orEmpty())
                .setSingleChoiceItems(items.toTypedArray(), defaultIndex) { dialog, which ->
                    dialog.dismiss()
                    resolve(promiseId, which.toString(), quote = false)
                }
                .setOnCancelListener { reject(promiseId, "cancel") }
                .show()
        }
    }

    private fun openPreview(payload: ImportPreviewPayload) {
        mainHandler.post {
            CoursePreviewActivity.start(context, payload.toJsonString())
        }
    }

    private fun resolve(promiseId: String?, result: String, quote: Boolean = true) {
        if (promiseId.isNullOrBlank() || promiseId == "undefined") return
        val payload = if (quote) JSONObject.quote(result) else result
        webView.post {
            webView.evaluateJavascript("window._resolveAndroidPromise(${JSONObject.quote(promiseId)}, $payload);", null)
        }
    }

    private fun reject(promiseId: String?, error: String) {
        if (promiseId.isNullOrBlank() || promiseId == "undefined") {
            showToast(error)
            return
        }
        webView.post {
            webView.evaluateJavascript(
                "window._rejectAndroidPromise(${JSONObject.quote(promiseId)}, ${JSONObject.quote(error)});",
                null,
            )
        }
    }

    private fun parseSelectionItems(raw: String?): List<String> {
        return runCatching {
            val array = JSONArray(raw.orEmpty())
            buildList {
                for (index in 0 until array.length()) {
                    val item = array.opt(index)
                    add(
                        when (item) {
                            is JSONObject -> item.optString("label").ifBlank { item.optString("name") }
                            else -> item?.toString().orEmpty()
                        },
                    )
                }
            }.filter { it.isNotBlank() }
        }.getOrDefault(emptyList())
    }

    private fun CountDownLatch.awaitSafely() {
        runCatching { await() }.onFailure { Thread.currentThread().interrupt() }
    }

    private companion object {
        const val Tag = "ScheduleImportBridge"
    }
}
