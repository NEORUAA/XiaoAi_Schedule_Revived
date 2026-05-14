package com.neoruaa.xiaoaischedule.web

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.content.res.Configuration
import android.view.View
import android.view.ViewGroup
import android.webkit.JsResult
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.zIndex
import com.neoruaa.xiaoaischedule.R
import com.neoruaa.xiaoaischedule.account.AccountRepository
import com.neoruaa.xiaoaischedule.data.PrivacyStore
import com.neoruaa.xiaoaischedule.importer.ImportJs
import com.neoruaa.xiaoaischedule.importer.ScheduleRepairAndroidBridge
import kotlin.coroutines.resume
import kotlin.math.roundToInt
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.suspendCancellableCoroutine
import org.json.JSONObject
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun XiaoAiWebView(
    url: String,
    visible: Boolean,
    accountRepository: AccountRepository,
    privacyStore: PrivacyStore,
    routeHandler: NativeRouteHandler,
    fileChooserDelegate: WebFileChooserDelegate,
    host: BridgeHost,
    scope: CoroutineScope,
    modifier: Modifier = Modifier,
    openHttpExternally: Boolean = false,
    onWebViewReady: (WebView) -> Unit = {},
) {
    val context = LocalContext.current
    var hasError by remember { mutableStateOf(false) }
    var loaded by rememberSaveable(url) { mutableStateOf(false) }
    val xiaoAiCss = remember(context) {
        runCatching {
            context.assets.open(XiaoAiCssAsset).bufferedReader().use { it.readText() }
        }.getOrDefault("")
    }
    val importToolsJs = remember(context) {
        runCatching {
            context.assets.open(XiaoAiImportToolsAsset).bufferedReader().use { it.readText() }
        }.getOrDefault("")
    }

    val webView = remember(url) {
        WebView(context).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT,
            )
            overScrollMode = View.OVER_SCROLL_NEVER
            setBackgroundColor(android.graphics.Color.TRANSPARENT)
            settings.configure(context)
        }
    }

    DisposableEffect(webView) {
        val activity = context as? ComponentActivity
        webView.addJavascriptInterface(
            XiaoAiBridge(
                context = context,
                webView = webView,
                accountRepository = accountRepository,
                privacyStore = privacyStore,
                routeHandler = routeHandler,
                host = host,
                scope = scope,
            ),
            "app",
        )
        webView.addJavascriptInterface(ScheduleRepairAndroidBridge(context), "Android")
        webView.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                return handleUrl(request.url)
            }

            @Deprecated("Deprecated in Java")
            override fun shouldOverrideUrlLoading(view: WebView, url: String): Boolean {
                return handleUrl(Uri.parse(url))
            }

            override fun onPageStarted(view: WebView, url: String?, favicon: Bitmap?) {
                hasError = false
            }

            override fun onPageFinished(view: WebView, url: String?) {
                super.onPageFinished(view, url)
                view.injectXiaoAiTheme(context.isSystemNightMode())
                view.injectXiaoAiCss(xiaoAiCss)
                view.injectXiaoAiSafeArea(context.statusBarCssPx())
                view.injectXiaoAiImportTools(importToolsJs)
                view.evaluateJavascript(ImportJs.SettingPatch, null)
            }

            override fun onReceivedError(
                view: WebView,
                request: WebResourceRequest,
                error: WebResourceError,
            ) {
                if (request.isForMainFrame) hasError = true
            }

            override fun onReceivedHttpError(
                view: WebView,
                request: WebResourceRequest,
                errorResponse: WebResourceResponse,
            ) {
                if (request.isForMainFrame && errorResponse.statusCode >= 400) hasError = true
            }

            private fun handleUrl(uri: Uri): Boolean {
                val scheme = uri.scheme.orEmpty()
                if (scheme == "tbopen") return true
                if (scheme == "aischedule" || scheme == "xiaoailite") {
                    return routeHandler.handleLocalScheme(uri, context)
                }
                if (openHttpExternally && (scheme == "http" || scheme == "https")) {
                    return runCatching {
                        context.startActivity(Intent(Intent.ACTION_VIEW, uri))
                        true
                    }.getOrDefault(false)
                }
                if (scheme != "http" && scheme != "https" && scheme.isNotBlank()) {
                    return runCatching {
                        context.startActivity(Intent(Intent.ACTION_VIEW, uri))
                        true
                    }.getOrDefault(true)
                }
                return false
            }
        }
        webView.webChromeClient = object : WebChromeClient() {
            override fun onShowFileChooser(
                webView: WebView,
                filePathCallback: android.webkit.ValueCallback<Array<Uri>>,
                fileChooserParams: FileChooserParams,
            ): Boolean {
                return fileChooserDelegate.showFileChooser(filePathCallback, fileChooserParams)
            }

            override fun onJsAlert(view: WebView?, url: String?, message: String?, result: JsResult?): Boolean {
                result?.confirm()
                return true
            }
        }
        val viewportLayoutListener = View.OnLayoutChangeListener { view, left, top, right, bottom, _, _, _, _ ->
            view.applyExactViewportLayoutParams(right - left, bottom - top)
        }
        webView.addOnLayoutChangeListener(viewportLayoutListener)
        val observer = WebViewLifecycleObserver(webView)
        activity?.lifecycle?.addObserver(observer)
        onDispose {
            webView.removeOnLayoutChangeListener(viewportLayoutListener)
            activity?.lifecycle?.removeObserver(observer)
        }
    }

    LaunchedEffect(visible, url) {
        if (visible && !loaded) {
            webView.awaitNonZeroViewport()
            loaded = true
            webView.loadUrl(url)
        }
    }

    Box(modifier = modifier.zIndex(if (visible) 1f else 0f)) {
        AndroidView(
            factory = { webView },
            update = {
                val nextVisibility = if (visible) View.VISIBLE else View.INVISIBLE
                if (it.visibility != nextVisibility) {
                    it.visibility = nextVisibility
                    it.requestLayout()
                }
                it.isEnabled = visible
                it.isClickable = visible
                it.isFocusable = visible
                it.isFocusableInTouchMode = visible
                onWebViewReady(it)
            },
            modifier = Modifier.fillMaxSize(),
        )
        if (visible && hasError) {
            WebErrorView(
                onRetry = {
                    hasError = false
                    webView.settings.cacheMode = WebSettings.LOAD_DEFAULT
                    webView.reload()
                },
            )
        }
    }
}

private fun WebView.injectXiaoAiCss(css: String) {
    if (css.isBlank()) return
    val quotedCss = JSONObject.quote(css)
    evaluateJavascript(
        """
            (function() {
              var id = 'xiaoai-assets-style';
              var style = document.getElementById(id);
              if (!style) {
                style = document.createElement('style');
                style.id = id;
                (document.head || document.documentElement).appendChild(style);
              }
              style.textContent = $quotedCss;
            })();
        """.trimIndent(),
        null,
    )
}

private fun WebView.injectXiaoAiTheme(dark: Boolean) {
    val theme = if (dark) "dark" else "light"
    evaluateJavascript(
        """
            (function() {
              var theme = ${JSONObject.quote(theme)};
              function applyTheme() {
                var root = document.documentElement;
                if (!root) return;
                if (root.getAttribute('data-theme') !== theme) {
                  root.setAttribute('data-theme', theme);
                }
                root.style.colorScheme = theme;
              }
              applyTheme();
              if (!window.__xiaoAiThemeObserver && document.documentElement) {
                window.__xiaoAiThemeObserver = new MutationObserver(applyTheme);
                window.__xiaoAiThemeObserver.observe(document.documentElement, {
                  attributes: true,
                  attributeFilter: ['data-theme', 'class', 'style']
                });
              }
            })();
        """.trimIndent(),
        null,
    )
}

private fun WebView.injectXiaoAiImportTools(script: String) {
    if (script.isBlank()) return
    evaluateJavascript(
        """
            (function() {
              if (window.__xiaoAiImportToolsInjected) return;
              window.__xiaoAiImportToolsInjected = true;
              try {
                ${script}
                if (typeof AIScheduleTools === 'function') {
                  AIScheduleTools();
                }
              } catch (e) {
                console.warn('xiaoai import tools inject failed', e);
              }
            })();
        """.trimIndent(),
        null,
    )
}

private fun WebView.injectXiaoAiSafeArea(statusBarHeight: Int) {
    evaluateJavascript(
        XiaoAiSafeAreaPatch.replace("__STATUS_BAR_HEIGHT__", statusBarHeight.toString()),
        null,
    )
}

private fun Context.statusBarCssPx(): Int {
    val resourceId = resources.getIdentifier("status_bar_height", "dimen", "android")
    val statusBarPx = if (resourceId > 0) resources.getDimensionPixelSize(resourceId) else 0
    return (statusBarPx / resources.displayMetrics.density).roundToInt().coerceAtLeast(0)
}

private suspend fun WebView.awaitNonZeroViewport() {
    if (hasNonZeroViewport()) return
    suspendCancellableCoroutine { continuation ->
        var completed = false
        lateinit var layoutListener: View.OnLayoutChangeListener
        lateinit var attachListener: View.OnAttachStateChangeListener

        fun cleanup() {
            removeOnLayoutChangeListener(layoutListener)
            removeOnAttachStateChangeListener(attachListener)
        }

        fun completeIfReady() {
            if (!completed && hasNonZeroViewport() && continuation.isActive) {
                completed = true
                applyExactViewportLayoutParams(width, height)
                cleanup()
                continuation.resume(Unit)
            }
        }

        layoutListener = View.OnLayoutChangeListener { _, _, _, _, _, _, _, _, _ ->
            completeIfReady()
        }
        attachListener = object : View.OnAttachStateChangeListener {
            override fun onViewAttachedToWindow(view: View) {
                completeIfReady()
            }

            override fun onViewDetachedFromWindow(view: View) = Unit
        }

        addOnLayoutChangeListener(layoutListener)
        addOnAttachStateChangeListener(attachListener)
        post { completeIfReady() }
        continuation.invokeOnCancellation {
            if (!completed) {
                completed = true
                cleanup()
            }
        }
    }
}

private fun WebView.hasNonZeroViewport(): Boolean {
    return isAttachedToWindow && width > 0 && height > 0
}

private fun View.applyExactViewportLayoutParams(width: Int, height: Int) {
    if (width <= 0 || height <= 0) return
    val current = layoutParams
    if (current == null) {
        layoutParams = ViewGroup.LayoutParams(width, height)
        return
    }
    if (current.width != width || current.height != height) {
        current.width = width
        current.height = height
        layoutParams = current
    }
}

private const val XiaoAiCssAsset = "xiaoai.css"
private const val XiaoAiImportToolsAsset = "aischedule_tools.js"

private val XiaoAiSafeAreaPatch = """
    (function() {
      var statusBarHeight = __STATUS_BAR_HEIGHT__;
      if (window.__xiaoAiSafeAreaPatchInstalled) {
        if (window.__xiaoAiSafeAreaPatchRun) {
          window.__xiaoAiSafeAreaPatchRun(statusBarHeight);
        }
        return;
      }
      window.__xiaoAiSafeAreaPatchInstalled = true;

      function setImportantStyle(node, name, value) {
        if (!node) {
          return;
        }
        if (
          node.style.getPropertyValue(name) !== value ||
          node.style.getPropertyPriority(name) !== 'important'
        ) {
          node.style.setProperty(name, value, 'important');
        }
      }

      function installBridgeInsets(height) {
        window.__xiaoAiStatusBarHeight = height;
        if (window.jsBridge && !window.jsBridge.getStatusBarHeight) {
          window.jsBridge.getStatusBarHeight = function() {
            return Promise.resolve(height);
          };
        }
      }

      function patchTodayPage(height) {
        var root = document.getElementById('root');
        if (!root || !/(^|\/)today_?lesson(?:[/?#]|${'$'})/.test(window.location.hash || '')) {
          return;
        }
        var headers = root.querySelectorAll('[class^="header___"], [class*=" header___"]');
        for (var i = 0; i < headers.length; i += 1) {
          setImportantStyle(headers[i], 'padding-top', height + 'px');
        }
      }

      function patch(height) {
        document.documentElement.style.setProperty('--xiaoai-status-bar-height', height + 'px');
        installBridgeInsets(height);
        patchTodayPage(height);
      }

      var pending = false;
      window.__xiaoAiSafeAreaPatchRun = function(height) {
        if (pending) {
          return;
        }
        pending = true;
        setTimeout(function() {
          pending = false;
          patch(height);
          setTimeout(function() { patch(height); }, 50);
          setTimeout(function() { patch(height); }, 250);
        }, 0);
      };

      var observer = new MutationObserver(function() {
        window.__xiaoAiSafeAreaPatchRun(statusBarHeight);
      });
      observer.observe(document.documentElement, {
        childList: true,
        subtree: true,
        attributes: true,
        attributeFilter: ['class', 'style']
      });

      window.addEventListener('hashchange', function() {
        window.__xiaoAiSafeAreaPatchRun(statusBarHeight);
      });
      window.__xiaoAiSafeAreaPatchRun(statusBarHeight);
    })();
""".trimIndent()

@Suppress("DEPRECATION")
private fun WebSettings.configure(context: Context) {
    javaScriptEnabled = true
    domStorageEnabled = true
    databaseEnabled = true
    loadsImagesAutomatically = true
    allowFileAccess = true
    allowContentAccess = true
    useWideViewPort = true
    loadWithOverviewMode = true
    setSupportZoom(false)
    builtInZoomControls = false
    displayZoomControls = false
    mediaPlaybackRequiresUserGesture = false
    cacheMode = WebSettings.LOAD_DEFAULT
    mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
}

private fun Context.isSystemNightMode(): Boolean {
    return (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
}

@Composable
private fun WebErrorView(onRetry: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFF8FBFD))
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(text = stringResource(R.string.main_tab_no_network_retry), color = Color(0x99000000))
        Button(modifier = Modifier.padding(top = 14.dp), onClick = onRetry) {
            Text(
                text = stringResource(R.string.main_tab_click_to_retry),
                color = MiuixTheme.colorScheme.onPrimary,
            )
        }
    }
}
