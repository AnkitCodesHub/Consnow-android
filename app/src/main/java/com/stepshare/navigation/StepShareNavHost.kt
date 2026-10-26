package com.Consnow.navigation

import android.app.Application
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.Consnow.ConsnowApp
import com.Consnow.features.auth.presentation.AuthScreen
import com.Consnow.features.auth.presentation.AuthViewModel
import com.Consnow.features.connections.presentation.ConnectScreen
import com.Consnow.features.connections.presentation.ConnectionsViewModel
import com.Consnow.features.steps.presentation.*

object Routes {
    const val AUTH = "auth"
    const val HOME = "home"
    const val CONNECT = "connect"
    const val PEER_STEPS = "peer_steps/{peerId}"
    fun peerSteps(peerId: String) = "peer_steps/$peerId"
}

@Composable
fun ConsnowNavHost() {
    val context = LocalContext.current
    val app = context.applicationContext as ConsnowApp
    val c = app.appContainer
    val navController = rememberNavController()

    // Auth ViewModel lives here so login state persists across all screens
    val authViewModel: AuthViewModel = viewModel(
        factory = AuthViewModel.Factory(c.loginUseCase, c.registerUseCase, c.authRepository)
    )
    val authState by authViewModel.state.collectAsState()

    // Auto-navigate to Home when login succeeds, clearing the auth backstack
    LaunchedEffect(authState.isLoggedIn) {
        if (authState.isLoggedIn) {
            navController.navigate(Routes.HOME) {
                popUpTo(Routes.AUTH) { inclusive = true }
            }
        }
    }

    NavHost(
        navController = navController,
        startDestination = if (authState.isLoggedIn) Routes.HOME else Routes.AUTH
    ) {

        // ── Auth Screen ───────────────────────────────────────────────────────
        composable(Routes.AUTH) {
            AuthScreen(viewModel = authViewModel)
        }

        // ── Home Screen ───────────────────────────────────────────────────────
        composable(Routes.HOME) {
            val stepsViewModel: StepsViewModel = viewModel(
                factory = StepsViewModel.Factory(
                    application = context.applicationContext as Application,
                    observeTodayStepsUseCase = c.observeTodayStepsUseCase,
                    tokenManager = c.tokenManager,
                )
            )
            val connectionsViewModel: ConnectionsViewModel = viewModel(
                factory = ConnectionsViewModel.Factory(
                    connectUseCase = c.connectUseCase,
                    getConnectionsUseCase = c.getConnectionsUseCase,
                    tokenManager = c.tokenManager,
                )
            )
            val stepsState by stepsViewModel.state.collectAsState()
            val connectionsState by connectionsViewModel.state.collectAsState()

            // Own lock status — sourced from ActivityEventBus via StepsViewModel
            // Updated immediately on lock/unlock via BroadcastReceiver in StepCounterService
            val ownIsLocked by stepsViewModel.ownIsLocked.collectAsState()
            val ownLastUnlockedAtMs by stepsViewModel.ownLastUnlockedAtMs.collectAsState()

            HomeScreen(
                stepsState = stepsState,
                connectionsState = connectionsState,
                onPermissionResult = { granted ->
                    if (granted) stepsViewModel.onPermissionGranted()
                    else stepsViewModel.onPermissionDenied()
                },
                onSync = { stepsViewModel.triggerSync() },
                onPeerClick = { peer -> navController.navigate(Routes.peerSteps(peer.id)) },
                onConnectClick = { navController.navigate(Routes.CONNECT) },
                onLogout = {
                    authViewModel.logout()
                    navController.navigate(Routes.AUTH) { popUpTo(0) { inclusive = true } }
                },
                onDeleteAccount = {
                    stepsViewModel.deleteAccount(
                        onSuccess = {
                            authViewModel.logout()
                            navController.navigate(Routes.AUTH) { popUpTo(0) { inclusive = true } }
                        },
                        onError = {
                            // silently ignore for now — can add a Snackbar later
                        }
                    )
                },
                ownIsLocked = ownIsLocked,
                ownLastUnlockedAtMs = ownLastUnlockedAtMs,
            )
        }

        // ── Connect Screen ────────────────────────────────────────────────────
        composable(Routes.CONNECT) {
            val connectionsViewModel: ConnectionsViewModel = viewModel(
                factory = ConnectionsViewModel.Factory(
                    connectUseCase = c.connectUseCase,
                    getConnectionsUseCase = c.getConnectionsUseCase,
                    tokenManager = c.tokenManager,
                )
            )
            val state by connectionsViewModel.state.collectAsState()
            ConnectScreen(
                state = state,
                onConnect = { code -> connectionsViewModel.connect(code) },
                onBack = { navController.popBackStack() }
            )
        }

        // ── Peer Steps Screen ─────────────────────────────────────────────────
        composable(
            route = Routes.PEER_STEPS,
            arguments = listOf(navArgument("peerId") { type = NavType.StringType })
        ) { backStackEntry ->
            val peerId = backStackEntry.arguments?.getString("peerId") ?: return@composable

            val peerStepsViewModel: PeerStepsViewModel = viewModel(
                factory = PeerStepsViewModel.Factory(c.getPeerStepsUseCase, c.apiService, peerId)
            )
            val state by peerStepsViewModel.state.collectAsState()

            // Peer lock status — polled every 15s + updated via WebSocket events
            // isOnline drives the offline state in LockStatusWidget (heartbeat-based)
            val peerIsLocked by peerStepsViewModel.isLocked.collectAsState()
//            val peerIsOnline by peerStepsViewModel.isOnline.collectAsState()
            val peerLastUnlockedAtMs by peerStepsViewModel.lastUnlockedAtMs.collectAsState()

            // Start auto-refreshing peer steps every 5s when this screen is visible
            LaunchedEffect(Unit) { peerStepsViewModel.startAutoRefresh() }

            PeerStepsScreen(
                state = state,
                onRefresh = { peerStepsViewModel.loadPeerSteps() },
                onBack = { navController.popBackStack() },
                isLocked = peerIsLocked,
//                isOnline = peerIsOnline,
                lastUnlockedAtMs = peerLastUnlockedAtMs,
            )
        }
    }
}