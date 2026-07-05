package com.beacon

import android.app.Application
import org.osmdroid.config.Configuration

class BeaconApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        // OSM tile servers require an identifying user agent.
        Configuration.getInstance().userAgentValue = packageName
        container = AppContainer(this)
    }
}
