package com.neoruaa.xiaoaischedule.web

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.provider.MediaStore
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.FileProvider
import java.io.File

class WebFileChooserDelegate(private val activity: ComponentActivity) {
    private var pendingCallback: ValueCallback<Array<Uri>>? = null
    private var cameraOutput: Uri? = null

    private val launcher = activity.registerForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        val callback = pendingCallback ?: return@registerForActivityResult
        pendingCallback = null
        val values = if (result.resultCode == Activity.RESULT_OK) {
            WebChromeClient.FileChooserParams.parseResult(result.resultCode, result.data)
                ?: cameraOutput?.let { arrayOf(it) }
        } else {
            null
        }
        cameraOutput = null
        callback.onReceiveValue(values)
    }

    fun showFileChooser(
        filePathCallback: ValueCallback<Array<Uri>>,
        fileChooserParams: WebChromeClient.FileChooserParams,
    ): Boolean {
        pendingCallback?.onReceiveValue(null)
        pendingCallback = filePathCallback

        val contentIntent = runCatching { fileChooserParams.createIntent() }
            .getOrElse {
                Intent(Intent.ACTION_GET_CONTENT).apply {
                    addCategory(Intent.CATEGORY_OPENABLE)
                    type = "*/*"
                }
            }

        val captureIntent = createCaptureIntent()
        val chooser = Intent(Intent.ACTION_CHOOSER).apply {
            putExtra(Intent.EXTRA_INTENT, contentIntent)
            putExtra(Intent.EXTRA_TITLE, "选择文件")
            if (captureIntent != null) {
                putExtra(Intent.EXTRA_INITIAL_INTENTS, arrayOf(captureIntent))
            }
        }

        return runCatching {
            launcher.launch(chooser)
            true
        }.getOrElse {
            pendingCallback = null
            filePathCallback.onReceiveValue(null)
            false
        }
    }

    private fun createCaptureIntent(): Intent? {
        val imageFile = runCatching {
            File.createTempFile("web_capture_", ".jpg", File(activity.cacheDir, "images").apply { mkdirs() })
        }.getOrNull() ?: return null
        val uri = FileProvider.getUriForFile(activity, "${activity.packageName}.fileprovider", imageFile)
        cameraOutput = uri
        return Intent(MediaStore.ACTION_IMAGE_CAPTURE).apply {
            putExtra(MediaStore.EXTRA_OUTPUT, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
        }
    }
}
