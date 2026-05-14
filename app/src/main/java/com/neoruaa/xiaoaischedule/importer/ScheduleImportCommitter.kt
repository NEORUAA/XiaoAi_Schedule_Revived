package com.neoruaa.xiaoaischedule.importer

import android.content.Context
import com.neoruaa.xiaoaischedule.account.AccountRepository
import com.neoruaa.xiaoaischedule.core.AppEvents
import com.neoruaa.xiaoaischedule.data.PrivacyStore
import com.neoruaa.xiaoaischedule.widget.CourseWidgetProvider

class ScheduleImportCommitter(
    private val context: Context,
    private val accountRepository: AccountRepository,
    private val privacyStore: PrivacyStore,
) {
    suspend fun commit(payload: ImportPreviewPayload): Result<Unit> {
        if (accountRepository.currentSession()?.isLoggedIn != true) {
            return Result.failure(IllegalStateException("请先登录小米账号"))
        }
        val validCourses = payload.courses.filter { it.isValid }
        if (validCourses.isEmpty()) {
            return Result.failure(IllegalArgumentException("没有可导入的有效课程"))
        }
        privacyStore.putStorage("presetData", payload.copy(courses = validCourses).toPresetData())
        CourseWidgetProvider.requestRefresh(context)
        AppEvents.importFinished.tryEmit(Unit)
        return Result.success(Unit)
    }
}
