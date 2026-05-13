package com.neoruaa.xiaoaischedule.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun SimpleTopBar(
    title: String,
    onBack: () -> Unit,
) {
    Box(modifier = Modifier.fillMaxWidth().height(54.dp).background(Color.White)) {
        Row(modifier = Modifier.fillMaxWidth().height(54.dp), verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onBack, modifier = Modifier.padding(start = 2.dp)) {
                Text(text = "返回", color = Color(0xFF0099FF))
            }
        }
        Text(
            text = title,
            modifier = Modifier.align(Alignment.Center),
            color = Color.Black,
            fontWeight = FontWeight.SemiBold,
            fontSize = 18.sp,
        )
        HorizontalDivider(modifier = Modifier.align(Alignment.BottomCenter), color = Color(0x1A000000), thickness = 0.5.dp)
    }
}
