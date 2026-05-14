package com.neoruaa.xiaoaischedule.importer

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.neoruaa.xiaoaischedule.ui.MiuixPageScaffold
import com.neoruaa.xiaoaischedule.ui.theme.XiaoaischeduleTheme
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.CircularProgressIndicator
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.window.WindowDialog

class ImportHubActivity : ComponentActivity() {
    private lateinit var repository: ScheduleImportRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        repository = ScheduleImportRepository(this)
        setContent {
            XiaoaischeduleTheme {
                ImportHubContent()
            }
        }
    }

    @Composable
    private fun ImportHubContent() {
        val scope = rememberCoroutineScope()
        var commonSources by remember { mutableStateOf<List<ImportSourceItem>>(emptyList()) }
        var schools by remember { mutableStateOf<List<ShiguangSchool>>(emptyList()) }
        var selectedSchool by remember { mutableStateOf<ShiguangSchool?>(null) }
        var loading by remember { mutableStateOf(false) }
        var message by remember { mutableStateOf("") }
        var showJsonDialog by remember { mutableStateOf(false) }
        var showAiDialog by remember { mutableStateOf(false) }
        var pendingCommonSource by remember { mutableStateOf<ImportSourceItem?>(null) }
        var pendingAdapter by remember { mutableStateOf<PendingAdapterInput?>(null) }

        fun refresh(force: Boolean = false) {
            scope.launch {
                loading = true
                message = "正在加载导入源..."
                runCatching {
                    commonSources = repository.commonSources(force)
                    schools = repository.shiguangSchools(force)
                }.onFailure {
                    message = "导入源加载失败：${it.message.orEmpty()}"
                }.onSuccess {
                    message = "已加载 ${commonSources.size} 个通用源，${schools.size} 个适配学校"
                }
                loading = false
            }
        }

        LaunchedEffect(Unit) {
            refresh(force = false)
        }

        MiuixPageScaffold(
            title = selectedSchool?.name ?: "教务导入",
            onBack = {
                if (selectedSchool != null) selectedSchool = null else finish()
            },
        ) { padding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                if (selectedSchool == null) {
                    QuickActions(
                        onAi = { showAiDialog = true },
                        onJson = { showJsonDialog = true },
                        onRefresh = { refresh(force = true) },
                        loading = loading,
                    )
                    if (message.isNotBlank()) Text(text = message, color = Color(0x99000000))
                    SourceSection("通用教务系统", commonSources) { item ->
                        if (item.url.isBlank()) {
                            pendingCommonSource = item
                        } else {
                            openCommonSource(scope, item, item.url) { loading = it }
                        }
                    }
                    SchoolSection(schools) { selectedSchool = it }
                } else {
                    AdapterSection(
                        school = selectedSchool!!,
                        onAdapter = { school, adapter ->
                            if (adapter.importUrl.isBlank()) {
                                pendingAdapter = PendingAdapterInput(school, adapter)
                            } else {
                                openAdapterSource(scope, school, adapter, adapter.importUrl) { loading = it }
                            }
                        },
                    )
                }
            }
        }
        if (showJsonDialog) {
            JsonImportDialog(
                onDismiss = { showJsonDialog = false },
                onImport = { raw ->
                    showJsonDialog = false
                    runCatching {
                        CourseImportParser.parseToPreviewPayload(raw, schoolName = "JSON导入", source = "json")
                    }.onSuccess {
                        CoursePreviewActivity.start(this, it.toJsonString())
                    }.onFailure {
                        Toast.makeText(this, "JSON解析失败：${it.message.orEmpty()}", Toast.LENGTH_SHORT).show()
                    }
                },
            )
        }
        if (showAiDialog) {
            AiSettingsDialog(
                repository = repository,
                onDismiss = { showAiDialog = false },
                onOpen = { url ->
                    showAiDialog = false
                    ImportWebViewActivity.start(
                        context = this,
                        url = url,
                        title = "AI解析导入",
                        schoolName = "AI解析导入",
                        source = "ai",
                        buttonText = "开始解析导入",
                        aiMode = true,
                        desktopMode = true,
                    )
                },
            )
        }
        pendingAdapter?.let { pending ->
            AdapterUrlDialog(
                school = pending.school,
                adapter = pending.adapter,
                onDismiss = { pendingAdapter = null },
                onOpen = { url ->
                    pendingAdapter = null
                    openAdapterSource(scope, pending.school, pending.adapter, url) { loading = it }
                },
            )
        }
        pendingCommonSource?.let { item ->
            CommonUrlDialog(
                source = item,
                onDismiss = { pendingCommonSource = null },
                onOpen = { url ->
                    pendingCommonSource = null
                    openCommonSource(scope, item, url) { loading = it }
                },
            )
        }
    }

    private fun openAdapterSource(
        scope: kotlinx.coroutines.CoroutineScope,
        school: ShiguangSchool,
        adapter: ShiguangAdapter,
        url: String,
        onLoading: (Boolean) -> Unit,
    ) {
        scope.launch {
            onLoading(true)
            runCatching {
                val scriptPath = adapter.assetJsPath.ifBlank {
                    "${school.resourceFolder.trimEnd('/')}/${adapter.adapterId}.js"
                }
                val script = repository.downloadScript(repository.shiguangScriptUrl(scriptPath))
                ImportWebViewActivity.start(
                    context = this@ImportHubActivity,
                    url = url,
                    script = script,
                    title = adapter.adapterName.ifBlank { adapter.adapterId },
                    schoolName = school.name,
                    source = "shiguang:${adapter.adapterId}",
                )
            }.onFailure {
                Toast.makeText(this@ImportHubActivity, "适配器加载失败：${it.message.orEmpty()}", Toast.LENGTH_SHORT).show()
            }
            onLoading(false)
        }
    }

    private fun openCommonSource(
        scope: kotlinx.coroutines.CoroutineScope,
        item: ImportSourceItem,
        url: String,
        onLoading: (Boolean) -> Unit,
    ) {
        scope.launch {
            onLoading(true)
            runCatching {
                val script = repository.downloadScript(repository.commonScriptUrl(item.extra))
                ImportWebViewActivity.start(
                    context = this@ImportHubActivity,
                    url = url,
                    script = script,
                    title = item.name,
                    schoolName = item.name,
                    source = "common:${item.extra}",
                )
            }.onFailure {
                Toast.makeText(this@ImportHubActivity, "脚本加载失败：${it.message.orEmpty()}", Toast.LENGTH_SHORT).show()
            }
            onLoading(false)
        }
    }

    companion object {
        fun start(context: Context) {
            val intent = Intent(context, ImportHubActivity::class.java)
            if (context !is Activity) intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
        }
    }
}

private data class PendingAdapterInput(
    val school: ShiguangSchool,
    val adapter: ShiguangAdapter,
)

@Composable
private fun QuickActions(
    onAi: () -> Unit,
    onJson: () -> Unit,
    onRefresh: () -> Unit,
    loading: Boolean,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MiuixTheme.colorScheme.surfaceVariant, RoundedCornerShape(8.dp))
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(text = "导入方式", fontWeight = FontWeight.SemiBold)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TextButton(modifier = Modifier.weight(1f), text = "AI解析导入", colors = ButtonDefaults.textButtonColorsPrimary(), onClick = onAi)
            TextButton(modifier = Modifier.weight(1f), text = "JSON导入", onClick = onJson)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            TextButton(modifier = Modifier.weight(1f), text = "刷新导入源", onClick = onRefresh, enabled = !loading)
            if (loading) CircularProgressIndicator(modifier = Modifier.height(20.dp), strokeWidth = 2.dp)
        }
    }
}

@Composable
private fun SourceSection(
    title: String,
    items: List<ImportSourceItem>,
    onClick: (ImportSourceItem) -> Unit,
) {
    Column {
        Text(text = title, modifier = Modifier.padding(start = 4.dp, bottom = 8.dp), color = Color(0x99000000))
        if (items.isEmpty()) {
            EmptyCard("暂无通用导入源")
        } else {
            CardList {
                items.forEach { item ->
                    ListRow(
                        title = item.name,
                        subtitle = item.url.ifBlank { "点击后输入教务系统网址" },
                        onClick = { onClick(item) },
                    )
                }
            }
        }
    }
}

@Composable
private fun SchoolSection(
    schools: List<ShiguangSchool>,
    onClick: (ShiguangSchool) -> Unit,
) {
    Column {
        Text(text = "适配仓库", modifier = Modifier.padding(start = 4.dp, bottom = 8.dp), color = Color(0x99000000))
        if (schools.isEmpty()) {
            EmptyCard("暂无拾光适配学校")
        } else {
            CardList {
                schools.sortedWith(compareByDescending<ShiguangSchool> { it.isPinned }.thenBy { it.initial }.thenBy { it.name })
                    .forEach { school ->
                        val subtitle = buildString {
                            append("${school.adapters.size} 个适配")
                            if (school.isPinned) append(" · 通用")
                        }
                        ListRow(title = school.name, subtitle = subtitle, onClick = { onClick(school) })
                    }
            }
        }
    }
}

@Composable
private fun AdapterSection(
    school: ShiguangSchool,
    onAdapter: (ShiguangSchool, ShiguangAdapter) -> Unit,
) {
    Column {
        Text(text = "拾光适配", modifier = Modifier.padding(start = 4.dp, bottom = 8.dp), color = Color(0x99000000))
        if (school.adapters.isEmpty()) {
            EmptyCard("该学校暂无可用适配")
        } else {
            CardList {
                school.adapters.forEach { adapter ->
                    val subtitle = buildString {
                        if (adapter.maintainer.isNotBlank()) append("贡献者：${adapter.maintainer}")
                        if (adapter.importUrl.isBlank()) {
                            if (isNotBlank()) append("\n")
                            append("需手动输入教务系统网址")
                        }
                        if (adapter.description.isNotBlank()) {
                            if (isNotBlank()) append("\n")
                            append(adapter.description.replace("\\n", "\n"))
                        }
                    }
                    ListRow(title = adapter.adapterName.ifBlank { adapter.adapterId }, subtitle = subtitle, onClick = { onAdapter(school, adapter) })
                }
            }
        }
    }
}

@Composable
private fun CardList(content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MiuixTheme.colorScheme.surfaceVariant, RoundedCornerShape(8.dp)),
        content = content,
    )
}

@Composable
private fun ListRow(title: String, subtitle: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(text = title, fontWeight = FontWeight.Medium)
            if (subtitle.isNotBlank()) {
                Text(text = subtitle, modifier = Modifier.padding(top = 4.dp), color = Color(0x99000000))
            }
        }
        Text(text = "›", color = Color(0x66000000))
    }
}

@Composable
private fun EmptyCard(text: String) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MiuixTheme.colorScheme.surfaceVariant, RoundedCornerShape(8.dp))
            .padding(16.dp),
    ) {
        Text(text = text, color = Color(0x99000000))
    }
}

@Composable
private fun JsonImportDialog(
    onDismiss: () -> Unit,
    onImport: (String) -> Unit,
) {
    var raw by remember { mutableStateOf("") }
    WindowDialog(
        show = true,
        onDismissRequest = onDismiss,
        title = "JSON导入",
        content = {
            Column {
                Text(text = "请粘贴符合小爱课程表适配规范的 JSON 数据", color = Color(0x99000000))
                TextField(
                    value = raw,
                    onValueChange = { raw = it },
                    modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                    label = "课程 JSON",
                    useLabelAsPlaceholder = true,
                    minLines = 6,
                )
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 20.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    TextButton(modifier = Modifier.weight(1f), text = "取消", onClick = onDismiss)
                    TextButton(
                        modifier = Modifier.weight(1f),
                        text = "导入",
                        colors = ButtonDefaults.textButtonColorsPrimary(),
                        enabled = raw.isNotBlank(),
                        onClick = { onImport(raw) },
                    )
                }
            }
        },
    )
}

@Composable
private fun CommonUrlDialog(
    source: ImportSourceItem,
    onDismiss: () -> Unit,
    onOpen: (String) -> Unit,
) {
    var url by remember(source) { mutableStateOf("") }
    WindowDialog(
        show = true,
        onDismissRequest = onDismiss,
        title = source.name,
        content = {
            Column {
                Text(text = "请输入教务系统登录页或课程表页地址", color = Color(0x99000000))
                TextField(
                    value = url,
                    onValueChange = { url = it },
                    modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                    label = "教务系统 URL",
                    useLabelAsPlaceholder = true,
                    singleLine = true,
                )
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 20.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    TextButton(modifier = Modifier.weight(1f), text = "取消", onClick = onDismiss)
                    TextButton(
                        modifier = Modifier.weight(1f),
                        text = "打开",
                        colors = ButtonDefaults.textButtonColorsPrimary(),
                        enabled = url.startsWith("http://") || url.startsWith("https://"),
                        onClick = { onOpen(url) },
                    )
                }
            }
        },
    )
}

@Composable
private fun AdapterUrlDialog(
    school: ShiguangSchool,
    adapter: ShiguangAdapter,
    onDismiss: () -> Unit,
    onOpen: (String) -> Unit,
) {
    var url by remember(school, adapter) { mutableStateOf("") }
    val title = adapter.adapterName.ifBlank { adapter.adapterId }
    WindowDialog(
        show = true,
        onDismissRequest = onDismiss,
        title = title,
        content = {
            Column {
                Text(
                    text = "${school.name} 的该适配器未内置教务系统地址，请自行输入登录页或课程表页地址。",
                    color = Color(0x99000000),
                )
                TextField(
                    value = url,
                    onValueChange = { url = it },
                    modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                    label = "教务系统 URL",
                    useLabelAsPlaceholder = true,
                    singleLine = true,
                )
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 20.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    TextButton(modifier = Modifier.weight(1f), text = "取消", onClick = onDismiss)
                    TextButton(
                        modifier = Modifier.weight(1f),
                        text = "打开",
                        colors = ButtonDefaults.textButtonColorsPrimary(),
                        enabled = url.startsWith("http://") || url.startsWith("https://"),
                        onClick = { onOpen(url) },
                    )
                }
            }
        },
    )
}

@Composable
private fun AiSettingsDialog(
    repository: ScheduleImportRepository,
    onDismiss: () -> Unit,
    onOpen: (String) -> Unit,
) {
    var importUrl by remember { mutableStateOf(repository.aiImportUrl()) }
    var apiUrl by remember { mutableStateOf(repository.aiApiUrl()) }
    var model by remember { mutableStateOf(repository.aiModel()) }
    var apiKey by remember { mutableStateOf(repository.aiApiKey()) }
    WindowDialog(
        show = true,
        onDismissRequest = onDismiss,
        title = "AI解析导入",
        content = {
            Column {
                TextField(
                    value = importUrl,
                    onValueChange = { importUrl = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = "教务系统网址",
                    useLabelAsPlaceholder = true,
                    singleLine = true,
                )
                TextField(
                    value = apiUrl,
                    onValueChange = { apiUrl = it },
                    modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
                    label = "API URL",
                    useLabelAsPlaceholder = true,
                    singleLine = true,
                )
                TextField(
                    value = model,
                    onValueChange = { model = it },
                    modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
                    label = "模型",
                    useLabelAsPlaceholder = true,
                    singleLine = true,
                )
                TextField(
                    value = apiKey,
                    onValueChange = { apiKey = it },
                    modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
                    label = "API Key",
                    useLabelAsPlaceholder = true,
                    singleLine = true,
                )
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 20.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    TextButton(modifier = Modifier.weight(1f), text = "取消", onClick = onDismiss)
                    TextButton(
                        modifier = Modifier.weight(1f),
                        text = "打开",
                        colors = ButtonDefaults.textButtonColorsPrimary(),
                        enabled = importUrl.isNotBlank(),
                        onClick = {
                            repository.setAiSettings(importUrl, apiUrl, model, apiKey)
                            onOpen(importUrl)
                        },
                    )
                }
            }
        },
    )
}
