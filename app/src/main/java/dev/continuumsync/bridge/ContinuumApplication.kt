package dev.continuumsync.bridge

import android.app.Application
import dev.continuumsync.bridge.data.SyncRepository

class ContinuumApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        SyncRepository.init(this)
    }
}
