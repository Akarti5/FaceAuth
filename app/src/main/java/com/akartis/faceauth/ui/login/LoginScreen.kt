package com.akartis.faceauth.ui.login

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.outlined.Email
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.akartis.faceauth.R
import com.akartis.faceauth.data.AuthRepository
import com.akartis.faceauth.ui.theme.FaceAuthBackground
import com.akartis.faceauth.ui.theme.FaceAuthBorder
import com.akartis.faceauth.ui.theme.FaceAuthError
import com.akartis.faceauth.ui.theme.FaceAuthGreen
import com.akartis.faceauth.ui.theme.FaceAuthSurface
import com.akartis.faceauth.ui.theme.FaceAuthTextPrimary
import com.akartis.faceauth.ui.theme.FaceAuthTextSecondary
import com.akartis.faceauth.ui.theme.FaceAuthTheme
import kotlinx.coroutines.launch

@Composable
fun LoginScreen(
    onLoginSuccess: () -> Unit,
    onNavigateToSignup: () -> Unit,
    onFaceAuthClick: (email: String) -> Unit = {},
    onForgotPasswordClick: () -> Unit = {}
) {
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }
    var rememberMe by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var isLoading by remember { mutableStateOf(false) }

    val focusManager = LocalFocusManager.current
    val scope = rememberCoroutineScope()
    val fieldShape = RoundedCornerShape(12.dp)

    val context = androidx.compose.ui.platform.LocalContext.current

    fun submitLogin() {
        if (isLoading) return
        scope.launch {
            attemptLogin(
                context = context,
                email = email,
                password = password,
                onError = { errorMessage = it },
                onLoading = { isLoading = it },
                onSuccess = onLoginSuccess
            )
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(FaceAuthBackground)
            .imePadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Image(
            painter = painterResource(R.drawable.faceauth_notitle),
            contentDescription = stringResource(R.string.faceauth_logo_cd),
            modifier = Modifier.size(170.dp)
        )

        Text(
            text = buildAnnotatedString {
                withStyle(
                    SpanStyle(
                        color = FaceAuthTextPrimary,
                        fontWeight = FontWeight.Bold,
                        fontSize = 40.sp
                    )
                ) {
                    append("face")
                }
                withStyle(
                    SpanStyle(
                        color = FaceAuthGreen,
                        fontWeight = FontWeight.Bold,
                        fontSize = 40.sp
                    )
                ) {
                    append("auth")
                }
            },
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(28.dp))

        Text(
            text = stringResource(R.string.welcome_back),
            color = FaceAuthTextPrimary,
            style = MaterialTheme.typography.headlineMedium,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = stringResource(R.string.login_subtitle),
            color = FaceAuthTextSecondary,
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(28.dp))

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, FaceAuthBorder, RoundedCornerShape(20.dp))
                .background(FaceAuthSurface, RoundedCornerShape(20.dp))
                .padding(24.dp)
        ) {
            Text(
                text = stringResource(R.string.email),
                color = FaceAuthTextPrimary,
                style = MaterialTheme.typography.labelMedium
            )

            Spacer(modifier = Modifier.height(8.dp))

            OutlinedTextField(
                value = email,
                onValueChange = {
                    email = it
                    errorMessage = null
                },
                placeholder = {
                    Text(stringResource(R.string.email_placeholder))
                },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Outlined.Email,
                        contentDescription = null,
                        tint = FaceAuthGreen
                    )
                },
                singleLine = true,
                shape = fieldShape,
                colors = darkFieldColors(),
                isError = errorMessage != null,
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Email,
                    imeAction = ImeAction.Next
                ),
                keyboardActions = KeyboardActions(
                    onNext = { focusManager.moveFocus(FocusDirection.Down) }
                ),
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = stringResource(R.string.password),
                color = FaceAuthTextPrimary,
                style = MaterialTheme.typography.labelMedium
            )

            Spacer(modifier = Modifier.height(8.dp))

            OutlinedTextField(
                value = password,
                onValueChange = {
                    password = it
                    errorMessage = null
                },
                placeholder = {
                    Text(stringResource(R.string.password_placeholder))
                },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Outlined.Lock,
                        contentDescription = null,
                        tint = FaceAuthGreen
                    )
                },
                trailingIcon = {
                    IconButton(onClick = { passwordVisible = !passwordVisible }) {
                        Icon(
                            imageVector = if (passwordVisible) {
                                Icons.Filled.VisibilityOff
                            } else {
                                Icons.Filled.Visibility
                            },
                            contentDescription = stringResource(
                                if (passwordVisible) {
                                    R.string.hide_password_cd
                                } else {
                                    R.string.show_password_cd
                                }
                            ),
                            tint = FaceAuthTextSecondary
                        )
                    }
                },
                singleLine = true,
                shape = fieldShape,
                colors = darkFieldColors(),
                isError = errorMessage != null,
                visualTransformation = if (passwordVisible) {
                    VisualTransformation.None
                } else {
                    PasswordVisualTransformation()
                },
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Password,
                    imeAction = ImeAction.Done
                ),
                keyboardActions = KeyboardActions(
                    onDone = {
                        focusManager.clearFocus()
                        submitLogin()
                    }
                ),
                modifier = Modifier.fillMaxWidth()
            )

            errorMessage?.let { error ->
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = error,
                    color = FaceAuthError,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(
                        checked = rememberMe,
                        onCheckedChange = { rememberMe = it },
                        colors = CheckboxDefaults.colors(
                            checkedColor = FaceAuthGreen,
                            uncheckedColor = FaceAuthTextSecondary,
                            checkmarkColor = Color.Black
                        )
                    )
                    Text(
                        text = stringResource(R.string.remember_me),
                        color = FaceAuthTextSecondary,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }

                Text(
                    text = stringResource(R.string.forgot_password),
                    color = FaceAuthGreen,
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                    modifier = Modifier.clickable(onClick = onForgotPasswordClick)
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        Button(
            onClick = { submitLogin() },
            enabled = !isLoading,
            shape = RoundedCornerShape(16.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = FaceAuthGreen,
                contentColor = Color.Black,
                disabledContainerColor = FaceAuthGreen.copy(alpha = 0.5f),
                disabledContentColor = Color.Black.copy(alpha = 0.6f)
            ),
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
        ) {
            if (isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(22.dp),
                    strokeWidth = 2.dp,
                    color = Color.Black
                )
            } else {
                Text(
                    text = stringResource(R.string.login_button),
                    fontWeight = FontWeight.Bold
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            HorizontalDivider(
                modifier = Modifier.weight(1f),
                color = FaceAuthBorder
            )
            Text(
                text = stringResource(R.string.or_divider),
                color = FaceAuthTextSecondary,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(horizontal = 12.dp)
            )
            HorizontalDivider(
                modifier = Modifier.weight(1f),
                color = FaceAuthBorder
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        OutlinedButton(
            onClick = {
                val trimmedEmail = email.trim()
                val hasLocalCreds = com.akartis.faceauth.data.EncryptedCredentialStore.hasCredentials(context)
                if (trimmedEmail.isEmpty() && !hasLocalCreds) {
                    errorMessage = "Veuillez saisir votre email"
                } else {
                    errorMessage = null
                    onFaceAuthClick(trimmedEmail)
                }
            },
            shape = RoundedCornerShape(16.dp),
            border = BorderStroke(1.dp, FaceAuthGreen),
            colors = ButtonDefaults.outlinedButtonColors(
                containerColor = Color.Transparent,
                contentColor = FaceAuthTextPrimary
            ),
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
        ) {
            Image(
                painter = painterResource(R.drawable.faceauth_notitle),
                contentDescription = stringResource(R.string.face_auth_icon_cd),
                modifier = Modifier.size(28.dp)
            )
            Spacer(modifier = Modifier.width(10.dp))
            Text(
                text = buildAnnotatedString {
                    withStyle(
                        SpanStyle(
                            color = FaceAuthTextPrimary,
                            fontWeight = FontWeight.Bold
                        )
                    ) {
                        append(stringResource(R.string.continue_with_prefix))
                    }
                    withStyle(
                        SpanStyle(
                            color = FaceAuthGreen,
                            fontWeight = FontWeight.Bold
                        )
                    ) {
                        append(stringResource(R.string.face_authentication))
                    }
                }
            )
        }

        Spacer(modifier = Modifier.height(28.dp))

        Row(
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = stringResource(R.string.no_account),
                color = FaceAuthTextSecondary,
                style = MaterialTheme.typography.bodyMedium
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = stringResource(R.string.signup_link),
                color = FaceAuthGreen,
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                modifier = Modifier.clickable(onClick = onNavigateToSignup)
            )
        }
    }
}

@Composable
private fun darkFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedTextColor = FaceAuthTextPrimary,
    unfocusedTextColor = FaceAuthTextPrimary,
    focusedContainerColor = FaceAuthBackground,
    unfocusedContainerColor = FaceAuthBackground,
    disabledContainerColor = FaceAuthBackground,
    focusedBorderColor = FaceAuthGreen,
    unfocusedBorderColor = FaceAuthBorder,
    errorBorderColor = FaceAuthError,
    cursorColor = FaceAuthGreen,
    focusedPlaceholderColor = FaceAuthTextSecondary,
    unfocusedPlaceholderColor = FaceAuthTextSecondary,
    focusedLeadingIconColor = FaceAuthGreen,
    unfocusedLeadingIconColor = FaceAuthGreen,
    focusedTrailingIconColor = FaceAuthTextSecondary,
    unfocusedTrailingIconColor = FaceAuthTextSecondary,
    errorTrailingIconColor = FaceAuthError
)

private suspend fun attemptLogin(
    context: android.content.Context,
    email: String,
    password: String,
    onError: (String) -> Unit,
    onLoading: (Boolean) -> Unit,
    onSuccess: () -> Unit
) {
    val trimmedEmail = email.trim()
    when {
        trimmedEmail.isEmpty() -> onError("Veuillez saisir votre email")
        !trimmedEmail.contains("@") -> onError("Email invalide")
        password.isEmpty() -> onError("Veuillez saisir votre mot de passe")
        else -> {
            onLoading(true)
            AuthRepository.login(trimmedEmail, password)
                .onSuccess { 
                    com.akartis.faceauth.data.EncryptedCredentialStore.save(context, trimmedEmail, password)
                    onSuccess() 
                }
                .onFailure { onError(it.message ?: "Échec de la connexion") }
            onLoading(false)
        }
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF0A0A0A)
@Composable
private fun LoginScreenPreview() {
    FaceAuthTheme {
        LoginScreen(
            onLoginSuccess = {},
            onNavigateToSignup = {}
        )
    }
}
