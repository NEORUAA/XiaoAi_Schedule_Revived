package com.neoruaa.xiaoaischedule.delete

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.neoruaa.xiaoaischedule.R
import com.neoruaa.xiaoaischedule.account.AccountRepository
import com.neoruaa.xiaoaischedule.data.PrivacyStore
import com.neoruaa.xiaoaischedule.ui.SimpleTopBar
import com.neoruaa.xiaoaischedule.ui.theme.XiaoaischeduleTheme
import kotlinx.coroutines.launch

class DeleteServiceActivity : ComponentActivity() {
    private lateinit var privacyStore: PrivacyStore
    private lateinit var accountRepository: AccountRepository
    private var step by mutableIntStateOf(0)
    private var checked by mutableStateOf(false)
    private var loading by mutableStateOf(false)
    private var error by mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        privacyStore = PrivacyStore(this)
        accountRepository = AccountRepository(this, privacyStore)
        setContent {
            XiaoaischeduleTheme {
                DeleteServiceContent()
            }
        }
    }

    @Composable
    private fun DeleteServiceContent() {
        val scope = rememberCoroutineScope()
        Column(Modifier.fillMaxSize().background(Color.White)) {
            SimpleTopBar(title = stringResource(R.string.delete_service), onBack = { finish() })
            when (step) {
                0 -> DataIllustration(onNext = { step = 1 })
                1 -> Declaration(
                    checked = checked,
                    loading = loading,
                    error = error,
                    onCheckedChange = { checked = it },
                    onCancel = { finish() },
                    onConfirm = {
                        scope.launch {
                            loading = true
                            error = null
                            val success = accountRepository.deleteScheduleService()
                            loading = false
                            if (success) {
                                accountRepository.clearLocalAccountData()
                                step = 2
                            } else {
                                error = "注销请求失败，请确认已登录并稍后重试"
                            }
                        }
                    },
                )
                else -> Result(onDone = { finish() })
            }
        }
    }
}

@Composable
private fun DataIllustration(onNext: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
    ) {
        Text(
            text = stringResource(R.string.delete_service_data_title),
            color = Color.Black,
            fontSize = 22.sp,
            fontWeight = FontWeight.SemiBold,
        )
        Text(
            text = stringResource(R.string.delete_service_data_content),
            modifier = Modifier.padding(top = 18.dp),
            color = Color(0xCC000000),
            lineHeight = 22.sp,
        )
        Text(
            text = stringResource(R.string.delete_service_data_tip),
            modifier = Modifier.padding(top = 18.dp),
            color = Color(0x99000000),
            lineHeight = 20.sp,
        )
        Spacer(modifier = Modifier.height(28.dp))
        Button(onClick = onNext, modifier = Modifier.fillMaxWidth()) {
            Text(text = stringResource(R.string.delete_service_data_button_text))
        }
    }
}

@Composable
private fun Declaration(
    checked: Boolean,
    loading: Boolean,
    error: String?,
    onCheckedChange: (Boolean) -> Unit,
    onCancel: () -> Unit,
    onConfirm: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
    ) {
        Column(
            modifier = Modifier.weight(1f).verticalScroll(rememberScrollState()),
        ) {
            Text(
                text = stringResource(R.string.delete_service_declaration_title),
                color = Color.Black,
                fontSize = 22.sp,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = stringResource(R.string.delete_service_declaration_content_tip),
                modifier = Modifier.padding(top = 18.dp),
                color = Color(0x99000000),
            )
            Text(
                text = stringResource(R.string.delete_service_declaration_content_title),
                modifier = Modifier.padding(top = 18.dp),
                color = Color.Black,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = stringResource(R.string.delete_service_declaration_content_text),
                modifier = Modifier.padding(top = 10.dp),
                color = Color(0xCC000000),
                lineHeight = 21.sp,
            )
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 18.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Checkbox(checked = checked, onCheckedChange = onCheckedChange, enabled = !loading)
                Text(text = stringResource(R.string.delete_service_declaration_checkbox))
            }
            error?.let {
                Text(text = it, color = Color(0xFFD32F2F), modifier = Modifier.padding(top = 8.dp))
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            TextButton(onClick = onCancel, enabled = !loading, modifier = Modifier.weight(1f)) {
                Text(text = stringResource(R.string.delete_service_declaration_cancel))
            }
            Button(
                onClick = onConfirm,
                enabled = checked && !loading,
                modifier = Modifier.weight(1f),
            ) {
                if (loading) {
                    CircularProgressIndicator(modifier = Modifier.height(18.dp), strokeWidth = 2.dp)
                } else {
                    Text(text = stringResource(R.string.delete_service_declaration_sure))
                }
            }
        }
    }
}

@Composable
private fun Result(onDone: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = stringResource(R.string.delete_service_result_success),
            color = Color.Black,
            fontSize = 22.sp,
            fontWeight = FontWeight.SemiBold,
        )
        Text(
            text = stringResource(R.string.delete_service_result_success_content),
            modifier = Modifier.padding(top = 14.dp),
            color = Color(0x99000000),
            lineHeight = 21.sp,
        )
        Button(onClick = onDone, modifier = Modifier.fillMaxWidth().padding(top = 28.dp)) {
            Text(text = stringResource(R.string.ok))
        }
    }
}
