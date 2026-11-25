package com.example.data.firebase

import android.content.Context
import android.content.Intent
import android.util.Log
import com.example.BuildConfig
import com.google.firebase.FirebaseApp
import com.google.firebase.appcheck.FirebaseAppCheck
import com.google.firebase.appcheck.debug.DebugAppCheckProviderFactory
import com.google.firebase.appcheck.recaptcha.RecaptchaAppCheckProviderFactory

private const val TAG = "AppCheckInit"

private const val DEBUG_TOKEN_EXTRA = "FIREBASE_APPCHECK_DEBUG_TOKEN"
private const val DEBUG_STORE_PREFIX = "com.google.firebase.appcheck.debug.store."
private const val DEBUG_SECRET_KEY = "com.google.firebase.appcheck.debug.DEBUG_SECRET"
private const val META_PREFS_NAME = "atelier_appcheck_meta"
private const val KEY_INTENT_TOKEN_REGISTERED = "intent_token_registered"
private const val RECAPTCHA_SITE_KEY_RES = "recaptcha_site_key"

fun configureAppCheck(context: Context, intent: Intent?) {
    try {
        if (FirebaseApp.getApps(context).isEmpty()) {
            FirebaseApp.initializeApp(context)
        }

        if (BuildConfig.DEBUG) {
            val debugToken = intent?.getStringExtra(DEBUG_TOKEN_EXTRA)
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
                Log.d(TAG, "App Check debug secret configured from launch intent and DebugAppCheckProviderFactory installed.")
            } else if (metaPrefs.getBoolean(KEY_INTENT_TOKEN_REGISTERED, false)) {
                // Previously received a valid registered debug token from the harness on this device/emulator.
                FirebaseAppCheck.getInstance().installAppCheckProviderFactory(
                    DebugAppCheckProviderFactory.getInstance()
                )
                Log.d(TAG, "DebugAppCheckProviderFactory installed using previously registered intent token.")
            } else {
                // Direct APK install on a physical device without a harness debug token:
                // Use reCAPTCHA Enterprise attestation so Firebase AI Logic receives a real valid App Check token
                // instead of an unregistered random debug UUID.
                val installedRecaptcha = installRecaptchaProviderIfAvailable(context)
                if (!installedRecaptcha) {
                    FirebaseAppCheck.getInstance().installAppCheckProviderFactory(
                        DebugAppCheckProviderFactory.getInstance()
                    )
                    Log.w(TAG, "No debug token or reCAPTCHA site key found; fell back to DebugAppCheckProviderFactory.")
                }
            }
        } else {
            installRecaptchaProviderIfAvailable(context)
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
    val appCheck = FirebaseAppCheck.getInstance()
    appCheck.installAppCheckProviderFactory(
        RecaptchaAppCheckProviderFactory.getInstance(siteKey)
    )
    // Pre-warm reCAPTCHA App Check token asynchronously so first Gemini request is immediate.
    appCheck.getAppCheckToken(false)
        .addOnSuccessListener { Log.d(TAG, "App Check token pre-warmed successfully.") }
        .addOnFailureListener { e -> Log.w(TAG, "App Check token pre-warm deferred: ${e.localizedMessage}") }
    Log.d(TAG, "RecaptchaAppCheckProviderFactory installed.")
    return true
}
