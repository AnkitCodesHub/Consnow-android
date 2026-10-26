//App calls                    →  HTTP request sent to backend
//─────────────────────────────────────────────────────────
//register(body)               →  POST   /auth/register
//login(body)                  →  POST   /auth/login
//createConnection(body)       →  POST   /connections
//getConnections()             →  GET    /connections
//syncSteps(body)              →  PUT    /steps/sync
//getMySteps(date)             →  GET    /steps/me?date=2026-10-04
//getPeerSteps(userId, date)   →  GET    /steps/user/abc123?date=2026-10-04
//updated: 15/10/2026 — reportUnlock/reportLock now accept LockEventRequest body

package com.Consnow.core.network.api

import com.Consnow.core.network.dto.*
import retrofit2.Response
import retrofit2.http.*

interface ApiService {

    // Auth
    @POST("auth/register")
    suspend fun register(@Body body: RegisterRequest): Response<AuthResponse>

    @POST("auth/login")
    suspend fun login(@Body body: LoginRequest): Response<AuthResponse>

    // Connections
    @POST("connections")
    suspend fun createConnection(@Body body: CreateConnectionRequest): Response<ConnectionResponse>

    @GET("connections")
    suspend fun getConnections(): Response<List<ConnectionResponse>>

    // Steps
    @PUT("steps/sync")
    suspend fun syncSteps(@Body body: SyncStepsRequest): Response<Any>

    @GET("steps/me")
    suspend fun getMySteps(@Query("date") date: String): Response<MyStepsResponse>

    @GET("steps/user/{userId}")
    suspend fun getPeerSteps(
        @Path("userId") userId: String,
        @Query("date") date: String
    ): Response<PeerStepsResponse>

    // Activity — lock/unlock/heartbeat
    @PUT("activity/unlock")
    suspend fun reportUnlock(@Body body: LockEventRequest): Response<UnlockResponse>

    @PUT("activity/lock")
    suspend fun reportLock(@Body body: LockEventRequest): Response<LockResponse>

    @PUT("activity/heartbeat")
    suspend fun sendHeartbeat(): Response<Unit>

    @DELETE("auth/me") //edited on 26/10/2026
    suspend fun deleteAccount(): Response<Unit>

    @PUT("activity/unlock")
    suspend fun reportUnlock(@Body body: ActivityEventRequest): Response<UnlockResponse>

    @PUT("activity/lock")
    suspend fun reportLock(@Body body: ActivityEventRequest): Response<LockResponse>

    @GET("activity/me")
    suspend fun getMyActivity(): Response<OwnActivityResponse>

    @GET("activity/peer/{userId}")
    suspend fun getPeerActivity(@Path("userId") userId: String): Response<PeerActivityResponse>

    data class UnlockResponse(val success: Boolean, val unlockedAt: String)
    data class PeerUnlockResponse(val userId: Int, val unlockedAt: String?)
}