package com.neoruaa.xiaoaischedule.web

import android.content.Context
import android.content.Intent
import android.net.Uri
import com.neoruaa.xiaoaischedule.delete.DeleteServiceActivity
import com.neoruaa.xiaoaischedule.importer.ImportHubActivity
import com.neoruaa.xiaoaischedule.importer.ModuleAboutActivity

class NativeRouteHandler {
    fun handleExternalScheme(uri: Uri?, context: Context): Boolean {
        if (uri == null) return false
        val scheme = uri.scheme.orEmpty()
        if (scheme != SchemeAiSchedule && scheme != SchemeLite) return false

        if (isOpenWeb(uri)) {
            val url = uri.getQueryParameter("url").orEmpty()
            if (isXiaomiDomain(url)) {
                WebContainerActivity.start(context, url)
                return true
            }
        }
        return handleLocalScheme(uri, context)
    }

    fun handleLocalScheme(uri: Uri?, context: Context): Boolean {
        if (uri == null) return false
        val scheme = uri.scheme.orEmpty()
        if (scheme != SchemeAiSchedule && scheme != SchemeLite) return false

        if (uri.host == "main") return true

        if (matches(uri, "/schedule/openURL", "schedule", "/openURL")) {
            val params = uri.getQueryParameter("params").orEmpty()
            if (params.isNotBlank()) ScheduleEducationalImportActivity.start(context, params)
            return params.isNotBlank()
        }

        if (matches(uri, "/schedule/privacy_revoke", "schedule", "/privacy_revoke")) {
            val url = uri.getQueryParameter("url").orEmpty()
            if (url.isNotBlank()) PrivacyRevokeActivity.start(context, url)
            return url.isNotBlank()
        }

        if (matches(uri, "/schedule/delete_service", "schedule", "/delete_service")) {
            context.startActivity(Intent(context, DeleteServiceActivity::class.java))
            return true
        }

        if (matches(uri, "/schedule/import_repair", "schedule", "/import_repair")) {
            ImportHubActivity.start(context)
            return true
        }

        if (matches(uri, "/schedule/module_about", "schedule", "/module_about")) {
            ModuleAboutActivity.start(context)
            return true
        }

        if (isOpenWeb(uri)) {
            val url = uri.getQueryParameter("url").orEmpty()
            if (isXiaomiDomain(url)) {
                WebContainerActivity.start(context, url)
                return true
            }
        }

        return false
    }

    private fun isOpenWeb(uri: Uri): Boolean {
        return uri.path == "/openWeb" || uri.host == "openWeb"
    }

    private fun matches(uri: Uri, fullPath: String, host: String, hostPath: String): Boolean {
        return uri.path == fullPath || (uri.host == host && uri.path == hostPath)
    }

    private fun isXiaomiDomain(url: String): Boolean {
        val host = runCatching { Uri.parse(url).host.orEmpty().lowercase() }.getOrDefault("")
        return host == "mi.com" ||
            host.endsWith(".mi.com") ||
            host == "xiaomi.com" ||
            host.endsWith(".xiaomi.com")
    }

    private companion object {
        const val SchemeAiSchedule = "aischedule"
        const val SchemeLite = "xiaoailite"
    }
}
