package com.neoruaa.xiaoaischedule.importer

import android.content.Context
import android.util.Base64
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsBytes
import io.ktor.client.statement.bodyAsText
import io.ktor.http.isSuccess
import org.json.JSONArray

class ScheduleImportRepository(context: Context) {
    private val appContext = context.applicationContext
    private val prefs = appContext.getSharedPreferences("schedule_import", Context.MODE_PRIVATE)
    private val client = HttpClient(CIO)

    suspend fun commonSources(forceRefresh: Boolean = false): List<ImportSourceItem> {
        val cached = prefs.getString(KeyCommonSources, null)
        if (!forceRefresh && !cached.isNullOrBlank()) {
            runCatching { parseCommonSources(cached) }.getOrNull()?.takeIf { it.isNotEmpty() }?.let { return it }
        }
        val body = client.get(CommonSourceUrl).bodyAsText()
        prefs.edit().putString(KeyCommonSources, body).apply()
        return parseCommonSources(body)
    }

    suspend fun shiguangSchools(forceRefresh: Boolean = false): List<ShiguangSchool> {
        val repo = shiguangRepoUrl()
        val cacheSource = prefs.getString(KeyShiguangCacheSource, "")
        val cached = prefs.getString(KeyShiguangIndex, null)
        if (!forceRefresh && cacheSource == repo && !cached.isNullOrBlank()) {
            val bytes = Base64.decode(cached, Base64.DEFAULT)
            runCatching { ShiguangIndexParser.parse(bytes) }.getOrNull()?.takeIf { it.isNotEmpty() }?.let { return it }
        }
        val response = client.get("$repo/raw/index-pb-release/school_index.pb")
        if (!response.status.isSuccess()) return emptyList()
        val bytes = response.bodyAsBytes()
        prefs.edit()
            .putString(KeyShiguangIndex, Base64.encodeToString(bytes, Base64.NO_WRAP))
            .putString(KeyShiguangCacheSource, repo)
            .apply()
        return ShiguangIndexParser.parse(bytes)
    }

    suspend fun downloadScript(url: String): String {
        return client.get(url).bodyAsText()
    }

    fun commonScriptUrl(type: String): String {
        return "$CommonSystemScriptBase/${type.trimStart('/')}.js"
    }

    fun shiguangScriptUrl(path: String): String {
        val normalizedPath = path.trimStart('/')
        return "${shiguangRepoUrl()}/raw/${shiguangBranch()}/$normalizedPath"
    }

    fun shiguangRepoUrl(): String {
        return prefs.getString(KeyShiguangRepoUrl, null)
            ?.trim()
            ?.trimEnd('/')
            ?.takeIf { it.isNotBlank() }
            ?: DefaultShiguangRepo
    }

    fun shiguangBranch(): String {
        return prefs.getString(KeyShiguangBranch, null)
            ?.trim()
            ?.takeIf { it.isNotBlank() }
            ?: DefaultShiguangBranch
    }

    fun setShiguangSource(url: String, branch: String) {
        prefs.edit()
            .putString(KeyShiguangRepoUrl, url.trim().trimEnd('/'))
            .putString(KeyShiguangBranch, branch.trim().ifBlank { DefaultShiguangBranch })
            .apply()
    }

    fun resetShiguangSource() {
        prefs.edit()
            .putString(KeyShiguangRepoUrl, DefaultShiguangRepo)
            .putString(KeyShiguangBranch, DefaultShiguangBranch)
            .apply()
    }

    fun aiApiUrl(): String = prefs.getString(KeyAiApiUrl, DefaultAiApiUrl).orEmpty().ifBlank { DefaultAiApiUrl }

    fun aiModel(): String = prefs.getString(KeyAiModel, DefaultAiModel).orEmpty().ifBlank { DefaultAiModel }

    fun aiApiKey(): String = prefs.getString(KeyAiApiKey, "").orEmpty()

    fun aiImportUrl(): String = prefs.getString(KeyAiImportUrl, DefaultAiImportUrl).orEmpty().ifBlank { DefaultAiImportUrl }

    fun setAiSettings(importUrl: String, apiUrl: String, model: String, apiKey: String) {
        prefs.edit()
            .putString(KeyAiImportUrl, importUrl.trim().ifBlank { DefaultAiImportUrl })
            .putString(KeyAiApiUrl, apiUrl.trim().trimEnd('/').ifBlank { DefaultAiApiUrl })
            .putString(KeyAiModel, model.trim().ifBlank { DefaultAiModel })
            .putString(KeyAiApiKey, apiKey.trim())
            .apply()
    }

    fun debugUrl(): String = prefs.getString(KeyDebugUrl, DefaultAiImportUrl).orEmpty().ifBlank { DefaultAiImportUrl }

    fun debugScript(): String = prefs.getString(KeyDebugScript, "").orEmpty()

    fun setDebugScript(url: String, script: String) {
        prefs.edit()
            .putString(KeyDebugUrl, url.trim())
            .putString(KeyDebugScript, script)
            .apply()
    }

    private fun parseCommonSources(raw: String): List<ImportSourceItem> {
        val array = JSONArray(raw)
        return buildList {
            for (index in 0 until array.length()) {
                val item = array.optJSONObject(index) ?: continue
                val name = item.optString("name")
                val url = item.optString("url")
                val type = item.optString("type")
                if (name.isNotBlank() && type.isNotBlank()) {
                    add(
                        ImportSourceItem(
                            name = name,
                            url = url,
                            type = ImportSourceType.Common,
                            extra = type,
                        ),
                    )
                }
            }
        }.sortedBy { it.name }
    }

    companion object {
        const val CommonSourceUrl = "https://gitee.com/padi/aishedule/raw/master/system.json"
        const val CommonSystemScriptBase = "https://gitee.com/padi/aishedule/raw/master/system"
        const val DefaultShiguangRepo = "https://gitee.com/XingHeYuZhuan-gh/shiguang_warehouse"
        const val DefaultShiguangBranch = "main"
        const val DefaultAiApiUrl = "https://dashscope.aliyuncs.com/compatible-mode/v1"
        const val DefaultAiModel = "qwen3-coder-plus"
        const val DefaultAiImportUrl = "https://jwxt.example.edu.cn"

        private const val KeyCommonSources = "cache_common_sources"
        private const val KeyShiguangIndex = "cache_shiguang_index"
        private const val KeyShiguangCacheSource = "cache_shiguang_source"
        private const val KeyShiguangRepoUrl = "debug_shiguang_repo_url"
        private const val KeyShiguangBranch = "debug_shiguang_repo_branch"
        private const val KeyAiApiUrl = "ai_api_url"
        private const val KeyAiModel = "ai_model"
        private const val KeyAiApiKey = "ai_api_key"
        private const val KeyAiImportUrl = "ai_import_url"
        private const val KeyDebugUrl = "debug_jw_url"
        private const val KeyDebugScript = "debug_jw_script"
    }
}
