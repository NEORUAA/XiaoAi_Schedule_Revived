package com.neoruaa.xiaoaischedule.importer

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
internal fun ImportLazyColumn(
    padding: PaddingValues,
    modifier: Modifier = Modifier,
    state: LazyListState = rememberLazyListState(),
    content: LazyListScope.() -> Unit,
) {
    LazyColumn(
        modifier = modifier,
        state = state,
        contentPadding = PaddingValues(
            start = 0.dp,
            top = padding.calculateTopPadding() + 12.dp,
            end = 0.dp,
            bottom = padding.calculateBottomPadding() + 12.dp,
        ),
        content = {
            content()
            item { Spacer(modifier = Modifier.navigationBarsPadding()) }
        },
    )
}

internal fun LazyListScope.miuixSection(
    title: String,
    content: @Composable ColumnScope.() -> Unit,
) {
    item { SmallTitle(title) }
    item {
        Card(
            modifier = Modifier
                .padding(horizontal = 12.dp)
                .padding(bottom = 12.dp),
            colors = CardDefaults.defaultColors(
                color = MiuixTheme.colorScheme.surfaceContainer,
                contentColor = MiuixTheme.colorScheme.onSurface,
            ),
            content = content,
        )
    }
}

@Composable
internal fun MiuixInfoCard(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.defaultColors(
            color = MiuixTheme.colorScheme.surfaceContainer,
            contentColor = MiuixTheme.colorScheme.onSurface,
        ),
        content = content,
    )
}

@Composable
internal fun MiuixNavigationRow(
    title: String,
    summary: String,
    onClick: () -> Unit,
) {
    ArrowPreference(
        title = title,
        summary = summary,
        onClick = onClick,
    )
}

@Composable
internal fun MiuixPlainRow(
    title: String,
    summary: String? = null,
    onClick: (() -> Unit)? = null,
) {
    BasicComponent(
        title = title,
        summary = summary,
        onClick = onClick,
    )
}

@Composable
internal fun MiuixSecondaryText(
    text: String,
    modifier: Modifier = Modifier,
) {
    Text(
        text = text,
        modifier = modifier,
        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
        style = MiuixTheme.textStyles.body2,
    )
}
