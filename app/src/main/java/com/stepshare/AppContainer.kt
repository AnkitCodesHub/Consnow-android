package com.Consnow

import android.content.Context
import androidx.room.Room
import com.Consnow.core.database.ConsnowDatabase  // only this one
import com.Consnow.core.datastore.TokenManager
import com.Consnow.core.network.NetworkMonitor
import com.Consnow.core.network.api.ApiService
import com.Consnow.core.network.interceptor.AuthInterceptor
import com.Consnow.features.auth.data.repository.AuthRepositoryImpl
import com.Consnow.features.auth.domain.AuthRepository
import com.Consnow.features.auth.domain.usecase.LoginUseCase
import com.Consnow.features.auth.domain.usecase.RegisterUseCase
import com.Consnow.features.connections.data.repository.ConnectionsRepositoryImpl
import com.Consnow.features.connections.domain.ConnectionsRepository
import com.Consnow.features.connections.domain.usecase.ConnectUseCase
import com.Consnow.features.connections.domain.usecase.GetConnectionsUseCase
import com.Consnow.features.steps.data.cache.PeerProfileCache
import com.Consnow.features.steps.data.repository.StepsRepositoryImpl
import com.Consnow.features.steps.domain.StepsRepository
import com.Consnow.features.steps.domain.usecase.GetPeerStepsUseCase
import com.Consnow.features.steps.domain.usecase.ObserveTodayStepsUseCase
import com.Consnow.features.steps.domain.usecase.SyncStepsUseCase
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

/**
 * Manual DI container. Each val is lazy — created once on first access.
 * Repositories expose interfaces, not implementations.
 * ViewModels receive Use Cases, not repositories.
 *
 * When this gets painful (10+ features), migrate to Hilt:
 *   - Each repo interface gets @Binds in a module
 *   - Each use case gets @Inject constructor
 *   - ViewModels get @HiltViewModel
 *   - Delete this file
 */
class AppContainer(context: Context) {

    // ── Core ──────────────────────────────────────────────

    val tokenManager = TokenManager(context)

    /** Live "do we have internet" signal for showing the offline note. */
    val networkMonitor = NetworkMonitor(context)

    init {
        // Load friends' last known profiles from disk so PeerStepsScreen opens instantly
        PeerProfileCache.init(context)
    }

    private val okHttpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .addInterceptor(AuthInterceptor(tokenManager))
            .addInterceptor(HttpLoggingInterceptor().apply {
                // Release builds log nothing: logcat is readable over USB and would expose
                // friends' user IDs and the login token. Debug builds log bodies, token hidden.
                level = if (BuildConfig.DEBUG) HttpLoggingInterceptor.Level.BODY
                        else HttpLoggingInterceptor.Level.NONE
                redactHeader("Authorization")
            })
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .build()
    }

    val apiService: ApiService by lazy {
        Retrofit.Builder()
            .baseUrl(BuildConfig.API_BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(ApiService::class.java)
    }

    val database: ConsnowDatabase by lazy {
        Room.databaseBuilder(context, ConsnowDatabase::class.java, "Consnow.db")
            .fallbackToDestructiveMigration()
            .build()
    }

    // ── Repositories (exposed as interfaces) ─────────────

    val authRepository: AuthRepository by lazy {
        AuthRepositoryImpl(apiService, tokenManager, context)
    }

    val connectionsRepository: ConnectionsRepository by lazy {
        ConnectionsRepositoryImpl(apiService, context)
    }

    val stepsRepository: StepsRepository by lazy {
        StepsRepositoryImpl(
            stepRecordDao = database.stepRecordDao(),
            apiService = apiService
        )
    }

    // ── Use Cases ────────────────────────────────────────

    val loginUseCase by lazy { LoginUseCase(authRepository) }
    val registerUseCase by lazy { RegisterUseCase(authRepository) }
    val connectUseCase by lazy { ConnectUseCase(connectionsRepository) }
    val getConnectionsUseCase by lazy { GetConnectionsUseCase(connectionsRepository) }
    val observeTodayStepsUseCase by lazy { ObserveTodayStepsUseCase(stepsRepository) }
    val syncStepsUseCase by lazy { SyncStepsUseCase(stepsRepository) }
    val getPeerStepsUseCase by lazy { GetPeerStepsUseCase(stepsRepository) }
}
