package dev.pranav.applock

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.LauncherApps
import android.os.Process
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Helper class to manage app search functionality with optimized performance
 */
class AppSearchManager(private val context: Context) {

    // Loaded app data
    private var allApps: List<ApplicationInfo> = emptyList()
    private var appNameCache: HashMap<ApplicationInfo, String> = HashMap()
    private var prefixIndexCache: HashMap<String, List<ApplicationInfo>> = HashMap()

    /**
     * Load all apps and prepare search indexes
     */
    suspend fun loadApps(): List<ApplicationInfo> {
        return withContext(Dispatchers.IO) {
            val launcherApps =
                context.getSystemService(Context.LAUNCHER_APPS_SERVICE) as LauncherApps

            val apps = launcherApps.getActivityList(null, Process.myUserHandle())
                .mapNotNull { it.applicationInfo }
                .filter { it.enabled && it.packageName != context.packageName }

            // Pre-compute app names with HashMap for better performance
            val nameCache =
                apps.associateWithTo(HashMap()) { app ->
                    app.loadLabel(context.packageManager).toString().lowercase()
                }

            // Create prefix index for faster search using HashMap
            val prefixCache = HashMap<String, MutableList<ApplicationInfo>>()

            nameCache.forEach { (app, appName) ->
                if (appName.isNotEmpty()) {
                    // Index first 1-3 characters for quick lookup
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

            // Sort apps by name
            val sortedApps = apps.sortedBy { nameCache[it] }

            // Store results in class properties
            allApps = sortedApps
            appNameCache = nameCache
            // Convert MutableList to List when storing in the prefixIndexCache
            val finalPrefixCache = HashMap<String, List<ApplicationInfo>>(prefixCache.size)
            prefixCache.forEach { (prefix, appList) ->
                finalPrefixCache[prefix] = appList
            }
            prefixIndexCache = finalPrefixCache

            sortedApps
        }
    }

    /**
     * Search apps based on query with optimized performance
     */
    fun searchApps(query: String): List<ApplicationInfo> {
        if (query.isEmpty()) {
            return allApps
        }

        val lowercaseQuery = query.lowercase()

        // Quick lookup for short queries using prefix index
        if (lowercaseQuery.length <= 3 && prefixIndexCache.containsKey(lowercaseQuery)) {
            return prefixIndexCache[lowercaseQuery] ?: emptyList()
        }

        // For longer queries, optimize search space
        val initialSearchSet = if (lowercaseQuery.length > 3 &&
            prefixIndexCache.containsKey(lowercaseQuery.take(3))
        ) {
            prefixIndexCache[lowercaseQuery.take(3)] ?: allApps
        } else {
            allApps
        }

        // Find apps that start with the query (higher priority)
        val startsWithMatches = initialSearchSet.filter { app ->
            appNameCache[app]?.startsWith(lowercaseQuery) == true
        }

        // Only search for contains matches if needed
        val containsMatches = if (startsWithMatches.size < 10 && lowercaseQuery.length > 1) {
            initialSearchSet.filter { app ->
                val name = appNameCache[app] ?: return@filter false
                !name.startsWith(lowercaseQuery) && name.contains(lowercaseQuery)
            }
        } else {
            emptyList()
        }

        // Combine results with priority order
        return startsWithMatches + containsMatches
    }

    /**
     * Get app name (cached for performance)
     */
    fun getAppName(app: ApplicationInfo): String {
        return appNameCache[app] ?: app.loadLabel(context.packageManager).toString()
    }
}
