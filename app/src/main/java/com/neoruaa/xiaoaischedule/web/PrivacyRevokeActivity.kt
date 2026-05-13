package com.neoruaa.xiaoaischedule.web

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.webkit.WebView
import androidx.activity.OnBackPressedCallback
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import android.webkit.CookieManager
import android.webkit.WebStorage
import com.neoruaa.xiaoaischedule.R
import com.neoruaa.xiaoaischedule.account.AccountRepository
import com.neoruaa.xiaoaischedule.data.PrivacyStore
import com.neoruaa.xiaoaischedule.ui.LoginDialog
import com.neoruaa.xiaoaischedule.ui.MiuixPageScaffold
import com.neoruaa.xiaoaischedule.ui.theme.XiaoaischeduleTheme
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.window.WindowDialog

class PrivacyRevokeActivity : ComponentActivity(), BridgeHost {
    private lateinit var privacyStore: PrivacyStore
    private lateinit var accountRepository: AccountRepository
    private lateinit var routeHandler: NativeRouteHandler
    private lateinit var fileChooserDelegate: WebFileChooserDelegate
    private var loginRequest by mutableStateOf<BridgeLoginRequest?>(null)
    private var showConfirm by mutableStateOf(false)
    private var webView: WebView? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        privacyStore = PrivacyStore(this)
        accountRepository = AccountRepository(this, privacyStore)
        routeHandler = NativeRouteHandler()
        fileChooserDelegate = WebFileChooserDelegate(this)
        onBackPressedDispatcher.addCallback(
            this,
            object : OnBackPressedCallback(true) {
                override fun handleOnBackPressed() {
                    handleWebBackPressed()
                }
            },
        )
        setContent {
            XiaoaischeduleTheme {
                PrivacyRevokeContent(intent.getStringExtra(ExtraUrl).orEmpty())
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
        finish()
    }

    private fun handleWebBackPressed() {
        val currentWebView = webView
        if (currentWebView?.canGoBack() == true) {
            currentWebView.goBack()
            return
        }
        finish()
    }

    @Composable
    private fun PrivacyRevokeContent(url: String) {
        val scope = rememberCoroutineScope()
        Box(Modifier.fillMaxSize()) {
            MiuixPageScaffold(
                title = stringResource(R.string.app_name),
                onBack = { finish() },
                bottomBar = {
                    Button(
                        onClick = { showConfirm = true },
                        modifier = Modifier.fillMaxWidth().padding(20.dp),
                    ) {
                        Text(
                            text = stringResource(R.string.privacy_revoke),
                            color = MiuixTheme.colorScheme.onPrimary,
                        )
                    }
                },
            ) { paddingValues ->
                Column(Modifier.fillMaxSize().padding(paddingValues)) {
                    XiaoAiWebView(
                        url = url,
                        visible = true,
                        accountRepository = accountRepository,
                        privacyStore = privacyStore,
                        routeHandler = routeHandler,
                        fileChooserDelegate = fileChooserDelegate,
                        host = this@PrivacyRevokeActivity,
                        scope = scope,
                        openHttpExternally = true,
                        modifier = Modifier.weight(1f),
                        onWebViewReady = { webView = it },
                    )
                }
            }
            if (showConfirm) {
                WindowDialog(
                    show = true,
                    onDismissRequest = { showConfirm = false },
                    title = stringResource(R.string.privacy_revoke_dialog_title),
                    content = {
                        Column {
                            Text(stringResource(R.string.privacy_revoke_dialog_msg))
                            Spacer(modifier = Modifier.height(24.dp))
                            Row(modifier = Modifier.fillMaxWidth()) {
                                TextButton(
                                    modifier = Modifier.weight(1f),
                                    text = stringResource(R.string.cancel),
                                    onClick = { showConfirm = false },
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                TextButton(
                                    modifier = Modifier.weight(1f),
                                    text = stringResource(R.string.ok),
                                    colors = ButtonDefaults.textButtonColorsPrimary(),
                                    onClick = {
                                        accountRepository.logout(clearSavedPassword = true)
                                        privacyStore.clearAllLocalData()
                                        CookieManager.getInstance().removeAllCookies(null)
                                        WebStorage.getInstance().deleteAllData()
                                        finishAffinity()
                                    },
                                )
                            }
                        }
                    },
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

        fun start(context: Context, url: String) {
            val intent = Intent(context, PrivacyRevokeActivity::class.java).putExtra(ExtraUrl, url)
            if (context !is Activity) intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
        }
    }
}
