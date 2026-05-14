package com.neoruaa.xiaoaischedule.importer

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.neoruaa.xiaoaischedule.ui.MiuixPageScaffold
import com.neoruaa.xiaoaischedule.ui.theme.XiaoaischeduleTheme
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

class OpenSourceNoticesActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            XiaoaischeduleTheme {
                NoticesContent()
            }
        }
    }

    @Composable
    private fun NoticesContent() {
        MiuixPageScaffold(
            title = "开源项目引用",
            onBack = { finish() },
        ) { padding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
            ) {
                OpenSourceProjects.forEachIndexed { index, item ->
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(MiuixTheme.colorScheme.surfaceVariant, RoundedCornerShape(8.dp))
                            .clickable { openUrl(item.url) }
                            .padding(16.dp),
                    ) {
                        Text(text = item.name, fontWeight = FontWeight.SemiBold)
                        Text(text = item.author, modifier = Modifier.padding(top = 4.dp), color = Color(0x99000000))
                        Text(text = item.license, modifier = Modifier.padding(top = 4.dp), color = Color(0x99000000))
                    }
                    if (index != OpenSourceProjects.lastIndex) Spacer(Modifier.height(10.dp))
                }
            }
        }
    }

    private fun openUrl(url: String) {
        runCatching { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
    }

    companion object {
        fun start(context: Context) {
            val intent = Intent(context, OpenSourceNoticesActivity::class.java)
            if (context !is Activity) intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
        }
    }
}

private data class OpenSourceProject(
    val name: String,
    val author: String,
    val license: String,
    val url: String,
)

private val OpenSourceProjects = listOf(
    OpenSourceProject("shiguang_warehouse", "XingHeYuZhuan", "MIT", "https://github.com/XingHeYuZhuan/shiguang_warehouse"),
    OpenSourceProject("ai-schedule-import-app", "litedream", "MIT", "https://gitee.com/litedream/ai-schedule-import-app"),
    OpenSourceProject("YuKiHookAPI", "HighCapable", "Apache-2.0", "https://github.com/HighCapable/YuKiHookAPI"),
    OpenSourceProject("KavaRef", "HighCapable", "Apache-2.0", "https://github.com/HighCapable/KavaRef"),
    OpenSourceProject("Hikage", "BetterAndroid", "Apache-2.0", "https://github.com/BetterAndroid/Hikage"),
    OpenSourceProject("BetterAndroid", "BetterAndroid", "Apache-2.0", "https://github.com/BetterAndroid/BetterAndroid"),
    OpenSourceProject("DrawableToolbox", "duanhong169", "Apache-2.0", "https://github.com/duanhong169/DrawableToolbox"),
    OpenSourceProject("compose-webview", "KevinnZou", "Apache-2.0", "https://github.com/KevinnZou/compose-webview"),
    OpenSourceProject("DialogX", "kongzue", "Apache-2.0", "https://github.com/kongzue/DialogX"),
    OpenSourceProject("OkHttp", "square", "Apache-2.0", "https://github.com/square/okhttp"),
    OpenSourceProject("Coil", "coil-kt", "Apache-2.0", "https://github.com/coil-kt/coil"),
    OpenSourceProject("kotlinx.serialization", "Kotlin", "Apache-2.0", "https://github.com/Kotlin/kotlinx.serialization"),
    OpenSourceProject("miuix", "yukonga", "Apache-2.0", "https://github.com/miuix-kotlin-multiplatform/miuix"),
    OpenSourceProject("Material Components", "material-components", "Apache-2.0", "https://github.com/material-components/material-components-android"),
    OpenSourceProject("XpHelper", "suzhelan", "No License", "https://github.com/suzhelan/XpHelper"),
)
