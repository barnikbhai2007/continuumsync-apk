package dev.continuumsync.bridge

import android.app.Application
import dev.continuumsync.bridge.data.SyncRepository
import dev.continuumsync.bridge.host.EmbeddedSyncServer

class ContinuumApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        EmbeddedSyncServer.init(this)
        SyncRepository.init(this)
    }
}
