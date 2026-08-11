package com.weichao.hyperconnecttoolkit.platform

import android.content.Context
import android.os.SystemClock
import androidx.core.content.edit
import com.weichao.hyperconnecttoolkit.BuildConfig

/**
 * Process-local handshake written by the LSPosed entry when this app is in scope.
 *
 * The module entry resolves this class through the app ClassLoader. This avoids hidden LSPosed
 * database coupling and gives the UI an honest signal that the module was loaded in this process.
 */
object ModuleRuntimeState {
    @Volatile
    private var activatedAtElapsedRealtime: Long = 0L

    @JvmStatic
    fun markActive() {
        activatedAtElapsedRealtime = SystemClock.elapsedRealtime()
    }

    fun recordTargetProcessActive(context: Context, sourcePackage: String) {
        deviceProtectedPreferences(context)
            .edit {
                putInt(KEY_VERSION_CODE, BuildConfig.VERSION_CODE)
                putString(KEY_SOURCE_PACKAGE, sourcePackage)
                putLong(KEY_REPORTED_AT, System.currentTimeMillis())
            }
    }

    fun isActive(context: Context): Boolean =
        activatedAtElapsedRealtime > 0L ||
            deviceProtectedPreferences(context).getInt(KEY_VERSION_CODE, 0) ==
            BuildConfig.VERSION_CODE

    private fun deviceProtectedPreferences(context: Context) =
        context.createDeviceProtectedStorageContext()
            .getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)

    private const val PREFERENCES_NAME = "module_runtime_state"
    private const val KEY_VERSION_CODE = "version_code"
    private const val KEY_SOURCE_PACKAGE = "source_package"
    private const val KEY_REPORTED_AT = "reported_at"
}
