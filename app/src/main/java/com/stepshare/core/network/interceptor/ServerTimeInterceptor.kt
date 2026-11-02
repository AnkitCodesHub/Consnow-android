package com.Consnow.core.network.interceptor

import com.Consnow.core.common.TrustedClock
import okhttp3.Interceptor
import okhttp3.Response

/** Feeds the server's time (HTTP Date header) into TrustedClock on every API response. */
class ServerTimeInterceptor : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val response = chain.proceed(chain.request())
        response.headers.getDate("Date")?.let { TrustedClock.onServerTime(it.time) }
        return response
    }
}
