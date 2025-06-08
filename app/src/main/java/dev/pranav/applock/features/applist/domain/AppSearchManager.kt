package dev.pranav.applock.features.applist.domain

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.LauncherApps
import android.os.Process
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class AppSearchManager(private val context: Context) {

    private var allApps: List<ApplicationInfo> = emptyList()
    private var appNameCache: HashMap<ApplicationInfo, String> = HashMap()
    private var prefixIndexCache: HashMap<String, List<ApplicationInfo>> = HashMap()

    suspend fun loadApps(): List<ApplicationInfo> {
        return withContext(Dispatchers.IO) {
            val launcherApps =
                context.getSystemService(Context.LAUNCHER_APPS_SERVICE) as LauncherApps

            val apps = launcherApps.getActivityList(null, Process.myUserHandle())
                .mapNotNull { it.applicationInfo }
                .filter { it.enabled && it.packageName != context.packageName }

            val nameCache =
                apps.associateWithTo(HashMap()) { app ->
                    app.loadLabel(context.packageManager).toString().lowercase()
                }

            val prefixCache = HashMap<String, MutableList<ApplicationInfo>>()

            nameCache.forEach { (app, appName) ->
                if (appName.isNotEmpty()) {
                    val firstChar = appName.take(1)
                    prefixCache.getOrPut(firstChar) { mutableListOf() }.add(app)

                    if (appName.length >= 2) {
                        val firstTwoChars = appName.take(2)
                        prefixCache.getOrPut(firstTwoChars) { mutableListOf() }.add(app)

                        if (appName.length >= 3) {
                            val firstThreeChars = appName.take(3)
                            prefixCache.getOrPut(firstThreeChars) { mutableListOf() }.add(app)
                        }
                    }
                }
            }

            val sortedApps = apps.sortedBy { nameCache[it] }

            allApps = sortedApps
            appNameCache = nameCache
            val finalPrefixCache = HashMap<String, List<ApplicationInfo>>(prefixCache.size)
            prefixCache.forEach { (prefix, appList) ->
                finalPrefixCache[prefix] = appList
            }
            prefixIndexCache = finalPrefixCache

            sortedApps
        }
    }

    fun searchApps(query: String): List<ApplicationInfo> {
        if (query.isEmpty()) {
            return allApps
        }

        val lowercaseQuery = query.lowercase()

        if (lowercaseQuery.length <= 3 && prefixIndexCache.containsKey(lowercaseQuery)) {
            return prefixIndexCache[lowercaseQuery] ?: emptyList()
        }

        val initialSearchSet = if (lowercaseQuery.length > 3 &&
            prefixIndexCache.containsKey(lowercaseQuery.take(3))
        ) {
            prefixIndexCache[lowercaseQuery.take(3)] ?: allApps
        } else {
            allApps
        }

        val startsWithMatches = initialSearchSet.filter { app ->
            appNameCache[app]?.startsWith(lowercaseQuery) == true
        }

        val containsMatches = if (startsWithMatches.size < 10 && lowercaseQuery.length > 1) {
            initialSearchSet.filter { app ->
                val name = appNameCache[app] ?: return@filter false
                !name.startsWith(lowercaseQuery) && name.contains(lowercaseQuery)
            }
        } else {
            emptyList()
        }

        return startsWithMatches + containsMatches
    }

    fun getAppName(app: ApplicationInfo): String {
        return appNameCache[app] ?: app.loadLabel(context.packageManager).toString()
    }
}

