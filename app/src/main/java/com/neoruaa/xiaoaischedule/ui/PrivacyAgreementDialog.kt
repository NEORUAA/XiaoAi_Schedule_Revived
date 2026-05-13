package com.neoruaa.xiaoaischedule.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.unit.dp
import com.neoruaa.xiaoaischedule.R
import com.neoruaa.xiaoaischedule.core.XiaoAiConstants
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Checkbox
import top.yukonga.miuix.kmp.basic.CheckboxDefaults
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.window.WindowDialog

@Composable
fun PrivacyAgreementDialog(
    onAgree: () -> Unit,
    onDisagree: () -> Unit,
    onOpenUrl: (String) -> Unit,
) {
    var checked by remember { mutableStateOf(false) }
    WindowDialog(
        show = true,
        onDismissRequest = onDisagree,
        title = stringResource(R.string.user_privacy_protection_title),
        content = {
            Column {
                Text(text = stringResource(R.string.user_privacy_protection_msg))
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Checkbox(
                        state = ToggleableState(checked),
                        onClick = { checked = !checked },
                        colors = CheckboxDefaults.checkboxColors(),
                    )
                    Text(text = stringResource(R.string.user_privacy_protection_tips_1))
                }
                Row(modifier = Modifier.fillMaxWidth()) {
                    TextButton(
                        text = stringResource(R.string.user_agreement),
                        onClick = { onOpenUrl(XiaoAiConstants.UserAgreementUrl) },
                    )
                    TextButton(
                        text = stringResource(R.string.privacy_policy),
                        onClick = { onOpenUrl(XiaoAiConstants.PrivacyUrl) },
                    )
                }
                Spacer(modifier = Modifier.height(24.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp),
                ) {
                    TextButton(
                        modifier = Modifier.weight(1f),
                        text = stringResource(R.string.disagree),
                        onClick = onDisagree,
                    )
                    Spacer(Modifier.width(8.dp))
                    TextButton(
                        modifier = Modifier.weight(1f),
                        text = stringResource(R.string.agree),
                        onClick = onAgree,
                        enabled = checked,
                        colors = ButtonDefaults.textButtonColorsPrimary(),
                    )
                }
            }
        },
    )
}
