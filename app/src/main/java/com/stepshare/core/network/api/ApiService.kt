//App calls                    →  HTTP request sent to backend
//─────────────────────────────────────────────────────────
//register(body)               →  POST   /auth/register
//login(body)                  →  POST   /auth/login
//createConnection(body)       →  POST   /connections
//getConnections()             →  GET    /connections
//syncSteps(body)              →  PUT    /steps/sync
//getMySteps(date)             →  GET    /steps/me?date=2026-10-04
//getPeerSteps(userId, date)   →  GET    /steps/user/abc123?date=2026-10-04

//edited :- 04/10/2026 by Ankit
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
    @PUT("activity/unlock") //sends a put req. when phone unlocked & the backend records the unlock timme
    suspend fun reportUnlock(): Response<UnlockResponse>

    @PUT("activity/lock") //put request when phone is locked
    suspend fun reportLock(): Response<LockResponse>

    @GET("activity/me") //fetches activity from backend
    suspend fun getMyActivity(): Response<OwnActivityResponse>

    @GET("activity/peer/{userId}") //fetches another users activity from backend
    suspend fun getPeerActivity(@Path("userId") userId: String): Response<PeerActivityResponse>

// Data classes (add to your models file or inline):
data class UnlockResponse(val success: Boolean, val unlockedAt: String)
data class PeerUnlockResponse(val userId: Int, val unlockedAt: String?)
}
