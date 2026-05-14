package com.neoruaa.xiaoaischedule.importer

import android.content.Context
import android.webkit.JavascriptInterface
import android.widget.Toast

class ScheduleRepairAndroidBridge(private val context: Context) {
    @JavascriptInterface
    fun navSchoolScreen() {
        ImportHubActivity.start(context)
    }

    @JavascriptInterface
    fun navModuleScreen() {
        ModuleAboutActivity.start(context)
    }

    @JavascriptInterface
    fun showToast(message: String?) {
        Toast.makeText(context, message.orEmpty(), Toast.LENGTH_SHORT).show()
    }
}
