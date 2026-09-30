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
}
