package com.unloop.launcher.data

import android.content.ComponentName
import android.content.Context
import android.content.pm.LauncherApps
import android.os.Handler
import android.os.Looper
import android.os.UserHandle
import android.os.UserManager

data class AppInfo(
    val label: String,
    val packageName: String,
    val component: ComponentName,
    val user: UserHandle,
) {
    /** Stable, unique id for list keys (same app can exist in personal + work profile). */
    val key: String = "${component.flattenToShortString()}#${user.hashCode()}"
}

/** Reads launchable apps through LauncherApps, which also covers work profiles. */
class AppRepository(context: Context) {
    private val launcherApps = context.getSystemService(LauncherApps::class.java)
    private val userManager = context.getSystemService(UserManager::class.java)
    private val selfPackage = context.packageName

    fun loadApps(): List<AppInfo> =
        userManager.userProfiles
            .flatMap { user ->
                launcherApps.getActivityList(null, user).map {
                    AppInfo(
                        label = it.label.toString().trim(),
                        packageName = it.componentName.packageName,
                        component = it.componentName,
                        user = user,
                    )
                }
            }
            .filter { it.packageName != selfPackage }
            .sortedBy { it.label.lowercase() }

    fun launch(app: AppInfo) {
        launcherApps.startMainActivity(app.component, app.user, null, null)
    }

    fun openAppInfo(app: AppInfo) {
        launcherApps.startAppDetailsActivity(app.component, app.user, null, null)
    }

    /** Calls [onChange] whenever an app is installed, removed or updated. Returns an unregister function. */
    fun observe(onChange: () -> Unit): () -> Unit {
        val callback = object : LauncherApps.Callback() {
            override fun onPackageRemoved(packageName: String?, user: UserHandle?) = onChange()
            override fun onPackageAdded(packageName: String?, user: UserHandle?) = onChange()
            override fun onPackageChanged(packageName: String?, user: UserHandle?) = onChange()
            override fun onPackagesAvailable(packageNames: Array<out String>?, user: UserHandle?, replacing: Boolean) = onChange()
            override fun onPackagesUnavailable(packageNames: Array<out String>?, user: UserHandle?, replacing: Boolean) = onChange()
        }
        launcherApps.registerCallback(callback, Handler(Looper.getMainLooper()))
        return { launcherApps.unregisterCallback(callback) }
    }
}
