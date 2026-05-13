package com.neoruaa.xiaoaischedule.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.neoruaa.xiaoaischedule.R
import com.neoruaa.xiaoaischedule.account.AccountRepository
import com.neoruaa.xiaoaischedule.web.BridgeLoginRequest
import kotlinx.coroutines.launch

@Composable
fun LoginDialog(
    request: BridgeLoginRequest,
    accountRepository: AccountRepository,
    onDismiss: () -> Unit,
    onResult: (Boolean) -> Unit,
) {
    val saved = remember(request) { accountRepository.savedPassword() }
    var account by remember(request) { mutableStateOf(saved?.account.orEmpty()) }
    var password by remember(request) { mutableStateOf(saved?.password.orEmpty()) }
    var savePassword by remember(request) { mutableStateOf(saved != null) }
    var loading by remember(request) { mutableStateOf(false) }
    var error by remember(request) { mutableStateOf<String?>(null) }
    var twoFactorOptions by remember(request) { mutableStateOf<List<Int>>(emptyList()) }
    var selectedFlag by remember(request) { mutableStateOf<Int?>(null) }
    var ticket by remember(request) { mutableStateOf("") }
    var sendingFlag by remember(request) { mutableIntStateOf(0) }
    val scope = rememberCoroutineScope()

    fun submitLogin() {
        scope.launch {
            loading = true
            error = null
            when (val result = accountRepository.login(account, password, savePassword)) {
                is AccountRepository.LoginResult.Success -> onResult(true)
                is AccountRepository.LoginResult.NeedTwoFactor -> twoFactorOptions = result.options
                is AccountRepository.LoginResult.Error -> error = result.message
            }
            loading = false
        }
    }

    fun sendTicket(flag: Int) {
        scope.launch {
            sendingFlag = flag
            error = null
            val sent = accountRepository.sendTicket(flag)
            if (sent) {
                selectedFlag = flag
            } else {
                error = "验证码发送失败"
            }
            sendingFlag = 0
        }
    }

    fun submitTicket() {
        val flag = selectedFlag ?: return
        scope.launch {
            loading = true
            error = null
            when (val result = accountRepository.submitTwoFactorTicket(account, password, savePassword, flag, ticket)) {
                is AccountRepository.LoginResult.Success -> onResult(true)
                is AccountRepository.LoginResult.NeedTwoFactor -> twoFactorOptions = result.options
                is AccountRepository.LoginResult.Error -> error = result.message
            }
            loading = false
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = stringResource(R.string.login)) },
        text = {
            Column {
                OutlinedTextField(
                    value = account,
                    onValueChange = { account = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(stringResource(R.string.account)) },
                    singleLine = true,
                    enabled = !loading && twoFactorOptions.isEmpty(),
                )
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                    label = { Text(stringResource(R.string.password)) },
                    singleLine = true,
                    enabled = !loading && twoFactorOptions.isEmpty(),
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                )

                if (twoFactorOptions.isEmpty()) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Checkbox(
                            checked = savePassword,
                            onCheckedChange = { savePassword = it },
                            enabled = !loading,
                        )
                        Text(text = stringResource(R.string.save_password))
                    }
                }

                if (twoFactorOptions.isNotEmpty() && selectedFlag == null) {
                    Spacer(Modifier.height(14.dp))
                    if (twoFactorOptions.contains(4)) {
                        Button(
                            onClick = { sendTicket(4) },
                            enabled = sendingFlag == 0,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(if (sendingFlag == 4) stringResource(R.string.verifying) else stringResource(R.string.send_phone_code))
                        }
                    }
                    if (twoFactorOptions.contains(8)) {
                        Button(
                            onClick = { sendTicket(8) },
                            enabled = sendingFlag == 0,
                            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                        ) {
                            Text(if (sendingFlag == 8) stringResource(R.string.verifying) else stringResource(R.string.send_email_code))
                        }
                    }
                }

                if (selectedFlag != null) {
                    OutlinedTextField(
                        value = ticket,
                        onValueChange = { ticket = it },
                        modifier = Modifier.fillMaxWidth().padding(top = 14.dp),
                        label = { Text(stringResource(R.string.verification_code)) },
                        singleLine = true,
                        enabled = !loading,
                    )
                }

                error?.let {
                    Text(
                        text = it,
                        modifier = Modifier.padding(top = 12.dp),
                        color = androidx.compose.ui.graphics.Color(0xFFD32F2F),
                    )
                }
            }
        },
        confirmButton = {
            Button(
                enabled = !loading && account.isNotBlank() && password.isNotBlank() && (selectedFlag == null || ticket.isNotBlank()),
                onClick = {
                    if (selectedFlag == null) submitLogin() else submitTicket()
                },
            ) {
                Text(
                    text = when {
                        loading -> stringResource(R.string.logging_in)
                        selectedFlag != null -> stringResource(R.string.submit)
                        else -> stringResource(R.string.login)
                    },
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !loading) {
                Text(text = stringResource(R.string.cancel))
            }
            if (selectedFlag != null) {
                Spacer(Modifier.width(6.dp))
            }
        },
    )
}
