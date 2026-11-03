//App calls                    →  HTTP request sent to backend
//─────────────────────────────────────────────────────────
//register(body)               →  POST   /auth/register
//login(body)                  →  POST   /auth/login
//createConnection(body)       →  POST   /connections
//getConnections()             →  GET    /connections?include=disconnected
//disconnect / undo / request  →  POST   /connections/{peerId}/disconnect | undo-disconnect | request
//accept / decline / cancel    →  POST   /connections/requests/{id}/accept | decline, DELETE /connections/requests/{id}
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

    // Swaps a still-valid JWT for a fresh one (sliding session)
    @POST("auth/refresh")
    suspend fun refreshToken(): Response<TokenResponse>

    // Connections
    @POST("connections")
    suspend fun createConnection(@Body body: CreateConnectionRequest): Response<ConnectionResponse>

    // include = "disconnected" also returns disconnected friends with their row state
    @GET("connections")
    suspend fun getConnections(@Query("include") include: String? = "disconnected"): Response<List<ConnectionResponse>>

    @POST("connections/{peerId}/disconnect")
    suspend fun disconnect(@Path("peerId") peerId: String): Response<ConnectionActionResponse>

    @POST("connections/{peerId}/undo-disconnect")
    suspend fun undoDisconnect(@Path("peerId") peerId: String): Response<ConnectionActionResponse>

    @POST("connections/{peerId}/request")
    suspend fun requestConnect(@Path("peerId") peerId: String): Response<ConnectionActionResponse>

    @GET("connections/requests")
    suspend fun getIncomingRequests(): Response<List<IncomingRequestResponse>>

    @POST("connections/requests/{id}/accept")
    suspend fun acceptRequest(@Path("id") requestId: String): Response<ConnectionResponse>

    @POST("connections/requests/{id}/decline")
    suspend fun declineRequest(@Path("id") requestId: String): Response<Unit>

    @DELETE("connections/requests/{id}")
    suspend fun cancelRequest(@Path("id") requestId: String): Response<Unit>

    // Push notifications
    @PUT("notifications/token")
    suspend fun registerPushToken(@Body body: FcmTokenRequest): Response<Unit>

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

    // Who's watching you
    @POST("profile-views/{ownerId}")
    suspend fun recordProfileView(@Path("ownerId") ownerId: String): Response<Any>

    @GET("profile-views")
    suspend fun getProfileViews(@Query("since") since: String): Response<List<ProfileViewResponse>>

    // Activity — lock/unlock/heartbeat
    @PUT("activity/unlock")
    suspend fun reportUnlock(@Body body: LockEventRequest): Response<UnlockResponse>

    @PUT("activity/lock")
    suspend fun reportLock(@Body body: LockEventRequest): Response<LockResponse>

    @PUT("activity/heartbeat")
    suspend fun sendHeartbeat(): Response<Unit>

    @DELETE("auth/me") //edited on 26/10/2026
    suspend fun deleteAccount(): Response<Unit>

    @GET("activity/me")
    suspend fun getMyActivity(): Response<OwnActivityResponse>

    @GET("activity/peer/{userId}")
    suspend fun getPeerActivity(@Path("userId") userId: String): Response<PeerActivityResponse>
}