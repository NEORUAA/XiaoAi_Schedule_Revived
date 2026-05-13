package com.neoruaa.xiaoaischedule.data

import kotlinx.serialization.Serializable

@Serializable
data class LoginSession(
    val account: String,
    val userId: String,
    val cUserId: String = "",
    val userName: String = "",
    val userIcon: String = "",
    val serviceToken: String = "",
    val passToken: String = "",
    val accessToken: String,
    val refreshToken: String = "",
    val openId: String = "",
    val expiresIn: Long = 0,
    val lastRefreshTimeSeconds: Long = System.currentTimeMillis() / 1000,
) {
    val isLoggedIn: Boolean
        get() = accessToken.isNotBlank()

    fun isExpired(skewSeconds: Long = 60): Boolean {
        if (accessToken.isBlank() || refreshToken.isBlank() || expiresIn <= 0) return false
        val now = System.currentTimeMillis() / 1000
        return lastRefreshTimeSeconds + expiresIn - skewSeconds <= now
    }
}

data class SavedPassword(
    val account: String,
    val password: String,
)
