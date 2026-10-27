package com.Consnow.core.network.interceptor

import com.Consnow.core.datastore.TokenManager
import kotlinx.coroutines.runBlocking
import okhttp3.Interceptor
import okhttp3.Response

class AuthInterceptor(private val tokenManager: TokenManager) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val original = chain.request()
        val path = original.url.encodedPath

        // Only login/register go without a token. (Previously every "/auth/" path was
        // skipped, so DELETE /auth/me was sent unauthenticated.)
        if (path.endsWith("/auth/login") || path.endsWith("/auth/register")) {
            return chain.proceed(original)
        }

        val token = runBlocking { tokenManager.getToken() }
            ?: return chain.proceed(original)

        val response = chain.proceed(
            original.newBuilder().header("Authorization", "Bearer $token").build()
        )

        // Token expired or account gone: drop the dead session so the app goes back to
        // the login screen instead of silently failing forever (the "reinstall to fix" bug).
        if (response.code == 401) {
            runBlocking { tokenManager.clearSessionIfToken(token) }
        }
        return response
    }
}
