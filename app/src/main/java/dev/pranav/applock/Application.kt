package dev.pranav.applock

import android.app.Application

class AppLockApplication : Application() {
    var appLockServiceInstance: AppLockService? = null
}
