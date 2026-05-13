package com.neoruaa.xiaoaischedule.core

object XiaoAiConstants {
    const val Host = "https://i.ai.mi.com"
    const val AppId = "2882303761518539170"
    const val AppSecret = "7NrhKDvg8SLDGll9sJWtRQ=="
    const val CompatibilityPackageName = "com.xiaomi.aischedule"

    const val TodayLessonUrl = "$Host/h5/precache/ai-schedule/#/today_lesson"
    const val ScheduleUrl = "$Host/h5/precache/ai-schedule/#/home"
    const val MineUrl = "$Host/h5/precache/ai-schedule/#/set_schedule"
    const val PrivacyUrl = "$Host/h5/precache/ai-schedule/#/privacy"
    const val UserAgreementUrl = "$Host/h5/precache/ai-schedule/#/user-agreement"
    const val DeleteAccountUrl = "https://account.xiaomi.com/pass/del"

    const val CourseInfoUrl = "$Host/course/courseInfoDay"
    const val CourseSettingUrl = "$Host/course/setting"
    const val LogoutScheduleUrl = "$Host/course-multi-auth/logout"

    const val XiaomiAccountUrl = "https://account.xiaomi.com"
    const val OAuthSid = "oauth2.0"
    const val OAuthRedirectUri = "https://xiaoai.mi.com/"
}
