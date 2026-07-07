package com.beacon.data

import android.content.Context
import androidx.core.content.edit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * "You've helped N people" — deliberately private and local-only. A quiet
 * count the owner sees, not a leaderboard anyone can perform for. This is
 * the one thing that intentionally outlives a session: kindness compounds.
 */
class ThanksLedger(context: Context) {

    private val prefs = context.getSharedPreferences("beacon_thanks", Context.MODE_PRIVATE)

    private val _count = MutableStateFlow(prefs.getInt(KEY, 0))
    val count: StateFlow<Int> = _count.asStateFlow()

    fun increment() {
        _count.value += 1
        prefs.edit { putInt(KEY, _count.value) }
    }

    private companion object {
        const val KEY = "helped_count"
    }
}
