package com.beacon.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import com.beacon.AppContainer
import com.beacon.service.BeaconService
import com.beacon.ui.chat.ChatScreen
import com.beacon.ui.chat.ChatViewModel
import com.beacon.ui.home.HomeScreen
import com.beacon.ui.home.HomeViewModel
import com.beacon.ui.onboarding.OnboardingScreen
import kotlinx.serialization.Serializable

@Serializable
object OnboardingRoute

@Serializable
object HomeRoute

@Serializable
data class ChatRoute(val roomCode: String, val title: String)

@Composable
fun BeaconNavHost(container: AppContainer) {
    val navController = rememberNavController()
    val context = LocalContext.current
    val start: Any =
        if (container.identityRepository.onboardingComplete) HomeRoute else OnboardingRoute

    // Notification taps land here and open the requested chat.
    LaunchedEffect(Unit) {
        container.pendingChatOpens.collect { route ->
            if (container.identityRepository.onboardingComplete) {
                navController.navigate(route)
            }
        }
    }

    NavHost(navController = navController, startDestination = start) {
        composable<OnboardingRoute> {
            OnboardingScreen(identity = container.identityRepository) {
                BeaconService.start(context)
                navController.navigate(HomeRoute) {
                    popUpTo(OnboardingRoute) { inclusive = true }
                }
            }
        }
        composable<HomeRoute> {
            LaunchedEffect(Unit) { BeaconService.start(context) }
            HomeScreen(
                viewModel = viewModel { HomeViewModel(container) },
                onOpenChat = { roomCode, title ->
                    navController.navigate(ChatRoute(roomCode, title))
                },
            )
        }
        composable<ChatRoute> { backStackEntry ->
            val route = backStackEntry.toRoute<ChatRoute>()
            ChatScreen(
                viewModel = viewModel(key = route.roomCode) {
                    ChatViewModel(container, route.roomCode)
                },
                title = route.title,
                onBack = { navController.popBackStack() },
            )
        }
    }
}
