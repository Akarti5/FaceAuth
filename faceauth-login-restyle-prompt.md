# Prompt: Restyle Login Screen to "FaceAuth" Dark/Neon Design (Android, Kotlin, Jetpack Compose)

Restyle my **existing login screen**, written in Kotlin with Jetpack Compose, to visually match the design described below.
**Do NOT change, remove, or rewrite any existing logic** — ViewModel, state hoisting (`remember`/`mutableStateOf`/`StateFlow`/`collectAsState`), form validation, authentication/API calls, navigation (`NavController` calls), or the actual biometric/face-auth integration (e.g. `BiometricPrompt` / CameraX / whatever is already wired up). This is a **pure Composable UI/styling pass**. Keep all existing parameter names, callbacks (`onLoginClick`, `onFaceAuthClick`, `onSignUpClick`, `onForgotPasswordClick`, text field `onValueChange` lambdas, etc.) intact so the screen keeps working exactly as before — just re-skin the Composables that render it.

## Assets to use
- `faceauth_icon.png` and `faceauth_notitle.png` are already provided — add them to `res/drawable/` (or `res/drawable-nodpi/` if they're large raster PNGs) as `faceauth_icon` and `faceauth_notitle`, and reference them via `painterResource(R.drawable.faceauth_icon)` / `painterResource(R.drawable.faceauth_notitle)`.
- `faceauth_icon.png` → the square face-scan icon (corner brackets + face), used above the wordmark, rendered with an `Image` composable.
- `faceauth_notitle.png` → same icon without the wordmark text, use as the small leading icon inside the "Continue with Face Authentication" `OutlinedButton`.
- The "faceauth" wordmark should be rendered as a real `Text` composable (not baked into the image) using an `AnnotatedString` / `buildAnnotatedString` so "face" is white and "auth" is the accent green, both bold, lowercase, no space, forming one word "faceauth".

## Overall theme
Define these as reusable `Color` constants (in `Color.kt` or the app's theme file) rather than hardcoding hex values inline in the Composables:
- **Background:** near-black, `Color(0xFF0A0A0A)` (or pure `Color.Black`).
- **Accent color:** bright lime/neon green, approx `Color(0xFF84D633)` — sample exactly from `faceauth_icon.png` if possible so it's pixel-consistent with the logo. Name it something like `FaceAuthGreen`.
- **Primary text:** white `Color(0xFFFFFFFF)`.
- **Secondary/muted text:** light gray, approx `Color(0xFF9A9A9A)`.
- **Borders:** subtle, low-opacity white/gray, e.g. `Color.White.copy(alpha = 0.12f)`, 1dp stroke, rounded corners (`RoundedCornerShape(12.dp–16.dp)`) on text fields and the card container.
- **Error color:** something that still fits the dark theme, e.g. `Color(0xFFFF6B6B)`, for validation error text/borders.
- **Font:** rounded, friendly sans-serif. Add a Google/variable font such as "Poppins", "Baloo 2", or "Nunito" via a `FontFamily` in the theme (e.g. using `androidx.compose.ui.text.font.Font` with downloadable fonts, or bundle `.ttf` files in `res/font/`). Bold weight for headings/buttons/labels, regular weight for placeholders/body text. Fall back to the default system font family if you don't want to add font files.
- Apply this as a dedicated dark `ColorScheme`/`Theme` (e.g. update `Theme.kt`'s `darkColorScheme(...)`) so it's consistent app-wide, not just hacked into this one screen.
- Layout is a single centered `Column` inside a scrollable container (`Modifier.verticalScroll(rememberScrollState())`), horizontally centered, with `Modifier.padding(horizontal = 24.dp)` and generous `Spacer`s between sections (16–32.dp).

## Page structure (top to bottom, inside the Column)

1. **Logo block** (centered, `Modifier.align(Alignment.CenterHorizontally)`)
   - `Image(painterResource(R.drawable.faceauth_icon), ...)`, roughly 160–180.dp square.
   - Below it, a `Text` with `buildAnnotatedString` for "faceauth" at ~40.sp, bold — "face" span in white, "auth" span in accent green, no gap between them.

2. **Heading block** (centered)
   - "Welcome back" — `Text`, bold, white, ~28.sp.
   - "Login to your account" — `Text`, muted gray, ~16.sp, regular weight, directly beneath (small `Spacer` between).

3. **Form card**
   - A `Card` or plain `Box`/`Column` with `Modifier.border(1.dp, BorderColor, RoundedCornerShape(20.dp))`, background either transparent or a slightly lighter-than-background fill (`Color(0xFF141414)`), `Modifier.padding(24.dp)`.
   - **Email field:**
     - Label "Email" — small bold white `Text` above the field.
     - `OutlinedTextField` (or custom `BasicTextField` if you need more control) bound to the existing email state/callback, with `leadingIcon = { Icon(Icons.Outlined.Email, tint = FaceAuthGreen, ...) }`, placeholder "Enter your email", `OutlinedTextFieldDefaults.colors(...)` set to dark fill + subtle border + `RoundedCornerShape(12.dp)`, muted placeholder color.
   - **Password field:**
     - Label "Password" — same style as Email label.
     - `OutlinedTextField` bound to the existing password state, `leadingIcon` = green lock icon, `trailingIcon` = an `IconButton` with an eye icon that toggles `visualTransformation` between `PasswordVisualTransformation()` and `VisualTransformation.None` — wire this into whatever show/hide `mutableStateOf<Boolean>` already exists in the screen.
   - **Row below password:** `Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween)`
     - Left: `Checkbox` + "Remember me" `Text`, checkbox colors overridden (`CheckboxDefaults.colors(checkedColor = FaceAuthGreen, checkmarkColor = Color.Black, ...)`), bound to the existing "remember me" state.
     - Right: "Forgot password?" as a clickable `Text` in accent green (`Modifier.clickable { onForgotPasswordClick() }`), reusing the existing callback/nav action.

4. **Login button**
   - `Button` with `Modifier.fillMaxWidth().height(56.dp)`, `shape = RoundedCornerShape(16.dp)`, `colors = ButtonDefaults.buttonColors(containerColor = FaceAuthGreen)`, `Text("Login", color = Color.Black, fontWeight = FontWeight.Bold)`. `onClick` calls the existing login submit handler/ViewModel function unchanged.

5. **Divider**
   - A `Row` with a thin `Divider`/`HorizontalDivider` on each side (low-opacity gray) and "or" centered between them in muted gray `Text`, small horizontal padding around the word.

6. **Face Authentication button**
   - `OutlinedButton` with `Modifier.fillMaxWidth().height(56.dp)`, `shape = RoundedCornerShape(16.dp)`, `border = BorderStroke(1.dp, FaceAuthGreen)`, transparent/dark container color.
   - Leading: small `Image(painterResource(R.drawable.faceauth_notitle), ...)`.
   - Text: `buildAnnotatedString` — "Continue with " in white + "Face Authentication" in accent green, bold, same `Text`.
   - `onClick` calls whatever existing face-auth/biometric handler is already wired up in the ViewModel/Activity (e.g. launching `BiometricPrompt` or a CameraX flow) — do not change that logic, just re-skin the trigger button. If no handler exists yet, leave the `onClick` as a passed-in lambda parameter (e.g. `onFaceAuthClick: () -> Unit`) so it's easy to hook up later.

7. **Footer text**
   - Centered `Row` or single `Text` with `buildAnnotatedString`: "Don't have an account? " in muted gray + "Sign up" in accent green as a clickable span (use `ClickableText` or a `Modifier.clickable` on the whole `Text` with an `LinkAnnotation`/manual tap-offset check) that calls the existing sign-up navigation callback.

## Component/interaction details to preserve
- Keep the existing form validation and error states (`isError`, `supportingText`, whatever mechanism is already there) — just restyle the error color/text to be readable on the dark background (`Color(0xFFFF6B6B)`).
- Set `OutlinedTextFieldDefaults.colors(focusedBorderColor = FaceAuthGreen, unfocusedBorderColor = BorderColor, ...)` so focused fields switch to the accent green border. A soft glow isn't natively supported by `OutlinedTextField` — skip it or approximate with a subtle `Modifier.shadow` if you want to get fancy, but it's not required.
- Buttons should use Compose's default `Interaction`/ripple for pressed states; you can optionally override `ButtonDefaults` for a slightly different pressed container color, but don't change `onClick` behavior.
- Preserve/add `contentDescription` on all `Image`/`Icon` composables and proper `semantics`/`Modifier.testTag` if the existing code already has them (for accessibility and any existing UI tests) — don't strip these out while restyling.
- Make sure contrast ratios stay reasonable for readability (white/gray text on near-black background), consistent with Material accessibility guidelines.

## Deliverable
Apply this styling directly to the existing login `@Composable` function(s) (e.g. `LoginScreen.kt`), reusing the existing composable structure, state hoisting, and parameters where possible rather than rewriting the screen from scratch. Add `faceauth_icon.png` and `faceauth_notitle.png` to `res/drawable/` and reference them via `painterResource`, rather than recreating the icon as a Compose `Canvas`/vector. If a shared `Theme.kt`/`Color.kt` already exists, add the new colors there so they're reusable across other screens later.
