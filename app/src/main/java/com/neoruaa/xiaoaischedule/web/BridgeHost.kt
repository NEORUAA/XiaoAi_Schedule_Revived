package com.neoruaa.xiaoaischedule.web

import android.webkit.WebView

data class BridgeLoginRequest(
    val id: String,
    val callback: String,
    val webView: WebView,
)

interface BridgeHost {
    fun onLoginRequested(request: BridgeLoginRequest)
    fun openWebPage(url: String)
    fun closeWebView(allPages: Boolean)
    fun onImportJwcFinish()
}
