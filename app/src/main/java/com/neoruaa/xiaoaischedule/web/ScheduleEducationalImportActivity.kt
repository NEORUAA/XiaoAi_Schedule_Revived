package com.neoruaa.xiaoaischedule.web

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Color.parseColor
import android.os.Bundle
import android.webkit.WebView
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.neoruaa.xiaoaischedule.R
import com.neoruaa.xiaoaischedule.account.AccountRepository
import com.neoruaa.xiaoaischedule.core.AppEvents
import com.neoruaa.xiaoaischedule.data.PrivacyStore
import com.neoruaa.xiaoaischedule.ui.LoginDialog
import com.neoruaa.xiaoaischedule.ui.SimpleTopBar
import com.neoruaa.xiaoaischedule.ui.theme.XiaoaischeduleTheme
import org.json.JSONObject

class ScheduleEducationalImportActivity : ComponentActivity(), BridgeHost {
    private lateinit var privacyStore: PrivacyStore
    private lateinit var accountRepository: AccountRepository
    private lateinit var routeHandler: NativeRouteHandler
    private lateinit var fileChooserDelegate: WebFileChooserDelegate
    private var loginRequest by mutableStateOf<BridgeLoginRequest?>(null)
    private var webView: WebView? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        privacyStore = PrivacyStore(this)
        accountRepository = AccountRepository(this, privacyStore)
        routeHandler = NativeRouteHandler()
        fileChooserDelegate = WebFileChooserDelegate(this)
        val params = parseParams(intent.getStringExtra(ExtraParams).orEmpty())

        setContent {
            XiaoaischeduleTheme {
                ImportContent(params)
            }
        }
    }

    override fun onLoginRequested(request: BridgeLoginRequest) {
        loginRequest = request
    }

    override fun openWebPage(url: String) {
        WebContainerActivity.start(this, url)
    }

    override fun closeWebView(allPages: Boolean) {
        finish()
    }

    override fun onImportJwcFinish() {
        AppEvents.importFinished.tryEmit(Unit)
        finish()
    }

    @Composable
    private fun ImportContent(params: ImportParams) {
        val scope = rememberCoroutineScope()
        Box(Modifier.fillMaxSize().background(Color.White)) {
            Column(Modifier.fillMaxSize()) {
                SimpleTopBar(title = stringResource(R.string.schedule_educational_import), onBack = { finish() })
                XiaoAiWebView(
                    url = params.url,
                    visible = true,
                    accountRepository = accountRepository,
                    privacyStore = privacyStore,
                    routeHandler = routeHandler,
                    fileChooserDelegate = fileChooserDelegate,
                    host = this@ScheduleEducationalImportActivity,
                    scope = scope,
                    modifier = Modifier.weight(1f),
                    onWebViewReady = { webView = it },
                )
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(params.backgroundColor)
                        .padding(horizontal = 20.dp, vertical = 14.dp),
                ) {
                    Text(
                        text = params.title,
                        color = params.titleColor,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        text = params.text,
                        color = params.textColor,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                    Button(
                        onClick = {
                            params.script.takeIf { it.isNotBlank() }?.let {
                                webView?.evaluateJavascript("javascript:$it", null)
                            }
                        },
                        modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                    ) {
                        Text(text = params.buttonText.ifBlank { stringResource(R.string.ok) })
                    }
                }
            }
            loginRequest?.let { request ->
                LoginDialog(
                    request = request,
                    accountRepository = accountRepository,
                    onDismiss = {
                        XiaoAiBridge.postDataToJs(request.webView, request.callback, XiaoAiBridge.loginPayload(request.id, false))
                        loginRequest = null
                    },
                    onResult = { success ->
                        XiaoAiBridge.postDataToJs(request.webView, request.callback, XiaoAiBridge.loginPayload(request.id, success))
                        loginRequest = null
                    },
                )
            }
        }
    }

    private fun parseParams(raw: String): ImportParams {
        val json = runCatching { JSONObject(raw) }.getOrNull() ?: JSONObject()
        return ImportParams(
            url = json.optString("url"),
            script = json.optString("script"),
            title = json.optString("title"),
            text = json.optString("text"),
            buttonText = json.optString("buttonText"),
            titleColor = parseComposeColor(json.optString("titleColor"), Color(0xE6000000)),
            textColor = parseComposeColor(json.optString("textColor"), Color(0x99000000)),
            backgroundColor = parseComposeColor(json.optString("backgroundColor"), Color.White),
        )
    }

    private fun parseComposeColor(value: String, fallback: Color): Color {
        return runCatching { Color(parseColor(value)) }.getOrDefault(fallback)
    }

    data class ImportParams(
        val url: String,
        val script: String,
        val title: String,
        val text: String,
        val buttonText: String,
        val titleColor: Color,
        val textColor: Color,
        val backgroundColor: Color,
    )

    companion object {
        private const val ExtraParams = "extra_params"

        fun start(context: Context, params: String) {
            val intent = Intent(context, ScheduleEducationalImportActivity::class.java).putExtra(ExtraParams, params)
            if (context !is Activity) intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
        }
    }
}
