package com.neoruaa.xiaoaischedule.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.neoruaa.xiaoaischedule.R
import com.neoruaa.xiaoaischedule.core.XiaoAiConstants

@Composable
fun PrivacyAgreementDialog(
    onAgree: () -> Unit,
    onDisagree: () -> Unit,
    onOpenUrl: (String) -> Unit,
) {
    var checked by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDisagree,
        title = { Text(text = stringResource(R.string.user_privacy_protection_title)) },
        text = {
            Column {
                Text(text = stringResource(R.string.user_privacy_protection_msg))
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Checkbox(checked = checked, onCheckedChange = { checked = it })
                    Text(text = stringResource(R.string.user_privacy_protection_tips_1))
                }
                Row(modifier = Modifier.fillMaxWidth()) {
                    TextButton(onClick = { onOpenUrl(XiaoAiConstants.UserAgreementUrl) }) {
                        Text(text = stringResource(R.string.user_agreement))
                    }
                    TextButton(onClick = { onOpenUrl(XiaoAiConstants.PrivacyUrl) }) {
                        Text(text = stringResource(R.string.privacy_policy))
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = onAgree, enabled = checked) {
                Text(text = stringResource(R.string.agree))
            }
        },
        dismissButton = {
            TextButton(onClick = onDisagree) {
                Text(text = stringResource(R.string.disagree))
            }
        },
    )
}
