package com.neoruaa.xiaoaischedule.ui

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Back
import top.yukonga.miuix.kmp.theme.LocalContentColor
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
fun MiuixBackButton(
    modifier: Modifier = Modifier,
    icon: ImageVector = MiuixIcons.Regular.Back,
    onClick: () -> Unit,
) {
    IconButton(onClick = onClick, modifier = modifier) {
        Icon(
            imageVector = icon,
            contentDescription = "返回",
            tint = LocalContentColor.current,
        )
    }
}

@Composable
fun SimpleTopBar(
    title: String,
    onBack: () -> Unit,
) {
    TopAppBar(
        color = MiuixTheme.colorScheme.surface,
        title = title,
        navigationIcon = {
            MiuixBackButton(onClick = onBack)
        },
    )
}

@Composable
fun MiuixPageScaffold(
    title: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    bottomBar: @Composable () -> Unit = {},
    content: @Composable (PaddingValues) -> Unit,
) {
    Scaffold(
        modifier = modifier,
        topBar = {
            SimpleTopBar(title = title, onBack = onBack)
        },
        bottomBar = bottomBar,
        containerColor = MiuixTheme.colorScheme.surface,
        content = content,
    )
}
