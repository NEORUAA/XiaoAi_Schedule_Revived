package com.neoruaa.xiaoaischedule.importer

import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.contentType
import org.json.JSONArray
import org.json.JSONObject

class AiScheduleParser {
    private val client = HttpClient(CIO)

    suspend fun parseHtml(
        html: String,
        apiUrl: String,
        model: String,
        apiKey: String,
    ): ImportPreviewPayload {
        if (apiKey.isBlank()) throw IllegalArgumentException("请先配置 AI API Key")
        val request = JSONObject()
            .put("model", model)
            .put("temperature", 0.1)
            .put("stream", true)
            .put(
                "messages",
                JSONArray()
                    .put(JSONObject().put("role", "system").put("content", SystemPrompt))
                    .put(JSONObject().put("role", "user").put("content", html.take(MaxHtmlLength))),
            )
        val response = client.post("${apiUrl.trimEnd('/')}/chat/completions") {
            contentType(ContentType.Application.Json)
            header(HttpHeaders.Authorization, "Bearer $apiKey")
            setBody(request.toString())
        }
        val content = extractAssistantContent(response.bodyAsText())
        val json = extractJsonObject(content)
        return CourseImportParser.parseToPreviewPayload(json, schoolName = "AI解析导入", source = "ai")
    }

    private fun extractAssistantContent(raw: String): String {
        if (!raw.lines().any { it.trimStart().startsWith("data:") }) {
            return JSONObject(raw)
                .optJSONArray("choices")
                ?.optJSONObject(0)
                ?.optJSONObject("message")
                ?.optString("content")
                .orEmpty()
                .ifBlank { raw }
        }
        val builder = StringBuilder()
        raw.lineSequence()
            .map { it.trim() }
            .filter { it.startsWith("data:") }
            .map { it.removePrefix("data:").trim() }
            .filter { it.isNotBlank() && it != "[DONE]" }
            .forEach { line ->
                runCatching {
                    val delta = JSONObject(line)
                        .optJSONArray("choices")
                        ?.optJSONObject(0)
                        ?.optJSONObject("delta")
                    builder.append(delta?.optString("content").orEmpty())
                }
            }
        return builder.toString()
    }

    private fun extractJsonObject(content: String): String {
        val unwrapped = content
            .replace("```json", "")
            .replace("```", "")
            .trim()
        if (unwrapped.startsWith("{") && unwrapped.endsWith("}")) return repairJson(unwrapped)
        val start = unwrapped.indexOf('{')
        val end = unwrapped.lastIndexOf('}')
        if (start >= 0 && end > start) return repairJson(unwrapped.substring(start, end + 1))
        throw IllegalArgumentException("AI 未返回可识别的 JSON")
    }

    private fun repairJson(raw: String): String {
        return raw.replace(Regex("\"(\\[.*?])\"")) { match ->
            val inner = match.groupValues[1].replace("\\\"", "\"")
            inner
        }
    }

    private companion object {
        const val MaxHtmlLength = 150_000
        val SystemPrompt = """
            你是小爱课程表导入助手。请从用户提供的教务系统 HTML 中提取课程表，只返回纯 JSON，不要 Markdown。
            JSON 格式：
            {
              "courses":[{"name":"课程名称","teacher":"教师姓名","position":"上课地点","day":1,"sections":"1,2","weeks":"1,2,3"}],
              "schedule":{"morningNum":5,"afternoonNum":4,"nightNum":3,"sections":[{"i":1,"s":"08:00","e":"08:45"}]}
            }
            day 使用 1 到 7 表示周一到周日。sections 和 weeks 必须是逗号分隔的数字。无法确认的教师或地点可留空。
        """.trimIndent()
    }
}
