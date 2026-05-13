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
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import android.webkit.CookieManager
import android.webkit.WebStorage
import com.neoruaa.xiaoaischedule.R
import com.neoruaa.xiaoaischedule.account.AccountRepository
import com.neoruaa.xiaoaischedule.data.PrivacyStore
import com.neoruaa.xiaoaischedule.ui.LoginDialog
import com.neoruaa.xiaoaischedule.ui.SimpleTopBar
import com.neoruaa.xiaoaischedule.ui.theme.XiaoaischeduleTheme

class PrivacyRevokeActivity : ComponentActivity(), BridgeHost {
    private lateinit var privacyStore: PrivacyStore
    private lateinit var accountRepository: AccountRepository
    private lateinit var routeHandler: NativeRouteHandler
    private lateinit var fileChooserDelegate: WebFileChooserDelegate
    private var loginRequest by mutableStateOf<BridgeLoginRequest?>(null)
    private var showConfirm by mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        privacyStore = PrivacyStore(this)
        accountRepository = AccountRepository(this, privacyStore)
        routeHandler = NativeRouteHandler()
        fileChooserDelegate = WebFileChooserDelegate(this)
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

    @Composable
    private fun PrivacyRevokeContent(url: String) {
        val scope = rememberCoroutineScope()
        Box(Modifier.fillMaxSize().background(Color.White)) {
            Column(Modifier.fillMaxSize()) {
                SimpleTopBar(title = stringResource(R.string.app_name), onBack = { finish() })
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
                )
                Button(
                    onClick = { showConfirm = true },
                    modifier = Modifier.fillMaxWidth().padding(20.dp),
                ) {
                    Text(text = stringResource(R.string.privacy_revoke))
                }
            }
            if (showConfirm) {
                AlertDialog(
                    onDismissRequest = { showConfirm = false },
                    title = { Text(stringResource(R.string.privacy_revoke_dialog_title)) },
                    text = { Text(stringResource(R.string.privacy_revoke_dialog_msg)) },
                    confirmButton = {
                        Button(
                            onClick = {
                                accountRepository.logout(clearSavedPassword = true)
                                privacyStore.clearAllLocalData()
                                CookieManager.getInstance().removeAllCookies(null)
                                WebStorage.getInstance().deleteAllData()
                                finishAffinity()
                            },
                        ) {
                            Text(stringResource(R.string.ok))
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { showConfirm = false }) {
                            Text(stringResource(R.string.cancel))
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
