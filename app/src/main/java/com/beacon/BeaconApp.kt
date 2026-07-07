package com.beacon

import android.app.Application
import org.osmdroid.config.Configuration
import java.io.File

class BeaconApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        val config = Configuration.getInstance()
        config.load(this, getSharedPreferences("osmdroid", MODE_PRIVATE))
        // OSM tile servers require an identifying user agent.
        config.userAgentValue = packageName
        // Keep the tile cache on internal storage: osmdroid's external-storage
        // default fails silently on some devices, rendering a blank grid.
        config.osmdroidBasePath = File(cacheDir, "osmdroid")
        config.osmdroidTileCache = File(config.osmdroidBasePath, "tiles")
        container = AppContainer(this)
    }
}
