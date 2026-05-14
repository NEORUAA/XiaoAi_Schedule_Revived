package com.neoruaa.xiaoaischedule.importer

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.neoruaa.xiaoaischedule.ui.MiuixPageScaffold
import com.neoruaa.xiaoaischedule.ui.theme.XiaoaischeduleTheme

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
            ImportLazyColumn(
                padding = padding,
                modifier = Modifier.fillMaxSize(),
            ) {
                miuixSection("开源项目引用") {
                    OpenSourceProjects.forEach { item ->
                        MiuixNavigationRow(
                            title = item.name,
                            summary = "${item.author} · ${item.license}",
                            onClick = { openUrl(item.url) },
                        )
                    }
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
    OpenSourceProject("Kotlin", "JetBrains", "Apache-2.0", "https://github.com/JetBrains/kotlin"),
    OpenSourceProject("kotlinx.coroutines", "Kotlin", "Apache-2.0", "https://github.com/Kotlin/kotlinx.coroutines"),
    OpenSourceProject("kotlinx.serialization", "Kotlin", "Apache-2.0", "https://github.com/Kotlin/kotlinx.serialization"),
    OpenSourceProject("AndroidX Core", "Android Open Source Project", "Apache-2.0", "https://github.com/androidx/androidx"),
    OpenSourceProject("AndroidX Activity", "Android Open Source Project", "Apache-2.0", "https://github.com/androidx/androidx"),
    OpenSourceProject("AndroidX Lifecycle", "Android Open Source Project", "Apache-2.0", "https://github.com/androidx/androidx"),
    OpenSourceProject("Jetpack Compose", "Android Open Source Project", "Apache-2.0", "https://github.com/androidx/androidx"),
    OpenSourceProject("Ktor", "JetBrains", "Apache-2.0", "https://github.com/ktorio/ktor"),
    OpenSourceProject("miuix", "yukonga", "Apache-2.0", "https://github.com/miuix-kotlin-multiplatform/miuix"),
    OpenSourceProject("InstallerX-Revived", "InstallerX Revived contributors", "GPL-3.0", "https://github.com/wxxsfxyzm/InstallerX-Revived"),
    OpenSourceProject("shiguang_warehouse", "XingHeYuZhuan", "MIT", "https://github.com/XingHeYuZhuan/shiguang_warehouse"),
    OpenSourceProject("ai-schedule-import-app", "litedream", "MIT", "https://gitee.com/litedream/ai-schedule-import-app"),
)
