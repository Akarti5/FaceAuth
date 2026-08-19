![My Image](./faceauth_icon.png)
AI generated content :
# FaceAuth Project Documentation

This document describes the real application flow, the main files in the project, and how the face-embedding system works from the camera to Firestore and back to authentication.

## 1. Project purpose

This project is an Android app that allows:

- regular email/password signup and login
- face registration/enrollment
- face-based login using a local face embedding comparison
- secure storage of credentials using Android Keystore encryption
- Firestore storage for the face embedding vector

The main idea is:

- capture a face from the front camera
- detect facial landmarks and orientation using ML Kit
- crop the face and run it through a TensorFlow Lite MobileFaceNet model
- generate a 192-dimension embedding vector
- store the embedding in Firestore under the authenticated user
- during login, compare a new embedding to the stored one using cosine similarity
- if similarity is high enough, use locally stored encrypted credentials to sign in with Firebase Auth

---

## 2. Root project files

These are the files at the repository root:

- `build.gradle.kts` — top-level Gradle configuration for the Android project
- `settings.gradle.kts` — project settings and module registration
- `gradle.properties` — Gradle runtime settings
- `gradlew` and `gradlew.bat` — project wrapper used to build and run the app
- `readme.md` — project description / usage notes
- `firebase.json` — Firebase configuration for hosting or Firebase tooling
- `local.properties` — local Android SDK path configuration
- `app/` — the Android application module

The generated folders under `app/build/` are build artifacts and not part of the application logic. They are created by Gradle and should not be treated as source files.

---

## 3. Android app module structure

### `app/`

- `build.gradle.kts` — app-level dependencies, Firebase, Compose, CameraX, ML Kit, security crypto, TensorFlow Lite
- `google-services.json` — Firebase project config used for Google services integration
- `proguard-rules.pro` — code shrinking rules
- `src/main/AndroidManifest.xml` — app permissions and app configuration

### `src/main/java/com/akartis/faceauth/`

This is the main application code:

#### `MainActivity.kt`
Purpose:
- Android entry point
- creates the Compose UI root
- sets the app theme and launches `AppNavigation()`

Flow:
1. activity starts
2. `setContent { FaceAuthTheme { AppNavigation() } }`
3. navigation decides whether to open login or home screen

#### `navigation/AppNavigation.kt`
Purpose:
- central route manager for the app
- handles login, signup, face enrollment, face login, home, and enrollment success screens

Routes include:
- `login`
- `signup`
- `home`
- `face_enrollment`
- `face_login`
- `enrollment_success`

Important logic:
- if Firebase user is already logged in, the app starts at `HOME`
- after signup success, app navigates to face enrollment
- after registration success, app logs out the Firebase user and directs them to success screen
- face login receives `email` via `savedStateHandle` from the regular login screen

#### `camera/FaceCaptureScreen.kt`
Purpose:
- uses CameraX to open the front camera
- requests camera permission if needed
- runs `FaceImageAnalyzer` on each frame
- shows the live camera preview in a rounded/circular layout
- emits face analysis data back to the calling screen as a callback

Important parts:
- uses `ProcessCameraProvider` and `ImageAnalysis`
- binds `Preview` + `ImageAnalysis` to the front camera
- `onFaceAnalyzed` callback receives:
  - cropped face bitmap
  - `headEulerAngleY`
  - `leftEyeOpenProbability`
  - `rightEyeOpenProbability`

This is the UI layer that connects the real camera stream to the face recognition logic.

#### `camera/FaceImageAnalyzer.kt`
Purpose:
- takes each camera frame
- uses ML Kit `FaceDetection` to locate the face
- extracts the largest face found
- crops the face area from the frame
- forwards face metadata to the UI logic

Important details:
- `FaceDetectorOptions` uses `CLASSIFICATION_MODE_ALL` because eye openness is needed for blink detection
- `headEulerAngleY` is used to detect whether the user is facing front or angled left/right
- `leftEyeOpenProbability` and `rightEyeOpenProbability` are used to check whether the user has their eyes open or blinked

#### `ml/EmbeddingMath.kt`
Purpose:
- contains the numeric math used after the model generates a face embedding

Functions:
- `averageAndL2Normalize(embeddings)`
  - averages multiple 192-length embeddings
  - applies L2 normalization to get a final stable compare vector
- `l2Normalize(embedding)`
  - normalizes a vector so the length becomes 1
- `cosineSimilarity(a, b)`
  - computes similarity between two normalized vectors
  - this is the method used to determine whether two faces match

#### `face/FaceNetHelper.kt`
Purpose:
- loads the TensorFlow Lite face model (`mobilefacenet.tflite`) from the app assets
- preprocesses a cropped face bitmap into model input format
- runs the model and returns a 192-element embedding vector

Important logic:
- input size is 112x112
- image pixels are normalized from `[0,255]` to `[-1,1]`
- model output is a `FloatArray(192)`
- the raw embedding is returned; normalization is handled later by `EmbeddingMath`

This is the core part that converts an image into a vector representation of the face.

#### `face/RegisterFaceScreen.kt`
Purpose:
- enrollment flow for registering the user's face
- collects 5 capture steps for better quality and more stable embeddings

Enrollment steps:
1. look straight ahead
2. turn right
3. turn left
4. blink eyes
5. look straight ahead again

Important logic:
- each valid face capture produces one embedding using `FaceNetHelper`
- all embeddings are stored in a `mutableStateListOf<FloatArray>()`
- after enough valid captures, the app averages them and normalizes them
- the final averaged embedding is saved to Firestore using `FaceEmbeddingRepository.saveFaceEmbedding()`

Saved data:
- `faceEmbedding`: vector of 192 floats
- `faceEnrolledAt`: timestamp
- `email`: user email

#### `face/LoginFaceScreen.kt`
Purpose:
- face login verification flow
- this is used when the user chooses face authentication instead of entering password manually

Important logic:
1. open front camera
2. wait for a stable frontal face with eyes open
3. crop and run `FaceNetHelper.getEmbedding(croppedBitmap)`
4. fetch stored face embedding by the user's email from Firestore
5. normalize both embeddings
6. compute cosine similarity
7. if similarity is above threshold (roughly 0.65), authenticate using stored encrypted credentials

If recognition fails several times, the app falls back to normal login.

#### `face/EnrollmentUI.kt`
Purpose:
- custom UI for the enrollment experience
- includes the circular face capture layout and progress rendering
- not the direct ML logic, but the visual experience around the face capture

#### `data/AuthRepository.kt`
Purpose:
- Firebase Authentication wrapper
- handles `signInWithEmailAndPassword` and `createUserWithEmailAndPassword`
- exposes helpers like `isLoggedIn()`, `getCurrentUserId()`, `getCurrentUserEmail()`, and `logout()`

#### `data/EncryptedCredentialStore.kt`
Purpose:
- stores the email and password in encrypted Android shared preferences
- uses `EncryptedSharedPreferences` with Android Keystore
- keeps the credentials on-device and not in plain text

This is used for face-login fallback: once face recognition succeeds, the app reads the local encrypted credentials and performs Firebase sign-in.

#### `data/FaceEmbeddingRepository.kt`
Purpose:
- Firestore data access layer for face embeddings
- stores and retrieves face vectors
- also handles faceAuthToken logic for some face auth workflows

Important fields:
- `users/{uid}` document
- `faceEmbedding` array
- `faceEnrolledAt` timestamp
- `email`
- `faceAuthToken`

Important functions:
- `saveFaceEmbedding(uid, embedding, email)`
- `getFaceEmbedding(uid)`
- `getFaceEmbeddingByEmail(email)`
- `generateAndSaveFaceAuthToken(uid)`
- `getFaceAuthTokenByEmail(email)`

#### `data/FirestoreRepository.kt`
Purpose:
- simple Firestore connectivity test helper
- used for checking Firebase connectivity and basic Firestore write/read usage

Not the main face-recognition logic, but part of the Firebase integration baseline.

#### `ui/...`
The `ui` package contains the regular app screens:

- login screen
- signup screen
- home screen
- enrollment success screen

These screens interact with navigation and auth logic but are not directly involved in the embedding pipeline.

---

## 4. Full runtime sequence: from camera to Firestore and back

### A. User signs up

1. `AppNavigation` loads the app and decides the entry screen.
2. `SignupScreen` collects email and password.
3. `AuthRepository.signup()` creates a Firebase user via Firebase Auth.
4. navigation moves to `FACE_ENROLLMENT`.

### B. Face enrollment begins

1. `RegisterFaceScreen` is shown.
2. `FaceCaptureScreen` opens the front camera with `CameraX`.
3. `FaceImageAnalyzer` processes each frame with ML Kit face detection.
4. the app checks:
   - face is detected
   - face is oriented front or in the required direction
   - eyes are open or blinked according to the step
5. a valid face crop is sent to `FaceNetHelper.getEmbedding()`.
6. the model generates a 192-length vector.
7. the vector is added to a list of embeddings.
8. after 5 valid captures, the app uses `EmbeddingMath.averageAndL2Normalize()`.
9. `FaceEmbeddingRepository.saveFaceEmbedding()` writes the embedding to Firestore.

Firestore write path:

`users/{uid}` -> `faceEmbedding` + `email` + `faceEnrolledAt`

### C. User logs in with face recognition

1. user enters email on normal login screen
2. user chooses face-auth option
3. `AppNavigation` sends the email to `FACE_LOGIN`
4. `LoginFaceScreen` opens the camera
5. `FaceCaptureScreen` and `FaceImageAnalyzer` detect the face
6. `FaceNetHelper` computes a live 192-dimension embedding
7. `FaceEmbeddingRepository.getFaceEmbeddingByEmail(email)` fetches the stored embedding from Firestore
8. both vectors are normalized using L2
9. `EmbeddingMath.cosineSimilarity()` calculates the match score
10. if score is high enough, the app loads the encrypted credentials from `EncryptedCredentialStore`
11. `AuthRepository.login(email, password)` signs in to Firebase Auth
12. app navigates to the home screen

### D. What happens in the model

The model is a MobileFaceNet-like embedding extractor loaded from `mobilefacenet.tflite`.

The process is:

- camera frame
- crop face rectangle
- resize to `112x112`
- convert RGB pixels to normalized float tensor
- TensorFlow Lite model inference
- output vector length = `192`
-
This 192-value vector is the face representation. It is not an image; it is a mathematical fingerprint of the face.

---

## 5. Embedding pipeline in detail

### Enrollment pipeline

```text
Camera frame
  -> FaceDetection (ML Kit)
  -> crop face
  -> FaceNetHelper.getEmbedding(bitmap)
  -> FloatArray(192)
  -> collect multiple embeddings
  -> average + L2 normalize
  -> Firestore users/{uid}.faceEmbedding
```

### Login pipeline

```text
Camera frame
  -> FaceDetection (ML Kit)
  -> crop face
  -> FaceNetHelper.getEmbedding(bitmap)
  -> FloatArray(192)
  -> read stored embedding from Firestore by email
  -> L2 normalize live + stored vectors
  -> cosine similarity
  -> threshold check
  -> Firebase auth login if match
```

---

## 6. Firestore data model

The app uses Firestore under the `users` collection.

Example document shape:

```json
{
  "email": "user@example.com",
  "faceEmbedding": [0.25, -0.64, 0.91, ...],
  "faceEnrolledAt": "timestamp",
  "faceAuthToken": "uuid-or-token"
}
```

Important details:
- the embedding is stored as a list of doubles
- the vector length is expected to be exactly 192
- the document is keyed by Firebase user UID (`users/{uid}`)
- a lookup by email is used for the face-login process

---

## 7. Security model

This app uses a layered security design:

- Firebase Authentication for standard email/password auth
- encrypted local storage for credentials via `EncryptedSharedPreferences`
- face embedding stored in Firestore, not the actual image
- embedding comparison happens locally on the device
- actual login uses the password from encrypted local storage only after face verification succeeds

This means the app does not save the original face photo in Firestore, only a face vector.

---

## 8. File summary by package

### `com.akartis.faceauth`
- `MainActivity.kt` — app bootstrap

### `com.akartis.faceauth.navigation`
- `AppNavigation.kt` — route management

### `com.akartis.faceauth.camera`
- `FaceCaptureScreen.kt` — CameraX camera UI and preview
- `FaceImageAnalyzer.kt` — ML Kit face detection, crop, metadata extraction

### `com.akartis.faceauth.face`
- `FaceNetHelper.kt` — TFLite model loader and embedding extractor
- `RegisterFaceScreen.kt` — enrollment flow and Firestore save
- `LoginFaceScreen.kt` — face verification and local auth success flow
- `EnrollmentUI.kt` — enrollment component UI

### `com.akartis.faceauth.ml`
- `EmbeddingMath.kt` — average, normalize, cosine similarity math

### `com.akartis.faceauth.data`
- `AuthRepository.kt` — Firebase Auth wrapper
- `EncryptedCredentialStore.kt` — encrypted credentials
- `FaceEmbeddingRepository.kt` — Firestore embedding CRUD
- `FirestoreRepository.kt` — simple Firebase connection test helper

### `ui/`
- app screens for login, signup, home, success screens and styling

---

## 9. Key design takeaway

The app is a hybrid of:

- Firebase Authentication for regular identity management
- ML Kit for face detection and orientation analysis
- TensorFlow Lite MobileFaceNet for embedding generation
- Firestore for persistent biometric template storage
- Android Keystore encryption for local credential safety

The most important technical chain is:

`front camera -> face detection -> face crop -> TFLite embedding -> Firestore -> cosine similarity -> Firebase login`

That is the central logic of this project.
