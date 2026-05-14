package com.neoruaa.xiaoaischedule.importer

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.neoruaa.xiaoaischedule.MainActivity
import com.neoruaa.xiaoaischedule.account.AccountRepository
import com.neoruaa.xiaoaischedule.data.PrivacyStore
import com.neoruaa.xiaoaischedule.ui.MiuixPageScaffold
import com.neoruaa.xiaoaischedule.ui.theme.XiaoaischeduleTheme
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.basic.CircularProgressIndicator
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.window.WindowDialog

class CoursePreviewActivity : ComponentActivity() {
    private lateinit var privacyStore: PrivacyStore
    private lateinit var accountRepository: AccountRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        privacyStore = PrivacyStore(this)
        accountRepository = AccountRepository(this, privacyStore)
        val payload = runCatching {
            ImportPreviewPayload.fromJsonString(intent.getStringExtra(ExtraPayload).orEmpty())
        }.getOrElse {
            ImportPreviewPayload(courses = emptyList())
        }
        setContent {
            XiaoaischeduleTheme {
                PreviewContent(payload)
            }
        }
    }

    @Composable
    private fun PreviewContent(initialPayload: ImportPreviewPayload) {
        val courses = remember { mutableStateListOf<ImportCourseDraft>().also { it.addAll(initialPayload.courses) } }
        val scope = rememberCoroutineScope()
        var editingIndex by remember { mutableStateOf<Int?>(null) }
        var loading by remember { mutableStateOf(false) }
        var error by remember { mutableStateOf("") }
        val validCount = courses.count { it.isValid }
        val invalidCount = courses.size - validCount
        val conflictCount = remember(courses.toList()) { countConflicts(courses) }

        MiuixPageScaffold(
            title = "课程预览",
            onBack = { finish() },
            bottomBar = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 18.dp, vertical = 12.dp),
                ) {
                    if (error.isNotBlank()) {
                        Text(text = error, color = MiuixTheme.colorScheme.error, modifier = Modifier.padding(bottom = 8.dp))
                    }
                    Button(
                        enabled = !loading && validCount > 0,
                        onClick = {
                            scope.launch {
                                loading = true
                                error = ""
                                val payload = initialPayload.copy(courses = courses.toList())
                                val result = ScheduleImportCommitter(
                                    context = this@CoursePreviewActivity,
                                    accountRepository = accountRepository,
                                    privacyStore = privacyStore,
                                ).commit(payload)
                                loading = false
                                result.onSuccess {
                                    startActivity(
                                        Intent(this@CoursePreviewActivity, MainActivity::class.java)
                                            .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP),
                                    )
                                    finish()
                                }.onFailure {
                                    error = it.message.orEmpty().ifBlank { "导入失败" }
                                }
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        if (loading) {
                            CircularProgressIndicator(modifier = Modifier.height(18.dp), strokeWidth = 2.dp)
                        } else {
                            Text(text = "确认导入 $validCount 门课程", color = MiuixTheme.colorScheme.onPrimary)
                        }
                    }
                }
            },
        ) { padding ->
            ImportLazyColumn(
                padding = padding,
                modifier = Modifier.fillMaxSize(),
            ) {
                item {
                    SummaryCard(
                        total = courses.size,
                        valid = validCount,
                        invalid = invalidCount,
                        conflicts = conflictCount,
                    )
                }
                item { SmallTitle("课程") }
                courses.forEachIndexed { index, course ->
                    item {
                        CourseCard(
                            course = course,
                            onClick = { editingIndex = index },
                        )
                    }
                }
            }
        }
        editingIndex?.let { index ->
            EditCourseDialog(
                course = courses[index],
                onDismiss = { editingIndex = null },
                onSave = {
                    courses[index] = it
                    editingIndex = null
                },
            )
        }
    }

    companion object {
        private const val ExtraPayload = "extra_payload"

        fun start(context: Context, payload: String) {
            val intent = Intent(context, CoursePreviewActivity::class.java).putExtra(ExtraPayload, payload)
            if (context !is Activity) intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
        }
    }
}

@Composable
private fun SummaryCard(total: Int, valid: Int, invalid: Int, conflicts: Int) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp)
            .padding(bottom = 12.dp),
        colors = CardDefaults.defaultColors(
            color = MiuixTheme.colorScheme.surfaceContainer,
            contentColor = MiuixTheme.colorScheme.onSurface,
        ),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(text = "共解析 $total 门课程", style = MiuixTheme.textStyles.title4, fontWeight = FontWeight.SemiBold)
            MiuixSecondaryText(
                text = "可导入 $valid 门，无效 $invalid 门，冲突 $conflicts 处",
                modifier = Modifier.padding(top = 6.dp),
            )
        }
    }
}

@Composable
private fun CourseCard(course: ImportCourseDraft, onClick: () -> Unit) {
    val cardColor = if (course.isValid) MiuixTheme.colorScheme.surfaceContainer else MiuixTheme.colorScheme.errorContainer
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp)
            .padding(bottom = 10.dp)
            .clickable(onClick = onClick),
        colors = CardDefaults.defaultColors(
            color = cardColor,
            contentColor = MiuixTheme.colorScheme.onSurface,
        ),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(text = course.name.ifBlank { "未命名课程" }, style = MiuixTheme.textStyles.title4, fontWeight = FontWeight.SemiBold)
            MiuixSecondaryText(
                text = "周${course.day} · 第${course.sections.joinToString(",")}节 · 第${course.weeks.joinToString(",")}周",
                modifier = Modifier.padding(top = 6.dp),
            )
            if (course.teacher.isNotBlank() || course.position.isNotBlank()) {
                MiuixSecondaryText(
                    text = listOf(course.teacher, course.position).filter { it.isNotBlank() }.joinToString(" · "),
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
            if (!course.isValid) {
                Text(text = course.invalidReason, modifier = Modifier.padding(top = 6.dp), color = MiuixTheme.colorScheme.error)
            } else if (course.repaired) {
                Text(text = "已自动修复部分字段", modifier = Modifier.padding(top = 6.dp), color = MiuixTheme.colorScheme.primary)
            }
        }
    }
}

@Composable
private fun EditCourseDialog(
    course: ImportCourseDraft,
    onDismiss: () -> Unit,
    onSave: (ImportCourseDraft) -> Unit,
) {
    var name by remember(course) { mutableStateOf(course.name) }
    var teacher by remember(course) { mutableStateOf(course.teacher) }
    var position by remember(course) { mutableStateOf(course.position) }
    var day by remember(course) { mutableStateOf(course.day.toString()) }
    var sections by remember(course) { mutableStateOf(course.sections.joinToString(",")) }
    var weeks by remember(course) { mutableStateOf(course.weeks.joinToString(",")) }
    WindowDialog(
        show = true,
        onDismissRequest = onDismiss,
        title = "编辑课程",
        content = {
            Column {
                TextField(value = name, onValueChange = { name = it }, modifier = Modifier.fillMaxWidth(), label = "课程名", useLabelAsPlaceholder = true, singleLine = true)
                TextField(value = teacher, onValueChange = { teacher = it }, modifier = Modifier.fillMaxWidth().padding(top = 10.dp), label = "教师", useLabelAsPlaceholder = true, singleLine = true)
                TextField(value = position, onValueChange = { position = it }, modifier = Modifier.fillMaxWidth().padding(top = 10.dp), label = "地点", useLabelAsPlaceholder = true, singleLine = true)
                TextField(value = day, onValueChange = { day = it }, modifier = Modifier.fillMaxWidth().padding(top = 10.dp), label = "星期 1-7", useLabelAsPlaceholder = true, singleLine = true)
                TextField(value = sections, onValueChange = { sections = it }, modifier = Modifier.fillMaxWidth().padding(top = 10.dp), label = "节次", useLabelAsPlaceholder = true, singleLine = true)
                TextField(value = weeks, onValueChange = { weeks = it }, modifier = Modifier.fillMaxWidth().padding(top = 10.dp), label = "周次", useLabelAsPlaceholder = true, singleLine = true)
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 20.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    TextButton(modifier = Modifier.weight(1f), text = "取消", onClick = onDismiss)
                    TextButton(
                        modifier = Modifier.weight(1f),
                        text = "保存",
                        colors = ButtonDefaults.textButtonColorsPrimary(),
                        onClick = {
                            val edited = CourseImportParser.parseCourse(
                                org.json.JSONObject()
                                    .put("name", name)
                                    .put("teacher", teacher)
                                    .put("position", position)
                                    .put("day", day.toIntOrNull() ?: 0)
                                    .put("sections", sections)
                                    .put("weeks", weeks),
                            )
                            onSave(edited)
                        },
                    )
                }
            }
        },
    )
}

private fun countConflicts(courses: List<ImportCourseDraft>): Int {
    var count = 0
    val valid = courses.filter { it.isValid }
    valid.forEachIndexed { index, left ->
        valid.drop(index + 1).forEach { right ->
            if (
                left.day == right.day &&
                left.sections.intersect(right.sections.toSet()).isNotEmpty() &&
                left.weeks.intersect(right.weeks.toSet()).isNotEmpty()
            ) {
                count += 1
            }
        }
    }
    return count
}
