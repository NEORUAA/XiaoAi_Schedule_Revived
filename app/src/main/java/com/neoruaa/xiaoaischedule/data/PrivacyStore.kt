package com.neoruaa.xiaoaischedule.data

import android.content.Context
import android.provider.Settings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.security.MessageDigest
import java.util.UUID

class PrivacyStore(context: Context) {
    private val appContext = context.applicationContext
    private val prefs = appContext.getSharedPreferences("xiaoai_schedule", Context.MODE_PRIVATE)
    private val securePasswordStore = SecurePasswordStore()

    private val _privacyAgreed = MutableStateFlow(isPrivacyAgreed())
    val privacyAgreed: StateFlow<Boolean> = _privacyAgreed

    fun isPrivacyAgreed(): Boolean = prefs.getBoolean(KeyPrivacyAgreed, false)

    fun setPrivacyAgreed(agreed: Boolean) {
        prefs.edit().putBoolean(KeyPrivacyAgreed, agreed).apply()
        _privacyAgreed.value = agreed
    }

    fun deviceId(): String {
        prefs.getString(KeyDeviceId, null)?.let { return it }
        val base = Settings.Secure.getString(appContext.contentResolver, Settings.Secure.ANDROID_ID)
            ?.takeIf { it.isNotBlank() && it != "9774d56d682e549c" }
            ?: UUID.randomUUID().toString()
        return md5(base).also { prefs.edit().putString(KeyDeviceId, it).apply() }
    }

    fun putStorage(key: String, value: String) {
        prefs.edit().putString("$StoragePrefix$key", value).apply()
    }

    fun getStorage(key: String): String? = prefs.getString("$StoragePrefix$key", null)

    fun removeStorage(key: String) {
        prefs.edit().remove("$StoragePrefix$key").apply()
    }

    fun savePassword(account: String, password: String) {
        val (encrypted, iv) = securePasswordStore.encrypt(password)
        prefs.edit()
            .putString(KeySavedAccount, account)
            .putString(KeySavedPassword, encrypted)
            .putString(KeySavedPasswordIv, iv)
            .apply()
    }

    fun savedPassword(): SavedPassword? {
        val account = prefs.getString(KeySavedAccount, null).orEmpty()
        val encrypted = prefs.getString(KeySavedPassword, null)
        val iv = prefs.getString(KeySavedPasswordIv, null)
        if (account.isBlank() || encrypted.isNullOrBlank() || iv.isNullOrBlank()) return null
        val password = securePasswordStore.decrypt(encrypted, iv) ?: return null
        return SavedPassword(account, password)
    }

    fun clearSavedPassword() {
        prefs.edit()
            .remove(KeySavedAccount)
            .remove(KeySavedPassword)
            .remove(KeySavedPasswordIv)
            .apply()
    }

    fun clearH5Storage() {
        val editor = prefs.edit()
        prefs.all.keys.filter { it.startsWith(StoragePrefix) }.forEach(editor::remove)
        editor.apply()
    }

    fun clearAllLocalData() {
        prefs.edit().clear().apply()
        _privacyAgreed.value = false
    }

    private fun md5(value: String): String {
        val digest = MessageDigest.getInstance("MD5").digest(value.toByteArray())
        return digest.joinToString("") { "%02x".format(it) }
    }

    private companion object {
        const val KeyPrivacyAgreed = "key_privacy_protection_agreed"
        const val KeyDeviceId = "key_device_id"
        const val KeySavedAccount = "saved_account"
        const val KeySavedPassword = "saved_password"
        const val KeySavedPasswordIv = "saved_password_iv"
        const val StoragePrefix = "h5_storage_"
    }
}
