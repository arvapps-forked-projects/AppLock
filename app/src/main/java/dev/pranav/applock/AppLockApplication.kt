package dev.pranav.applock

import android.app.Application
import dev.pranav.applock.data.repository.AppLockRepository

class AppLockApplication : Application() {
    lateinit var appLockRepository: AppLockRepository

    companion object {
        private const val TAG = "AppLockApplication"
    }

    override fun onCreate() {
        super.onCreate()
        appLockRepository = AppLockRepository(this)
    }
}
