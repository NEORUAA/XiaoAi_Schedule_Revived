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
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.neoruaa.xiaoaischedule.ui.MiuixPageScaffold
import com.neoruaa.xiaoaischedule.ui.theme.XiaoaischeduleTheme
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.window.WindowDialog

class ModuleAboutActivity : ComponentActivity() {
    private lateinit var repository: ScheduleImportRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        repository = ScheduleImportRepository(this)
        setContent {
            XiaoaischeduleTheme {
                AboutContent()
            }
        }
    }

    @Composable
    private fun AboutContent() {
        var showSourceDialog by remember { mutableStateOf(false) }
        var showDebugDialog by remember { mutableStateOf(false) }
        MiuixPageScaffold(
            title = "关于模块",
            onBack = { finish() },
        ) { padding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                HeaderCard()
                Section(
                    title = "调试",
                    rows = listOf(
                        AboutRow("JavaScript注入", "调用小爱的界面点击导入后自动注入JavaScript") {
                            showDebugDialog = true
                        },
                        AboutRow("自定义拾光仓库源", "可配置仓库 URL 和脚本分支") {
                            showSourceDialog = true
                        },
                        AboutRow("开源项目引用", "本项目使用或参考的开源项目") {
                            OpenSourceNoticesActivity.start(this@ModuleAboutActivity)
                        },
                    ),
                )
                Section(
                    title = "开发者",
                    rows = listOf(
                        AboutRow("帕帝天秀", "小爱课程表的忠实粉丝") { openQq("3373587110") },
                        AboutRow("Mercury", "AI导入课表部分全部代码实现；超级小爱适配") { openQq("3038899204") },
                        AboutRow("颜致恒plus", "教务导入思路提供者") { openQq("2488971290") },
                    ),
                )
                Section(
                    title = "贡献者",
                    rows = listOf(
                        AboutRow("川意", "项目贡献者") { openQq("3299699002") },
                        AboutRow("Aven Cole", "项目贡献者") { openQq("1587005702") },
                    ),
                )
            }
        }
        if (showSourceDialog) {
            ShiguangSourceDialog(
                repository = repository,
                onDismiss = { showSourceDialog = false },
            )
        }
        if (showDebugDialog) {
            DebugScriptDialog(
                repository = repository,
                onDismiss = { showDebugDialog = false },
                onOpen = { url, script ->
                    showDebugDialog = false
                    ImportWebViewActivity.start(
                        context = this,
                        url = url,
                        script = script,
                        title = "JavaScript注入",
                        schoolName = "调试导入",
                        source = "debug",
                    )
                },
            )
        }
    }

    private fun openQq(uin: String) {
        openUri("mqqwpa://im/chat?chat_type=wpa&uin=$uin")
    }

    private fun openUri(uri: String) {
        runCatching { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(uri))) }
    }

    companion object {
        fun start(context: Context) {
            val intent = Intent(context, ModuleAboutActivity::class.java)
            if (context !is Activity) intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
        }
    }
}

data class AboutRow(
    val title: String,
    val subtitle: String,
    val onClick: () -> Unit,
)

@Composable
private fun HeaderCard() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MiuixTheme.colorScheme.surfaceVariant, RoundedCornerShape(8.dp))
            .padding(18.dp),
    ) {
        Text(text = "小爱课程表复活计划", fontWeight = FontWeight.SemiBold)
        Text(
            text = "恢复超级小爱内置课程表导入能力，并复刻到当前独立课程表应用。",
            modifier = Modifier.padding(top = 8.dp),
            color = Color(0x99000000),
        )
    }
}

@Composable
private fun Section(title: String, rows: List<AboutRow>) {
    Column {
        Text(
            text = title,
            modifier = Modifier.padding(start = 4.dp, bottom = 8.dp),
            color = Color(0x99000000),
            fontWeight = FontWeight.SemiBold,
        )
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(MiuixTheme.colorScheme.surfaceVariant, RoundedCornerShape(8.dp)),
        ) {
            rows.forEachIndexed { index, row ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(onClick = row.onClick)
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(text = row.title, fontWeight = FontWeight.Medium)
                        Text(
                            text = row.subtitle,
                            modifier = Modifier.padding(top = 4.dp),
                            color = Color(0x99000000),
                        )
                    }
                    Text(text = "›", color = Color(0x66000000))
                }
                if (index != rows.lastIndex) Spacer(Modifier.height(1.dp).fillMaxWidth().background(Color(0x14000000)))
            }
        }
    }
}

@Composable
private fun ShiguangSourceDialog(
    repository: ScheduleImportRepository,
    onDismiss: () -> Unit,
) {
    var repo by remember { mutableStateOf(repository.shiguangRepoUrl()) }
    var branch by remember { mutableStateOf(repository.shiguangBranch()) }
    WindowDialog(
        show = true,
        onDismissRequest = onDismiss,
        title = "自定义拾光仓库源",
        content = {
            Column {
                TextField(
                    value = repo,
                    onValueChange = { repo = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = "仓库 URL",
                    useLabelAsPlaceholder = true,
                    singleLine = true,
                )
                TextField(
                    value = branch,
                    onValueChange = { branch = it },
                    modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                    label = "脚本分支",
                    useLabelAsPlaceholder = true,
                    singleLine = true,
                )
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 20.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    TextButton(
                        modifier = Modifier.weight(1f),
                        text = "重置",
                        onClick = {
                            repository.resetShiguangSource()
                            onDismiss()
                        },
                    )
                    TextButton(
                        modifier = Modifier.weight(1f),
                        text = "保存",
                        colors = ButtonDefaults.textButtonColorsPrimary(),
                        onClick = {
                            repository.setShiguangSource(repo, branch)
                            onDismiss()
                        },
                    )
                }
            }
        },
    )
}

@Composable
private fun DebugScriptDialog(
    repository: ScheduleImportRepository,
    onDismiss: () -> Unit,
    onOpen: (String, String) -> Unit,
) {
    var url by remember { mutableStateOf(repository.debugUrl()) }
    var script by remember { mutableStateOf(repository.debugScript()) }
    WindowDialog(
        show = true,
        onDismissRequest = onDismiss,
        title = "JavaScript注入",
        content = {
            Column {
                TextField(
                    value = url,
                    onValueChange = { url = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = "网页 URL",
                    useLabelAsPlaceholder = true,
                    singleLine = true,
                )
                TextField(
                    value = script,
                    onValueChange = { script = it },
                    modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                    label = "注入脚本",
                    useLabelAsPlaceholder = true,
                    minLines = 4,
                )
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 20.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    TextButton(
                        modifier = Modifier.weight(1f),
                        text = "取消",
                        onClick = onDismiss,
                    )
                    TextButton(
                        modifier = Modifier.weight(1f),
                        text = "打开",
                        colors = ButtonDefaults.textButtonColorsPrimary(),
                        onClick = {
                            repository.setDebugScript(url, script)
                            onOpen(url, script)
                        },
                    )
                }
            }
        },
    )
}
