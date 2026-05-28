package com.neoruaa.xiaoaischedule.web

import android.content.Context
import android.webkit.JavascriptInterface
import android.webkit.WebView
import com.neoruaa.xiaoaischedule.BuildConfig
import com.neoruaa.xiaoaischedule.account.AccountRepository
import com.neoruaa.xiaoaischedule.core.XiaoAiConstants
import com.neoruaa.xiaoaischedule.data.PrivacyStore
import com.neoruaa.xiaoaischedule.widget.CourseWidgetProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject

class XiaoAiBridge(
    private val context: Context,
    private val webView: WebView,
    private val accountRepository: AccountRepository,
    private val privacyStore: PrivacyStore,
    private val routeHandler: NativeRouteHandler,
    private val host: BridgeHost,
    private val scope: CoroutineScope,
) {
    @JavascriptInterface
    fun postData(raw: String?) {
        if (raw.isNullOrBlank()) return
        scope.launch(Dispatchers.Main) {
            runCatching { dispatch(JSONObject(raw)) }
        }
    }

    private fun dispatch(root: JSONObject) {
        val keys = root.keys()
        while (keys.hasNext()) {
            val action = keys.next()
            val params = root.optJSONObject(action) ?: JSONObject()
            handleAction(action, params)
        }
    }

    private fun handleAction(action: String, params: JSONObject) {
        when (action) {
            "appVersion" -> postResult(params.callback, appVersion(params.id))
            "getUserInfo" -> {
                scope.launch(Dispatchers.IO) {
                    val payload = userInfo(params.id)
                    withContext(Dispatchers.Main) { postResult(params.callback, payload) }
                }
            }
            "getAuthorization" -> {
                scope.launch(Dispatchers.IO) {
                    val auth = accountRepository.authorization()
                    val payload = nested("getAuthorization", params.id) {
                        put("authorization", auth)
                    }
                    withContext(Dispatchers.Main) { postResult(params.callback, payload) }
                }
            }
            "login" -> host.onLoginRequested(
                BridgeLoginRequest(
                    id = params.id,
                    callback = params.callback,
                    webView = webView,
                ),
            )
            "logout" -> {
                accountRepository.logout()
                CourseWidgetProvider.requestRefresh(context)
                if (params.callback.isNotBlank()) {
                    postResult(params.callback, nested("logout", params.id) { put("is_login", 0) })
                }
            }
            "openNativePage" -> {
                val url = params.optString("url")
                if (url.isNotBlank()) routeHandler.handleLocalScheme(android.net.Uri.parse(url), context)
            }
            "openWebPage" -> {
                if (params.optInt("type", 0) == 0) {
                    params.optString("url").takeIf { it.isNotBlank() }?.let(host::openWebPage)
                }
            }
            "closeWebView" -> host.closeWebView(params.optInt("allPage", 0) == 1)
            "storage" -> handleStorage(params)
            "isPrivacyAgreed" -> {
                val agreed = if (privacyStore.isPrivacyAgreed()) 1 else 0
                val inner = JSONObject().put("id", params.id).put("agreed", agreed)
                postResult(
                    params.callback,
                    JSONObject()
                        .put("isPrivacyAgreed", inner)
                        .put("storage", inner)
                        .toString(),
                )
            }
            "setPrivacyAgreed" -> {
                privacyStore.setPrivacyAgreed(params.optInt("agreed") == 1)
                if (params.callback.isNotBlank()) {
                    postResult(params.callback, nested("setPrivacyAgreed", params.id) { put("agreed", params.optInt("agreed")) })
                }
            }
            "importJWCFinish" -> host.onImportJwcFinish()
            "createWidget", "sendBroadcast", "sendLocalBroadcast" -> {
                CourseWidgetProvider.requestRefresh(context)
                if (params.callback.isNotBlank()) {
                    postResult(params.callback, nested(action, params.id) { put("status", true) })
                }
            }
        }
    }

    private fun handleStorage(params: JSONObject) {
        val key = params.optString("key")
        if (key.isBlank()) return
        when (params.optString("action")) {
            "put" -> privacyStore.putStorage(key, params.optString("value"))
            "remove" -> privacyStore.removeStorage(key)
            "get" -> {
                postResult(
                    params.callback,
                    nested("storage", params.id) {
                        put("value", privacyStore.getStorage(key))
                    },
                )
            }
        }
    }

    private fun appVersion(id: String): String {
        return nested("appVersion", id) {
            put("version", BuildConfig.VERSION_CODE)
            put("versionName", BuildConfig.VERSION_NAME)
            put("packageName", XiaoAiConstants.CompatibilityPackageName)
        }
    }

    private suspend fun userInfo(id: String): String {
        val session = accountRepository.currentFreshSession()
        return nested("getUserInfo", id) {
            put("appId", XiaoAiConstants.AppId)
            put("deviceId", privacyStore.deviceId())
            put("is_login", (session?.isLoggedIn == true).toString())
            put("cUserId", session?.openId.orEmpty())
            put("serviceToken", session?.accessToken.orEmpty())
            put("userId", session?.userId.orEmpty())
            put("userName", session?.userName.orEmpty())
            put("userIcon", session?.userIcon.orEmpty())
        }
    }

    private fun nested(action: String, id: String, build: JSONObject.() -> Unit): String {
        val inner = JSONObject().put("id", id)
        inner.build()
        return JSONObject().put(action, inner).toString()
    }

    private fun postResult(callback: String, payload: String) {
        postDataToJs(webView, callback, payload)
    }

    private val JSONObject.id: String
        get() = optString("id")

    private val JSONObject.callback: String
        get() = optString("callback")

    companion object {
        fun postDataToJs(webView: WebView, callback: String, payload: String) {
            val functionName = callback.ifBlank { "postData" }
            val js = "javascript:$functionName(${JSONObject.quote(payload)});"
            webView.post {
                webView.evaluateJavascript(js, null)
            }
        }

        fun loginPayload(id: String, isLogin: Boolean): String {
            return JSONObject()
                .put(
                    "login",
                    JSONObject()
                        .put("id", id)
                        .put("is_login", if (isLogin) 1 else 0),
                )
                .toString()
        }
    }
}
