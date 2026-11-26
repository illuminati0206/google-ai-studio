package com.example.data.firebase

import android.content.Context
import android.content.Intent
import android.util.Log
import com.example.BuildConfig
import com.google.firebase.FirebaseApp
import com.google.firebase.appcheck.FirebaseAppCheck
import com.google.firebase.appcheck.debug.DebugAppCheckProviderFactory
import com.google.firebase.appcheck.recaptcha.RecaptchaAppCheckProviderFactory
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.tasks.await

private const val TAG = "AppCheckInit"

private const val DEBUG_TOKEN_EXTRA = "FIREBASE_APPCHECK_DEBUG_TOKEN"
private const val DEBUG_STORE_PREFIX = "com.google.firebase.appcheck.debug.store."
private const val DEBUG_SECRET_KEY = "com.google.firebase.appcheck.debug.DEBUG_SECRET"
private const val META_PREFS_NAME = "atelier_appcheck_meta"
private const val KEY_INTENT_TOKEN_REGISTERED = "intent_token_registered"
private const val RECAPTCHA_SITE_KEY_RES = "recaptcha_site_key"

private val providerInstalled = AtomicBoolean(false)

fun configureAppCheck(context: Context, intent: Intent?) {
    try {
        if (FirebaseApp.getApps(context).isEmpty()) {
            FirebaseApp.initializeApp(context)
        }

        val debugToken = if (BuildConfig.DEBUG) intent?.getStringExtra(DEBUG_TOKEN_EXTRA) else null

        // Avoid re-installing the App Check provider on Activity recreation / onNewIntent
        // unless a new debug token extra was explicitly delivered by the test harness.
        if (providerInstalled.get() && debugToken.isNullOrEmpty()) {
            return
        }

        if (BuildConfig.DEBUG) {
            val metaPrefs = context.getSharedPreferences(META_PREFS_NAME, Context.MODE_PRIVATE)

            if (!debugToken.isNullOrEmpty()) {
                val persistenceKey = FirebaseApp.getInstance().persistenceKey
                val prefs = context.getSharedPreferences(
                    "$DEBUG_STORE_PREFIX$persistenceKey",
                    Context.MODE_PRIVATE
                )
                // Synchronous commit() ensures the debug secret is persisted before DebugAppCheckProviderFactory reads it.
                prefs.edit()
                    .putString(DEBUG_SECRET_KEY, debugToken)
                    .commit()
                metaPrefs.edit()
                    .putBoolean(KEY_INTENT_TOKEN_REGISTERED, true)
                    .commit()

                FirebaseAppCheck.getInstance().installAppCheckProviderFactory(
                    DebugAppCheckProviderFactory.getInstance()
                )
                providerInstalled.set(true)
                Log.d(TAG, "App Check debug secret configured from launch intent and DebugAppCheckProviderFactory installed.")
            } else if (metaPrefs.getBoolean(KEY_INTENT_TOKEN_REGISTERED, false)) {
                FirebaseAppCheck.getInstance().installAppCheckProviderFactory(
                    DebugAppCheckProviderFactory.getInstance()
                )
                providerInstalled.set(true)
                Log.d(TAG, "DebugAppCheckProviderFactory installed using previously registered intent token.")
            } else {
                // Direct APK install on a physical device without a harness debug token:
                // Use reCAPTCHA Enterprise attestation so Firebase AI Logic receives a real valid App Check token.
                val installedRecaptcha = installRecaptchaProviderIfAvailable(context)
                if (!installedRecaptcha) {
                    FirebaseAppCheck.getInstance().installAppCheckProviderFactory(
                        DebugAppCheckProviderFactory.getInstance()
                    )
                    Log.w(TAG, "No debug token or reCAPTCHA site key found; fell back to DebugAppCheckProviderFactory.")
                }
                providerInstalled.set(true)
            }
        } else {
            if (installRecaptchaProviderIfAvailable(context)) {
                providerInstalled.set(true)
            }
        }
    } catch (e: Exception) {
        Log.e(TAG, "Failed to initialize Firebase App Check", e)
    }
}

private fun installRecaptchaProviderIfAvailable(context: Context): Boolean {
    val resId = context.resources.getIdentifier(
        RECAPTCHA_SITE_KEY_RES,
        "string",
        context.packageName
    )
    if (resId == 0) {
        Log.e(TAG, "recaptcha_site_key string resource not found in package.")
        return false
    }
    val siteKey = context.getString(resId)
    if (siteKey.isBlank()) {
        Log.e(TAG, "recaptcha_site_key resource string is blank.")
        return false
    }
    FirebaseAppCheck.getInstance().installAppCheckProviderFactory(
        RecaptchaAppCheckProviderFactory.getInstance(siteKey)
    )
    Log.d(TAG, "RecaptchaAppCheckProviderFactory installed.")
    return true
}

/**
 * Forces a fresh App Check attestation token when a cached/expired token is rejected by the backend.
 */
suspend fun forceRefreshAppCheckToken(): Boolean {
    return try {
        val tokenResult = FirebaseAppCheck.getInstance()
            .getAppCheckToken(/* forceRefresh = */ true)
            .await()
        val valid = tokenResult.token.isNotBlank()
        Log.d(TAG, "Force-refreshed App Check token (valid=$valid).")
        valid
    } catch (e: Exception) {
        Log.w(TAG, "Failed to force-refresh App Check token", e)
        false
    }
}
