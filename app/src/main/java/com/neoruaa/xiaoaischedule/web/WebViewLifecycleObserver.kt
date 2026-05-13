package com.neoruaa.xiaoaischedule.web

import android.webkit.WebView
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner

class WebViewLifecycleObserver(private val webView: WebView) : DefaultLifecycleObserver {
    override fun onPause(owner: LifecycleOwner) {
        webView.onPause()
    }

    override fun onResume(owner: LifecycleOwner) {
        webView.onResume()
    }
}
