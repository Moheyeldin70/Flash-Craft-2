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
private const val HARNESS_MARKER_PREFS = "app_check_harness_state"
private const val HARNESS_MARKER_KEY = "has_injected_debug_token"
private const val RECAPTCHA_SITE_KEY_RES = "recaptcha_site_key"

fun configureAppCheck(context: Context, intent: Intent?) {
    try {
        if (FirebaseApp.getApps(context).isEmpty()) {
            FirebaseApp.initializeApp(context)
        }

        val debugToken = if (BuildConfig.DEBUG) {
            intent?.getStringExtra(DEBUG_TOKEN_EXTRA)
        } else {
            null
        }
        val markerPrefs = context.getSharedPreferences(HARNESS_MARKER_PREFS, Context.MODE_PRIVATE)

        if (BuildConfig.DEBUG && !debugToken.isNullOrEmpty()) {
            val persistenceKey = FirebaseApp.getInstance().persistenceKey
            val prefs = context.getSharedPreferences(
                "$DEBUG_STORE_PREFIX$persistenceKey",
                Context.MODE_PRIVATE
            )
            // Must use commit() synchronously so DebugAppCheckProviderFactory reads the secret immediately.
            prefs.edit()
                .putString(DEBUG_SECRET_KEY, debugToken)
                .commit()
            markerPrefs.edit()
                .putBoolean(HARNESS_MARKER_KEY, true)
                .commit()
            Log.d(TAG, "App Check debug secret configured successfully.")

            FirebaseAppCheck.getInstance().installAppCheckProviderFactory(
                DebugAppCheckProviderFactory.getInstance()
            )
            Log.d(TAG, "DebugAppCheckProviderFactory installed.")
            return
        }

        val hasInjectedDebugToken = BuildConfig.DEBUG &&
            markerPrefs.getBoolean(HARNESS_MARKER_KEY, false)

        if (hasInjectedDebugToken) {
            FirebaseAppCheck.getInstance().installAppCheckProviderFactory(
                DebugAppCheckProviderFactory.getInstance()
            )
            Log.d(TAG, "DebugAppCheckProviderFactory installed from previously injected token.")
            return
        }

        val resId = context.resources.getIdentifier(
            RECAPTCHA_SITE_KEY_RES,
            "string",
            context.packageName
        )
        val siteKey = if (resId != 0) context.getString(resId) else ""
        if (siteKey.isNotBlank()) {
            FirebaseAppCheck.getInstance().installAppCheckProviderFactory(
                RecaptchaAppCheckProviderFactory.getInstance(siteKey)
            )
            Log.d(TAG, "RecaptchaAppCheckProviderFactory installed.")
        } else if (BuildConfig.DEBUG) {
            Log.w(TAG, "No debug token or recaptcha_site_key found; falling back to DebugAppCheckProviderFactory.")
            FirebaseAppCheck.getInstance().installAppCheckProviderFactory(
                DebugAppCheckProviderFactory.getInstance()
            )
        } else {
            Log.e(TAG, "recaptcha_site_key string resource not found or blank in package.")
        }
    } catch (e: Exception) {
        Log.e(TAG, "Failed to initialize Firebase App Check", e)
    }
}
