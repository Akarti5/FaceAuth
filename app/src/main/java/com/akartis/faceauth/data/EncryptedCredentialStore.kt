package com.akartis.faceauth.data

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

/**
 * Stocke email + mot de passe chiffrés avec Android Keystore (AES-256-GCM).
 *
 * Utilisation :
 *  - Après signup/login réussi → [save]
 *  - Lors du face-login local  → [load] puis signInWithEmailAndPassword
 *  - Lors du logout            → [clear]
 *
 * Sécurité : les données sont chiffrées avec une clé dérivée du Keystore hardware.
 * Elles ne quittent jamais l'appareil.
 */
object EncryptedCredentialStore {

    private const val FILE_NAME  = "faceauth_secure_prefs"
    private const val KEY_EMAIL  = "credential_email"
    private const val KEY_PASS   = "credential_password"

    /**
     * Retourne les SharedPreferences chiffrées.
     * La MasterKey est générée ou récupérée depuis le Keystore hardware.
     */
    private fun getPrefs(context: Context) = EncryptedSharedPreferences.create(
        context,
        FILE_NAME,
        MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build(),
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    /** Sauvegarde email + mot de passe de façon chiffrée. */
    fun save(context: Context, email: String, password: String) {
        getPrefs(context).edit()
            .putString(KEY_EMAIL, email.trim().lowercase())
            .putString(KEY_PASS, password)
            .apply()
    }

    /**
     * Charge les credentials stockés.
     * @return Pair(email, password) ou null si rien n'a été sauvegardé.
     */
    fun load(context: Context): Pair<String, String>? {
        val prefs  = getPrefs(context)
        val email  = prefs.getString(KEY_EMAIL, null)
        val pass   = prefs.getString(KEY_PASS, null)
        return if (email != null && pass != null) email to pass else null
    }

    /** Supprime les credentials (à appeler au logout). */
    fun clear(context: Context) {
        getPrefs(context).edit()
            .remove(KEY_EMAIL)
            .remove(KEY_PASS)
            .apply()
    }

    /** Indique si des credentials sont présents. */
    fun hasCredentials(context: Context): Boolean = load(context) != null
}
