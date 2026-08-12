package com.akartis.faceauth.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.akartis.faceauth.data.AuthRepository
import com.akartis.faceauth.face.LoginFaceScreen
import com.akartis.faceauth.face.RegisterFaceScreen
import com.akartis.faceauth.ui.home.HomeScreen
import com.akartis.faceauth.ui.login.LoginScreen
import com.akartis.faceauth.ui.signup.SignupScreen

object Routes {
    const val LOGIN = "login"
    const val SIGNUP = "signup"
    const val HOME = "home"
    const val FACE_ENROLLMENT = "face_enrollment"
    const val FACE_LOGIN = "face_login"
}

private const val KEY_FACE_AUTH_EMAIL = "face_auth_email"

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
                },
                onFaceAuthClick = { email ->
                    navController.currentBackStackEntry?.savedStateHandle?.apply {
                        set(KEY_FACE_AUTH_EMAIL, email)
                    }
                    navController.navigate(Routes.FACE_LOGIN)
                }
            )
        }

        composable(Routes.SIGNUP) {
            SignupScreen(
                onSignupSuccess = {
                    navController.navigate(Routes.FACE_ENROLLMENT) {
                        popUpTo(Routes.SIGNUP) { inclusive = true }
                    }
                },
                onNavigateToLogin = {
                    navController.popBackStack()
                }
            )
        }

        composable(Routes.FACE_ENROLLMENT) {
            val context = androidx.compose.ui.platform.LocalContext.current
            RegisterFaceScreen(
                onRegistrationComplete = {
                    android.widget.Toast.makeText(context, "Enregistrement réussi !", android.widget.Toast.LENGTH_LONG).show()
                    AuthRepository.logout()
                    navController.navigate(Routes.LOGIN) {
                        popUpTo(0) { inclusive = true }
                    }
                }
            )
        }

        composable(Routes.FACE_LOGIN) {
            val email = navController.previousBackStackEntry
                ?.savedStateHandle
                ?.get<String>(KEY_FACE_AUTH_EMAIL)
                .orEmpty()

            LoginFaceScreen(
                email = email,
                onLoginSuccess = {
                    navController.navigate(Routes.HOME) {
                        popUpTo(Routes.LOGIN) { inclusive = true }
                    }
                },
                onBack = { navController.popBackStack() }
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
