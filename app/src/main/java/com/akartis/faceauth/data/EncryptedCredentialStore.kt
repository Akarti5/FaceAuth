package com.akartis.faceauth.data

import android.content.Context
import android.util.Log
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

    @Volatile
    private var prefsInstance: android.content.SharedPreferences? = null

    private fun getPrefs(context: Context): android.content.SharedPreferences {
        return prefsInstance ?: synchronized(this) {
            prefsInstance ?: try {
                EncryptedSharedPreferences.create(
                    context.applicationContext,
                    FILE_NAME,
                    MasterKey.Builder(context.applicationContext)
                        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                        .build(),
                    EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                    EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
                ).also { prefsInstance = it }
            } catch (e: Exception) {
                Log.w("EncryptedCredentialStore", "Encrypted prefs init failed — wiping and recreating", e)
                try {
                    context.deleteSharedPreferences(FILE_NAME)
                } catch (ex: Exception) {
                    Log.w("EncryptedCredentialStore", "Failed to delete prefs file", ex)
                }
                EncryptedSharedPreferences.create(
                    context.applicationContext,
                    FILE_NAME,
                    MasterKey.Builder(context.applicationContext)
                        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                        .build(),
                    EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                    EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
                ).also { prefsInstance = it }
            }
        }
    }

    /** Sauvegarde email + mot de passe de façon chiffrée. */
    fun save(context: Context, email: String, password: String) {
        getPrefs(context).edit()
            .putString(KEY_EMAIL, email.trim().lowercase())
            .putString(KEY_PASS, password)
            .commit()
    }

    /**
     * Charge les credentials stockés.
     * @return Pair(email, password) ou null si rien n'a été sauvegardé.
     */
    fun load(context: Context): Pair<String, String>? {
        val prefs  = getPrefs(context)
        val email  = prefs.getString(KEY_EMAIL, null)
        val pass   = prefs.getString(KEY_PASS, null)
        return if (!email.isNullOrBlank() && !pass.isNullOrBlank()) email to pass else null
    }

    /** Supprime les credentials. */
    fun clear(context: Context) {
        getPrefs(context).edit()
            .remove(KEY_EMAIL)
            .remove(KEY_PASS)
            .commit()
    }

    /** Indique si des credentials sont présents. */
    fun hasCredentials(context: Context): Boolean = load(context) != null
}
