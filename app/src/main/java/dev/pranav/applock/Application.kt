package dev.pranav.applock

import android.app.Application
import dev.pranav.applock.services.AppLockService

class AppLockApplication : Application() {
    var appLockServiceInstance: AppLockService? = null
}
