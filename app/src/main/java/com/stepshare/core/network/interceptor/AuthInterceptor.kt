package com.Consnow.core.network.interceptor

import com.Consnow.core.datastore.TokenManager
import kotlinx.coroutines.runBlocking
import okhttp3.Interceptor
import okhttp3.Response

class AuthInterceptor(private val tokenManager: TokenManager) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val original = chain.request()

        // Skip auth header for login/register
        if (original.url.encodedPath.contains("/auth/")) {
            return chain.proceed(original)
        }

        val token = runBlocking { tokenManager.getToken() }

        val request = if (token != null) {
            original.newBuilder()
                .header("Authorization", "Bearer $token")
                .build()
        } else {
            original
        }

        return chain.proceed(request)
    }
}
