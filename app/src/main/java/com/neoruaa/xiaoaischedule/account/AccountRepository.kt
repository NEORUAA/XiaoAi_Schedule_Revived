package com.neoruaa.xiaoaischedule.account

import android.content.Context
import android.util.Base64
import android.util.Log
import com.neoruaa.xiaoaischedule.core.XiaoAiConstants
import com.neoruaa.xiaoaischedule.data.LoginSession
import com.neoruaa.xiaoaischedule.data.PrivacyStore
import com.neoruaa.xiaoaischedule.data.SavedPassword
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.cookies.AcceptAllCookiesStorage
import io.ktor.client.plugins.cookies.HttpCookies
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.request.forms.submitForm
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.http.isSuccess
import io.ktor.http.parameters
import io.ktor.http.setCookie
import io.ktor.http.userAgent
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.json.JSONObject
import java.security.MessageDigest

class AccountRepository(
    context: Context,
    private val privacyStore: PrivacyStore,
) {
    private val prefs = context.applicationContext.getSharedPreferences("xiaoai_account", Context.MODE_PRIVATE)
    private val json = Json { ignoreUnknownKeys = true }
    private val client = HttpClient(CIO) {
        install(HttpCookies) {
            storage = AcceptAllCookiesStorage()
        }
        defaultRequest {
            userAgent(defaultUserAgent())
        }
    }
    private val oauthClient = HttpClient(CIO) {
        defaultRequest {
            userAgent(defaultUserAgent())
        }
    }

    private val _session = MutableStateFlow(loadSession())
    val session: StateFlow<LoginSession?> = _session
    private val refreshMutex = Mutex()

    fun savedPassword(): SavedPassword? = privacyStore.savedPassword()

    suspend fun login(
        account: String,
        password: String,
        savePassword: Boolean,
        captcha: String = "",
    ): LoginResult {
        if (account.isBlank() || password.isBlank()) {
            return LoginResult.Error("请输入小米账号和密码")
        }
        return runCatching {
            serviceLoginAuth2(account.trim(), password, savePassword, captcha)
        }.getOrElse {
            LoginResult.Error("网络请求失败，请稍后重试")
        }
    }

    suspend fun sendTicket(flag: Int): SendTicketResult {
        val apiPath = if (flag == 4) "/identity/auth/sendPhoneTicket" else "/identity/auth/sendEmailTicket"
        return runCatching {
            val response = client.submitForm(
                url = "${XiaoAiConstants.XiaomiAccountUrl}$apiPath",
                formParameters = parameters {
                    append("_json", "true")
                    append("retry", "0")
                    append("icode", "")
                },
            ) {
                parameter("_dc", System.currentTimeMillis())
            }
            val body = response.bodyAsText()
            if (!response.status.isSuccess()) {
                return@runCatching SendTicketResult.Error("验证码发送失败，请稍后重试")
            }
            val json = runCatching { JSONObject(stripXiaomiPrefix(body)) }.getOrNull()
                ?: return@runCatching SendTicketResult.Success
            when (val code = json.optInt("code", 0)) {
                0 -> SendTicketResult.Success
                XiaomiTicketTooManyRequests -> SendTicketResult.Error(
                    json.firstNonBlank("tips", "desc", "description", "title")
                        .ifBlank { "验证码发送过多，请明天再试" },
                )
                else -> SendTicketResult.Error(
                    json.firstNonBlank("desc", "tips", "description", "title")
                        .ifBlank { "验证码发送失败" },
                ).also {
                    Log.w(Tag, "send ticket failed: code=$code")
                }
            }
        }.getOrElse {
            Log.w(Tag, "send ticket failed: ${it.javaClass.simpleName}")
            SendTicketResult.Error("验证码发送失败，请稍后重试")
        }
    }

    suspend fun submitTwoFactorTicket(
        account: String,
        password: String,
        savePassword: Boolean,
        flag: Int,
        ticket: String,
    ): LoginResult {
        if (ticket.isBlank()) return LoginResult.Error("请输入验证码")
        val verifyResult = verify2FATicket(flag, ticket)
        if (verifyResult == 70014) return LoginResult.Error("验证码错误")
        if (verifyResult != 0) return LoginResult.Error("二次验证失败")
        return login(account, password, savePassword)
    }

    fun logout(clearSavedPassword: Boolean = false) {
        prefs.edit().remove(KeyLoginSession).apply()
        _session.value = null
        if (clearSavedPassword) privacyStore.clearSavedPassword()
    }

    suspend fun authorization(): String {
        val session = currentFreshSession() ?: return ""
        if (!session.isLoggedIn) return ""
        return oauthAuthorization(session.accessToken)
    }

    suspend fun authorizationWithScopeData(): String {
        val auth = authorization()
        if (auth.isBlank()) return ""
        val scopeJson = JSONObject().put("d", privacyStore.deviceId()).toString()
        val scope = Base64.encodeToString(scopeJson.toByteArray(Charsets.UTF_8), Base64.NO_WRAP)
        return "$auth,scope_data:$scope"
    }

    fun currentSession(): LoginSession? = _session.value

    suspend fun currentFreshSession(): LoginSession? = ensureFreshSession()

    suspend fun deleteScheduleService(): Boolean {
        val auth = authorizationWithScopeData()
        if (auth.isBlank()) return false
        return runCatching {
            val response = client.post(XiaoAiConstants.LogoutScheduleUrl) {
                header(HttpHeaders.Authorization, auth)
            }
            response.status.isSuccess() && response.bodyAsText().isNotBlank()
        }.getOrDefault(false)
    }

    fun clearLocalAccountData() {
        logout(clearSavedPassword = false)
        privacyStore.clearH5Storage()
    }

    private suspend fun serviceLoginAuth2(
        account: String,
        password: String,
        savePassword: Boolean,
        captcha: String = "",
    ): LoginResult {
        val response = client.submitForm(
            url = "${XiaoAiConstants.XiaomiAccountUrl}/pass/serviceLoginAuth2",
            formParameters = parameters {
                append("sid", XiaoAiConstants.OAuthSid)
                append("hash", md5(password).uppercase())
                append("user", account)
                append("_json", "true")
                append("_locale", "zh_CN")
                if (captcha.isNotBlank()) append("captCode", captcha)
            },
        )
        if (!response.status.isSuccess()) return LoginResult.Error("网络请求失败，请稍后重试")

        val content = JSONObject(stripXiaomiPrefix(response.bodyAsText()))
        val captchaUrl = content.optString("captchaUrl").takeIf { it.isNotBlank() && it != "null" }
        if (captchaUrl != null) {
            return LoginResult.Error("当前账号需要图片验证码，请稍后重试或先在网页登录小米账号")
        }

        val notificationUrl = content.optString("notificationUrl").takeIf { it.isNotBlank() && it != "null" }
        if (notificationUrl != null) {
            return prepareTwoFactor(notificationUrl)
        }

        val result = content.optString("result")
        val ssecurity = content.optString("ssecurity")
        if (result.isNotBlank() && result != "ok" || ssecurity.isBlank()) {
            return LoginResult.Error("账号或密码错误")
        }

        val location = content.optString("location")
        val userId = content.optString("userId")
        val nonce = content.optLong("nonce", 0L)
        if (location.isBlank() || userId.isBlank()) {
            return LoginResult.Error("登录响应缺少必要信息")
        }
        if (ssecurity.isBlank() || nonce == 0L) {
            return LoginResult.Error("登录响应缺少安全签名参数")
        }

        val serviceTokenResponse = client.get(location) {
            parameter("clientSign", clientSign(nonce, ssecurity))
            parameter("_userIdNeedEncrypt", "true")
        }
        if (!serviceTokenResponse.status.isSuccess()) {
            return LoginResult.Error("获取 serviceToken 失败")
        }
        val serviceTokenCookies = serviceTokenResponse.setCookie()
        Log.d(Tag, "service token cookie names=${serviceTokenCookies.map { it.name }}")
        val sidServiceTokenName = "${XiaoAiConstants.OAuthSid}_serviceToken"
        val serviceToken = serviceTokenCookies
            .lastOrNull { it.name == sidServiceTokenName && it.value.isNotBlank() }
            ?.value
            ?: serviceTokenCookies
                .lastOrNull { it.name == "serviceToken" && it.value.isNotBlank() }
                ?.value
                .orEmpty()
        if (serviceToken.isBlank()) return LoginResult.Error("小米接口未返回 serviceToken")

        val oauth = issueOAuthToken(serviceToken, userId)
            ?: return LoginResult.Error("获取课程表 OAuth token 失败")
        if (oauth.refreshToken.isBlank() || oauth.expiresIn <= 0) {
            Log.w(Tag, "OAuth issued-token missing refresh metadata: refresh=${oauth.refreshToken.isNotBlank()}, expiresIn=${oauth.expiresIn}")
            return LoginResult.Error("获取课程表 OAuth token 失败")
        }

        val session = LoginSession(
            account = account,
            userId = userId,
            cUserId = content.optString("cUserId"),
            userName = account,
            serviceToken = serviceToken,
            passToken = content.optString("passToken"),
            accessToken = oauth.accessToken,
            refreshToken = oauth.refreshToken,
            openId = oauth.openId,
            expiresIn = oauth.expiresIn,
            lastRefreshTimeSeconds = System.currentTimeMillis() / 1000,
        )
        saveSession(session)
        if (savePassword) {
            privacyStore.savePassword(account, password)
        } else {
            privacyStore.clearSavedPassword()
        }
        return LoginResult.Success(session)
    }

    private suspend fun prepareTwoFactor(notificationUrl: String): LoginResult {
        val listUrl = notificationUrl.replace("fe/service/identity/authStart", "identity/list")
        val response = client.get(listUrl)
        if (!response.status.isSuccess()) return LoginResult.Error("获取二次验证方式失败")
        val body = JSONObject(stripXiaomiPrefix(response.bodyAsText()))
        if (body.optBoolean("twoFactorAuth", false)) {
            return LoginResult.Error("当前账号开启了设备双重认证，暂不支持此登录方式")
        }
        val optionsJson = body.optJSONArray("options")
        val options = buildList {
            if (optionsJson != null) {
                for (index in 0 until optionsJson.length()) add(optionsJson.optInt(index))
            }
        }.filter { it == 4 || it == 8 }
        if (options.isEmpty()) return LoginResult.Error("没有可用的二次验证方式")
        return LoginResult.NeedTwoFactor(options)
    }

    private suspend fun verify2FATicket(flag: Int, ticket: String): Int {
        val apiPath = if (flag == 4) "/identity/auth/verifyPhone" else "/identity/auth/verifyEmail"
        return runCatching {
            val response = client.submitForm(
                url = "${XiaoAiConstants.XiaomiAccountUrl}$apiPath",
                formParameters = parameters {
                    append("_flag", flag.toString())
                    append("ticket", ticket)
                    append("trust", "true")
                    append("_json", "true")
                },
            ) {
                parameter("_dc", System.currentTimeMillis())
            }
            val body = JSONObject(stripXiaomiPrefix(response.bodyAsText()))
            val code = body.optInt("code", -1)
            if (code == 0) {
                body.optString("location").takeIf { it.isNotBlank() }?.let { client.get(it) }
            }
            code
        }.getOrDefault(-1)
    }

    private suspend fun issueOAuthToken(serviceToken: String, userId: String): OAuthToken? {
        val scopes = oauthClient.get("${XiaoAiConstants.XiaomiAccountUrl}/oauth2/user-credentials/scopes") {
            parameter("client_id", XiaoAiConstants.AppId)
            parameter("sid", XiaoAiConstants.OAuthSid)
        }
        val scopesBody = scopes.bodyAsText()
        if (!scopes.status.isSuccess()) {
            Log.w(Tag, "OAuth scopes failed: status=${scopes.status.value}, body=${sanitizeOAuthBody(scopesBody)}")
            return null
        }
        val code = JSONObject(stripXiaomiPrefix(scopesBody)).optString("code")
        if (code.isBlank()) return null

        val issued = oauthClient.submitForm(
            url = "${XiaoAiConstants.XiaomiAccountUrl}/oauth2/user-credentials/issued-token",
            formParameters = parameters {
                append("grant_type", "password")
                append("client_id", XiaoAiConstants.AppId)
                append("client_secret", XiaoAiConstants.AppSecret)
                append("sid", XiaoAiConstants.OAuthSid)
                append("code", code)
                append("user_id", userId)
            },
        ) {
            header(HttpHeaders.Cookie, "oauth2.0_serviceToken=$serviceToken; path=/; domain=.xiaomi.com;")
        }
        val issuedBody = issued.bodyAsText()
        if (!issued.status.isSuccess()) {
            Log.w(Tag, "OAuth issued-token failed: status=${issued.status.value}, body=${sanitizeOAuthBody(issuedBody)}")
            return null
        }
        return parseOAuthToken(issuedBody).also {
            if (it == null) {
                Log.w(Tag, "OAuth issued-token parse failed: body=${sanitizeOAuthBody(issuedBody)}")
            }
        }
    }

    private suspend fun ensureFreshSession(): LoginSession? {
        return refreshMutex.withLock {
            val current = _session.value ?: return@withLock null
            if (!current.isExpired()) return@withLock current
            Log.d(
                Tag,
                "OAuth token refresh needed: access=${current.accessToken.isNotBlank()}, refresh=${current.refreshToken.isNotBlank()}, expiresIn=${current.expiresIn}, lastRefresh=${current.lastRefreshTimeSeconds}",
            )
            when (val result = refreshOAuthToken(current)) {
                is RefreshResult.Success -> {
                    saveSession(result.session)
                    Log.d(Tag, "OAuth token refreshed")
                    result.session
                }
                RefreshResult.InvalidRefreshToken -> {
                    Log.w(Tag, "OAuth refresh token invalid, clearing saved session")
                    logout(clearSavedPassword = false)
                    null
                }
                RefreshResult.Failed -> {
                    Log.w(Tag, "OAuth refresh failed, keeping saved session for retry")
                    null
                }
            }
        }
    }

    private suspend fun refreshOAuthToken(session: LoginSession): RefreshResult {
        if (session.refreshToken.isBlank()) return RefreshResult.InvalidRefreshToken
        return runCatching {
            val response = oauthClient.get("${XiaoAiConstants.XiaomiAccountUrl}/oauth2/auth/token") {
                parameter("pt", "0")
                parameter("grant_type", "refresh_token")
                parameter("client_id", XiaoAiConstants.AppId)
                parameter("client_secret", XiaoAiConstants.AppSecret)
                parameter("redirect_uri", XiaoAiConstants.OAuthRedirectUri)
                parameter("refresh_token", session.refreshToken)
            }
            val body = response.bodyAsText()
            val oauth = parseOAuthToken(body)
            if (oauth == null) {
                val errorCode = parseOAuthErrorCode(body)
                val errorDescription = parseOAuthErrorDescription(body)
                Log.w(Tag, "OAuth refresh parse failed: status=${response.status.value}, error=$errorCode, desc=$errorDescription, body=${sanitizeOAuthBody(body)}")
                if (errorCode == OAuthErrorInvalidToken || errorCode == OAuthErrorInvalidRefreshToken) {
                    RefreshResult.InvalidRefreshToken
                } else {
                    RefreshResult.Failed
                }
            } else {
                RefreshResult.Success(
                    session.copy(
                        accessToken = oauth.accessToken,
                        refreshToken = oauth.refreshToken.ifBlank { session.refreshToken },
                        openId = oauth.openId.ifBlank { session.openId },
                        expiresIn = oauth.expiresIn,
                        lastRefreshTimeSeconds = System.currentTimeMillis() / 1000,
                    ),
                )
            }
        }.getOrElse {
            Log.w(Tag, "OAuth refresh failed: ${it.javaClass.simpleName}")
            RefreshResult.Failed
        }
    }

    private fun parseOAuthToken(raw: String): OAuthToken? {
        val body = JSONObject(stripXiaomiPrefix(raw))
        val accessToken = body.optString("access_token")
        if (accessToken.isBlank()) return null
        val expiresIn = body.optLong("expires_in", 0)
        if (expiresIn <= 0) return null
        return OAuthToken(
            accessToken = accessToken,
            refreshToken = body.optString("refresh_token"),
            openId = body.optString("openId"),
            expiresIn = expiresIn,
        )
    }

    private fun oauthAuthorization(accessToken: String): String {
        if (accessToken.isBlank()) return ""
        return "AO-TOKEN-V1 dev_app_id:${XiaoAiConstants.AppId},access_token:$accessToken"
    }

    private fun parseOAuthErrorCode(raw: String): Int {
        return runCatching { JSONObject(stripXiaomiPrefix(raw)).optInt("error", 0) }.getOrDefault(0)
    }

    private fun parseOAuthErrorDescription(raw: String): String {
        return runCatching { JSONObject(stripXiaomiPrefix(raw)).optString("error_description") }.getOrDefault("")
    }

    private fun JSONObject.firstNonBlank(vararg names: String): String {
        for (name in names) {
            val value = optString(name).trim()
            if (value.isNotBlank() && value != "null") return value
        }
        return ""
    }

    private fun loadSession(): LoginSession? {
        val raw = prefs.getString(KeyLoginSession, null) ?: return null
        return runCatching { json.decodeFromString<LoginSession>(raw) }.getOrNull()
    }

    private fun saveSession(session: LoginSession) {
        prefs.edit().putString(KeyLoginSession, json.encodeToString(session)).apply()
        _session.value = session
    }

    private fun stripXiaomiPrefix(value: String): String = value.removePrefix("&&&START&&&").trim()

    private fun sanitizeOAuthBody(raw: String): String {
        return stripXiaomiPrefix(raw)
            .replace(Regex("\"access_token\"\\s*:\\s*\"[^\"]*\""), "\"access_token\":\"***\"")
            .replace(Regex("\"refresh_token\"\\s*:\\s*\"[^\"]*\""), "\"refresh_token\":\"***\"")
            .replace(Regex("\"openId\"\\s*:\\s*\"[^\"]*\""), "\"openId\":\"***\"")
            .take(800)
    }

    private fun md5(value: String): String {
        val digest = MessageDigest.getInstance("MD5").digest(value.toByteArray())
        return digest.joinToString("") { "%02x".format(it) }
    }

    private fun clientSign(nonce: Long, ssecurity: String): String {
        val raw = "nonce=$nonce&$ssecurity"
        val digest = MessageDigest.getInstance("SHA1").digest(raw.toByteArray(Charsets.UTF_8))
        return Base64.encodeToString(digest, Base64.NO_WRAP)
    }

    private fun defaultUserAgent(): String {
        return "Dalvik/2.1.0 (Linux; U; Android 16; 25098PN5AC Build/BP2A.250605.031.A3)"
    }

    private data class OAuthToken(
        val accessToken: String,
        val refreshToken: String,
        val openId: String,
        val expiresIn: Long,
    )

    private sealed interface RefreshResult {
        data class Success(val session: LoginSession) : RefreshResult
        data object Failed : RefreshResult
        data object InvalidRefreshToken : RefreshResult
    }

    sealed interface LoginResult {
        data class Success(val session: LoginSession) : LoginResult
        data class NeedTwoFactor(val options: List<Int>) : LoginResult
        data class Error(val message: String) : LoginResult
    }

    sealed interface SendTicketResult {
        data object Success : SendTicketResult
        data class Error(val message: String) : SendTicketResult
    }

    private companion object {
        const val Tag = "XiaoAiAccount"
        const val KeyLoginSession = "login_session"
        const val OAuthErrorInvalidToken = 96008
        const val OAuthErrorInvalidRefreshToken = 96009
        const val XiaomiTicketTooManyRequests = 70022
    }
}
