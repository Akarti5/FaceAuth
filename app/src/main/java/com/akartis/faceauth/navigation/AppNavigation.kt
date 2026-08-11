package com.akartis.faceauth.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.akartis.faceauth.data.AuthRepository
import com.akartis.faceauth.ui.home.HomeScreen
import com.akartis.faceauth.ui.login.LoginScreen
import com.akartis.faceauth.ui.signup.SignupScreen

object Routes {
    const val LOGIN = "login"
    const val SIGNUP = "signup"
    const val HOME = "home"
}

@Composable
fun AppNavigation(
    navController: NavHostController = rememberNavController()
) {
    val startDestination = if (AuthRepository.isLoggedIn()) Routes.HOME else Routes.LOGIN

    NavHost(
        navController = navController,
        startDestination = startDestination
    ) {
        composable(Routes.LOGIN) {
            LoginScreen(
                onLoginSuccess = {
                    navController.navigate(Routes.HOME) {
                        popUpTo(Routes.LOGIN) { inclusive = true }
                    }
                },
                onNavigateToSignup = {
                    navController.navigate(Routes.SIGNUP)
                }
            )
        }

        composable(Routes.SIGNUP) {
            SignupScreen(
                onSignupSuccess = {
                    navController.navigate(Routes.HOME) {
                        popUpTo(Routes.LOGIN) { inclusive = true }
                    }
                },
                onNavigateToLogin = {
                    navController.popBackStack()
                }
            )
        }

        composable(Routes.HOME) {
            HomeScreen(
                onLogout = {
                    AuthRepository.logout()
                    navController.navigate(Routes.LOGIN) {
                        popUpTo(Routes.HOME) { inclusive = true }
                    }
                }
            )
        }
    }
}
