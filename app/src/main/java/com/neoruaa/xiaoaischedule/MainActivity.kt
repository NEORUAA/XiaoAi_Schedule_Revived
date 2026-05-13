package com.neoruaa.xiaoaischedule

import android.content.Intent
import android.os.Bundle
import android.webkit.WebView
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.neoruaa.xiaoaischedule.account.AccountRepository
import com.neoruaa.xiaoaischedule.core.AppEvents
import com.neoruaa.xiaoaischedule.core.XiaoAiConstants
import com.neoruaa.xiaoaischedule.data.PrivacyStore
import com.neoruaa.xiaoaischedule.ui.LoginDialog
import com.neoruaa.xiaoaischedule.ui.PrivacyAgreementDialog
import com.neoruaa.xiaoaischedule.ui.theme.XiaoaischeduleTheme
import com.neoruaa.xiaoaischedule.web.BridgeHost
import com.neoruaa.xiaoaischedule.web.BridgeLoginRequest
import com.neoruaa.xiaoaischedule.web.NativeRouteHandler
import com.neoruaa.xiaoaischedule.web.WebContainerActivity
import com.neoruaa.xiaoaischedule.web.WebFileChooserDelegate
import com.neoruaa.xiaoaischedule.web.XiaoAiBridge
import com.neoruaa.xiaoaischedule.web.XiaoAiWebView
import com.neoruaa.xiaoaischedule.widget.CourseWidgetProvider
import java.util.EnumMap

class MainActivity : ComponentActivity(), BridgeHost {
    private lateinit var privacyStore: PrivacyStore
    private lateinit var accountRepository: AccountRepository
    private lateinit var routeHandler: NativeRouteHandler
    private lateinit var fileChooserDelegate: WebFileChooserDelegate

    private var selectedTab by mutableStateOf(MainTab.Today)
    private var loginRequest by mutableStateOf<BridgeLoginRequest?>(null)
    private val webViews = EnumMap<MainTab, WebView>(MainTab::class.java)

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
                    moveTaskToBack(false)
                }
            },
        )

        setContent {
            XiaoaischeduleTheme {
                MainContent()
            }
        }

        routeHandler.handleExternalScheme(intent?.data, this)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        routeHandler.handleExternalScheme(intent.data, this)
    }

    override fun onLoginRequested(request: BridgeLoginRequest) {
        loginRequest = request
    }

    override fun openWebPage(url: String) {
        WebContainerActivity.start(this, url)
    }

    override fun closeWebView(allPages: Boolean) {
        // The original main activity stays alive; closeWebView only closes auxiliary web pages.
    }

    override fun onImportJwcFinish() {
        selectedTab = MainTab.Schedule
        webViews[MainTab.Schedule]?.loadUrl("${XiaoAiConstants.ScheduleUrl}?time=${System.currentTimeMillis()}")
        CourseWidgetProvider.requestRefresh(this)
    }

    @Composable
    private fun MainContent() {
        val privacyAgreed by privacyStore.privacyAgreed.collectAsState()
        val scope = rememberCoroutineScope()

        LaunchedEffect(Unit) {
            AppEvents.importFinished.collect {
                onImportJwcFinish()
            }
        }

        Box(modifier = Modifier.fillMaxSize().background(Color.White)) {
            if (privacyAgreed) {
                Column(modifier = Modifier.fillMaxSize()) {
                    Box(modifier = Modifier.weight(1f)) {
                        MainTab.entries.forEach { tab ->
                            XiaoAiWebView(
                                url = tab.url,
                                visible = selectedTab == tab,
                                accountRepository = accountRepository,
                                privacyStore = privacyStore,
                                routeHandler = routeHandler,
                                fileChooserDelegate = fileChooserDelegate,
                                host = this@MainActivity,
                                scope = scope,
                                modifier = Modifier.fillMaxSize(),
                                onWebViewReady = { webViews[tab] = it },
                            )
                        }
                    }
                    MainBottomBar(
                        selectedTab = selectedTab,
                        onSelected = { selectedTab = it },
                    )
                }
            }

            if (!privacyAgreed) {
                PrivacyAgreementDialog(
                    onAgree = { privacyStore.setPrivacyAgreed(true) },
                    onDisagree = { finish() },
                    onOpenUrl = { WebContainerActivity.start(this@MainActivity, it) },
                )
            }

            loginRequest?.let { request ->
                LoginDialog(
                    request = request,
                    accountRepository = accountRepository,
                    onDismiss = {
                        XiaoAiBridge.postDataToJs(
                            request.webView,
                            request.callback,
                            XiaoAiBridge.loginPayload(request.id, false),
                        )
                        loginRequest = null
                    },
                    onResult = { success ->
                        XiaoAiBridge.postDataToJs(
                            request.webView,
                            request.callback,
                            XiaoAiBridge.loginPayload(request.id, success),
                        )
                        if (success) CourseWidgetProvider.requestRefresh(this@MainActivity)
                        loginRequest = null
                    },
                )
            }
        }
    }
}

private enum class MainTab(
    val labelRes: Int,
    val url: String,
) {
    Today(R.string.main_tab_today, XiaoAiConstants.TodayLessonUrl),
    Schedule(R.string.main_tab_schedule, XiaoAiConstants.ScheduleUrl),
    Mine(R.string.main_tab_my, XiaoAiConstants.MineUrl),
}

@Composable
private fun MainBottomBar(
    selectedTab: MainTab,
    onSelected: (MainTab) -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth().background(Color.White)) {
        HorizontalDivider(color = Color(0x1A000000), thickness = 0.5.dp)
        Row(modifier = Modifier.fillMaxWidth().height(56.dp)) {
            MainTab.entries.forEach { tab ->
                val selected = selectedTab == tab
                TextButton(
                    onClick = { onSelected(tab) },
                    modifier = Modifier.weight(1f).height(56.dp),
                ) {
                    Text(
                        text = stringResource(tab.labelRes),
                        color = if (selected) Color(0xFF0099FF) else Color(0x99000000),
                        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                        fontSize = 12.sp,
                        modifier = Modifier.align(Alignment.CenterVertically),
                    )
                }
            }
        }
    }
}
