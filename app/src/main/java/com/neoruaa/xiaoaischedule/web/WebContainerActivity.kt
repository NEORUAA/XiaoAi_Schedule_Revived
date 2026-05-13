package com.neoruaa.xiaoaischedule.web

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.neoruaa.xiaoaischedule.account.AccountRepository
import com.neoruaa.xiaoaischedule.data.PrivacyStore
import com.neoruaa.xiaoaischedule.ui.LoginDialog
import com.neoruaa.xiaoaischedule.ui.SimpleTopBar
import com.neoruaa.xiaoaischedule.ui.theme.XiaoaischeduleTheme

class WebContainerActivity : ComponentActivity(), BridgeHost {
    private lateinit var privacyStore: PrivacyStore
    private lateinit var accountRepository: AccountRepository
    private lateinit var routeHandler: NativeRouteHandler
    private lateinit var fileChooserDelegate: WebFileChooserDelegate
    private var loginRequest by mutableStateOf<BridgeLoginRequest?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        privacyStore = PrivacyStore(this)
        accountRepository = AccountRepository(this, privacyStore)
        routeHandler = NativeRouteHandler()
        fileChooserDelegate = WebFileChooserDelegate(this)

        setContent {
            XiaoaischeduleTheme {
                WebContainerContent(
                    url = intent.getStringExtra(ExtraUrl).orEmpty(),
                    title = intent.getStringExtra(ExtraTitle).orEmpty(),
                )
            }
        }
    }

    override fun onLoginRequested(request: BridgeLoginRequest) {
        loginRequest = request
    }

    override fun openWebPage(url: String) {
        start(this, url)
    }

    override fun closeWebView(allPages: Boolean) {
        finish()
    }

    override fun onImportJwcFinish() {
        com.neoruaa.xiaoaischedule.core.AppEvents.importFinished.tryEmit(Unit)
        finish()
    }

    @Composable
    private fun WebContainerContent(url: String, title: String) {
        val scope = rememberCoroutineScope()
        Box(Modifier.fillMaxSize().background(Color.White)) {
            Column(Modifier.fillMaxSize()) {
                SimpleTopBar(title = title.ifBlank { "" }, onBack = { finish() })
                XiaoAiWebView(
                    url = url,
                    visible = true,
                    accountRepository = accountRepository,
                    privacyStore = privacyStore,
                    routeHandler = routeHandler,
                    fileChooserDelegate = fileChooserDelegate,
                    host = this@WebContainerActivity,
                    scope = scope,
                    modifier = Modifier.weight(1f),
                )
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

    companion object {
        private const val ExtraUrl = "extra_url"
        private const val ExtraTitle = "extra_title"

        fun start(context: Context, url: String, title: String = "") {
            val intent = Intent(context, WebContainerActivity::class.java)
                .putExtra(ExtraUrl, url)
                .putExtra(ExtraTitle, title)
            if (context !is Activity) intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
        }
    }
}
