package com.neoruaa.xiaoaischedule.importer

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.annotation.RequiresApi
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.neoruaa.xiaoaischedule.BuildConfig
import com.neoruaa.xiaoaischedule.ui.MiuixBackButton
import com.neoruaa.xiaoaischedule.ui.effect.BgEffectBackground
import com.neoruaa.xiaoaischedule.ui.theme.XiaoaischeduleTheme
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.SmallTopAppBar
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.blur.BlendColorEntry
import top.yukonga.miuix.kmp.blur.BlurBlendMode
import top.yukonga.miuix.kmp.blur.BlurColors
import top.yukonga.miuix.kmp.blur.BlurDefaults
import top.yukonga.miuix.kmp.blur.isRenderEffectSupported
import top.yukonga.miuix.kmp.blur.isRuntimeShaderSupported
import top.yukonga.miuix.kmp.blur.layerBackdrop
import top.yukonga.miuix.kmp.blur.rememberLayerBackdrop
import top.yukonga.miuix.kmp.blur.textureBlur
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Info
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
        val lazyListState = rememberLazyListState()
        val scrollProgress by remember {
            derivedStateOf {
                if (lazyListState.firstVisibleItemIndex > 0) {
                    1f
                } else {
                    (lazyListState.firstVisibleItemScrollOffset / 220f).coerceIn(0f, 1f)
                }
            }
        }
        val scrollBehavior = MiuixScrollBehavior()

        Scaffold(
            topBar = {
                SmallTopAppBar(
                    title = "关于",
                    scrollBehavior = scrollBehavior,
                    color = MiuixTheme.colorScheme.surface.copy(alpha = scrollProgress),
                    titleColor = MiuixTheme.colorScheme.onSurface.copy(alpha = scrollProgress),
                    defaultWindowInsetsPadding = false,
                    navigationIcon = { MiuixBackButton(onClick = { finish() }) },
                )
            },
            containerColor = MiuixTheme.colorScheme.surface,
        ) { padding ->
            AboutBackdrop {
                Box(modifier = Modifier.fillMaxSize()) {
                    ImportLazyColumn(
                        padding = padding,
                        modifier = Modifier.fillMaxSize(),
                        state = lazyListState,
                    ) {
                        item { AboutHeader() }
                        aboutMiuixSection("关于") {
                            MiuixNavigationRow(
                                title = "开源项目引用",
                                summary = "本项目使用或参考的开源项目与许可证",
                                onClick = { OpenSourceNoticesActivity.start(this@ModuleAboutActivity) },
                            )
                            MiuixNavigationRow(
                                title = "仓库地址",
                                summary = "https://github.com/NEORUAA/XiaoAi_Schedule_Revived",
                                onClick = { openUri("https://github.com/NEORUAA/XiaoAi_Schedule_Revived") },
                            )
                        }
                        aboutMiuixSection("调试") {
                            MiuixNavigationRow(
                                title = "JavaScript 执行",
                                summary = "在小爱课程表的环境中指定 URL 执行 JavaScript",
                                onClick = { showDebugDialog = true },
                            )
                            MiuixNavigationRow(
                                title = "自定义拾光仓库源",
                                summary = "配置仓库 URL 和脚本分支",
                                onClick = { showSourceDialog = true },
                            )
                        }
                        aboutMiuixSection("应用框架贡献者") {
                            MiuixNavigationRow("NEORUAA", "绫猫", onClick = { openUri("https://github.com/NEORUAA") })
                        }
                        aboutMiuixSection("教务导入贡献者") {
                            MiuixNavigationRow("帕帝天秀", "小爱课程表的忠实粉丝", onClick = { openQq("3373587110") })
                            MiuixNavigationRow("Mercury", "AI导入课表部分全部代码实现；超级小爱适配", onClick = { openQq("3038899204") })
                            MiuixNavigationRow("颜致恒plus", "教务导入思路提供者", onClick = { openQq("2488971290") })
                            MiuixNavigationRow("川意", "项目贡献者", onClick = { openQq("3299699002") })
                            MiuixNavigationRow("Aven Cole", "项目贡献者", onClick = { openQq("1587005702") })
                        }
                    }
                }
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

@Composable
private fun AboutHeader() {
    val isDark = isSystemInDarkTheme()
    val blurEnable = remember { isRenderEffectSupported() }
    val backdrop = LocalAboutBackdrop.current
    val logoBlend = remember(isDark) {
        if (isDark) {
            listOf(
                BlendColorEntry(Color(0xE6A1A1A1), BlurBlendMode.ColorDodge),
                BlendColorEntry(Color(0x4DE6E6E6), BlurBlendMode.LinearLight),
                BlendColorEntry(Color(0xFF1AF500), BlurBlendMode.Lab),
            )
        } else {
            listOf(
                BlendColorEntry(Color(0xCC4A4A4A), BlurBlendMode.ColorBurn),
                BlendColorEntry(Color(0xFF4F4F4F), BlurBlendMode.LinearLight),
                BlendColorEntry(Color(0xFF1AF200), BlurBlendMode.Lab),
            )
        }
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 48.dp, bottom = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.size(88.dp),
        ) {
            Icon(
                imageVector = MiuixIcons.Regular.Info,
                contentDescription = null,
                modifier = Modifier
                    .size(92.dp)
                    .graphicsLayer { alpha = 0.94f }
                    .then(
                        if (backdrop != null) {
                            Modifier.textureBlur(
                                backdrop = backdrop,
                                shape = androidx.compose.foundation.shape.RoundedCornerShape(18.dp),
                                blurRadius = 200f,
                                noiseCoefficient = BlurDefaults.NoiseCoefficient,
                                colors = BlurColors(blendColors = logoBlend),
                                contentBlendMode = BlendMode.DstIn,
                                enabled = blurEnable,
                            )
                        } else {
                            Modifier
                        },
                    ),
                tint = MiuixTheme.colorScheme.onSurface,
            )
        }
        Text(
            text = "小爱课程表 Revived",
            modifier = Modifier.then(
                if (backdrop != null) {
                    Modifier.textureBlur(
                        backdrop = backdrop,
                        shape = androidx.compose.foundation.shape.RoundedCornerShape(0.dp),
                        blurRadius = 150f,
                        noiseCoefficient = BlurDefaults.NoiseCoefficient,
                        colors = BlurColors(blendColors = logoBlend),
                        contentBlendMode = BlendMode.DstIn,
                        enabled = blurEnable,
                    )
                } else {
                    Modifier
                },
            ),
            fontWeight = FontWeight.Bold,
            fontSize = 30.sp,
        )
        MiuixSecondaryText(
            text = "Version ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})",
        )
    }
}

private val LocalAboutBackdrop = androidx.compose.runtime.staticCompositionLocalOf<top.yukonga.miuix.kmp.blur.LayerBackdrop?> { null }

private fun LazyListScope.aboutMiuixSection(
    title: String,
    content: @Composable ColumnScope.() -> Unit,
) {
    item { SmallTitle(title) }
    item {
        AboutBlurCard(
            modifier = Modifier
                .padding(horizontal = 12.dp)
                .padding(bottom = 12.dp),
            content = content,
        )
    }
}

@Composable
private fun AboutBlurCard(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    val isDark = isSystemInDarkTheme()
    val backdrop = LocalAboutBackdrop.current
    val blurEnable = remember { isRenderEffectSupported() && isRuntimeShaderSupported() }
    val blendColors = remember(isDark) {
        if (isDark) {
            listOf(
                BlendColorEntry(Color(0x757A7A7A), BlurBlendMode.Luminosity),
            )
        } else {
            listOf(
                BlendColorEntry(Color(0x340034F9), BlurBlendMode.Overlay),
                BlendColorEntry(Color(0xB3FFFFFF), BlurBlendMode.HardLight),
            )
        }
    }
    val cardModifier = if (backdrop != null) {
        modifier.textureBlur(
            backdrop = backdrop,
            shape = androidx.compose.foundation.shape.RoundedCornerShape(16.dp),
            blurRadius = 60f,
            noiseCoefficient = BlurDefaults.NoiseCoefficient,
            colors = BlurColors(
                blendColors = blendColors,
                brightness = 0f,
                contrast = 1f,
                saturation = 1f,
            ),
            enabled = blurEnable,
        )
    } else {
        modifier
    }

    Card(
        modifier = cardModifier,
        colors = CardDefaults.defaultColors(
            color = if (blurEnable && backdrop != null) Color.Transparent else MiuixTheme.colorScheme.surfaceContainer,
            contentColor = MiuixTheme.colorScheme.onSurface,
        ),
        content = content,
    )
}

@Composable
private fun AboutBackdrop(content: @Composable () -> Unit) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        AboutShaderBackdrop(content)
    } else {
        Box(modifier = Modifier.fillMaxSize()) {
            content()
        }
    }
}

@RequiresApi(Build.VERSION_CODES.TIRAMISU)
@Composable
private fun AboutShaderBackdrop(content: @Composable () -> Unit) {
    val backdrop = rememberLayerBackdrop()
    androidx.compose.runtime.CompositionLocalProvider(LocalAboutBackdrop provides backdrop) {
        BgEffectBackground(
            dynamicBackground = true,
            isOs3Effect = true,
            modifier = Modifier.fillMaxSize(),
            bgModifier = Modifier.layerBackdrop(backdrop),
            effectBackground = true,
        ) {
            content()
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
