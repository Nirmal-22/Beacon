package com.beacon

import android.content.Context

/**
 * Manual dependency container — one instance held by [BeaconApp].
 * Deliberately no DI framework: the object graph is small enough to read.
 */
class AppContainer(private val appContext: Context)
