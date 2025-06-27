package dev.pranav.applock

import android.app.Application
import dev.pranav.applock.data.repository.AppLockRepository

class AppLockApplication : Application() {
    lateinit var appLockRepository: AppLockRepository

    override fun onCreate() {
        super.onCreate()
        appLockRepository = AppLockRepository(this)
    }
}
