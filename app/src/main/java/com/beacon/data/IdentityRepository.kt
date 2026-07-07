package com.beacon.data

import android.content.Context
import androidx.core.content.edit
import com.beacon.domain.AnonymousNames
import com.beacon.domain.IdGen
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Ephemeral identity: the display name and onboarding flag survive restarts
 * (SharedPreferences), but the sessionId is regenerated every app process —
 * that is what makes Beacon identities ephemeral by default.
 */
class IdentityRepository(context: Context) : Identity {

    private val prefs = context.getSharedPreferences("beacon_identity", Context.MODE_PRIVATE)

    /** New every process. */
    override val sessionId: String = IdGen.newSessionId()

    override var displayName: String
        get() = prefs.getString(KEY_DISPLAY_NAME, "") ?: ""
        set(value) = prefs.edit { putString(KEY_DISPLAY_NAME, value.trim()) }

    var onboardingComplete: Boolean
        get() = prefs.getBoolean(KEY_ONBOARDING_COMPLETE, false)
        set(value) = prefs.edit { putBoolean(KEY_ONBOARDING_COMPLETE, value) }

    private val _anonymous = MutableStateFlow(prefs.getBoolean(KEY_ANONYMOUS, false))
    val anonymous: StateFlow<Boolean> = _anonymous.asStateFlow()

    fun setAnonymous(value: Boolean) {
        _anonymous.value = value
        prefs.edit { putBoolean(KEY_ANONYMOUS, value) }
    }

    override val effectiveName: String
        get() = if (_anonymous.value || displayName.isBlank()) {
            // Blank names would break endpointInfo parsing on peers; users who
            // skipped naming stay presentable via their anonymous handle.
            AnonymousNames.forSession(sessionId)
        } else {
            displayName
        }

    fun endpointInfo(): String = IdGen.encodeEndpointInfo(sessionId, effectiveName)

    var batteryPromptDismissed: Boolean
        get() = prefs.getBoolean(KEY_BATTERY_PROMPT, false)
        set(value) = prefs.edit { putBoolean(KEY_BATTERY_PROMPT, value) }

    private companion object {
        const val KEY_DISPLAY_NAME = "display_name"
        const val KEY_ONBOARDING_COMPLETE = "onboarding_complete"
        const val KEY_ANONYMOUS = "anonymous"
        const val KEY_BATTERY_PROMPT = "battery_prompt_dismissed"
    }
}
