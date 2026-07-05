package com.beacon

import android.content.Context
import com.beacon.data.ChatRepository
import com.beacon.data.IdentityRepository
import com.beacon.data.db.BeaconDatabase
import com.beacon.nearby.NearbyManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/**
 * Manual dependency container — one instance held by [BeaconApp].
 * Deliberately no DI framework: the object graph is small enough to read.
 */
class AppContainer(appContext: Context) {

    /** App-lifetime scope for repositories collecting the mesh. */
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    val identityRepository = IdentityRepository(appContext)

    val database = BeaconDatabase.build(appContext)

    val nearbyManager = NearbyManager(appContext, identityRepository, appScope)

    val chatRepository = ChatRepository(
        dao = database.messageDao(),
        nearby = nearbyManager,
        identity = identityRepository,
        scope = appScope,
    )
}
