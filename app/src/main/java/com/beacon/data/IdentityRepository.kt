package com.beacon.data

import android.content.Context
import androidx.core.content.edit
import com.beacon.domain.IdGen

/**
 * Ephemeral identity: the display name and onboarding flag survive restarts
 * (SharedPreferences), but the sessionId is regenerated every app process —
 * that is what makes Beacon identities ephemeral by default.
 */
class IdentityRepository(context: Context) {

    private val prefs = context.getSharedPreferences("beacon_identity", Context.MODE_PRIVATE)

    /** New every process. */
    val sessionId: String = IdGen.newSessionId()

    var displayName: String
        get() = prefs.getString(KEY_DISPLAY_NAME, "") ?: ""
        set(value) = prefs.edit { putString(KEY_DISPLAY_NAME, value.trim()) }

    var onboardingComplete: Boolean
        get() = prefs.getBoolean(KEY_ONBOARDING_COMPLETE, false)
        set(value) = prefs.edit { putBoolean(KEY_ONBOARDING_COMPLETE, value) }

    fun endpointInfo(): String = IdGen.encodeEndpointInfo(sessionId, displayName)

    private companion object {
        const val KEY_DISPLAY_NAME = "display_name"
        const val KEY_ONBOARDING_COMPLETE = "onboarding_complete"
    }
}
