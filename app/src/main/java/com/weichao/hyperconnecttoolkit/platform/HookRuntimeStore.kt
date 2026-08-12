package com.weichao.hyperconnecttoolkit.platform

import android.content.Context
import android.content.SharedPreferences
import android.provider.Settings
import androidx.core.content.edit
import com.weichao.hyperconnecttoolkit.BuildConfig
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Durable source of truth for target-process Hook reports.
 *
 * Evidence is accepted only for the current APK version and Android boot. This prevents a report
 * from a previous module build or previous boot from being presented as a currently loaded Hook.
 */
object HookRuntimeStore {
    private val lock = Any()
    private val mutableState = MutableStateFlow(HookRuntimeState())
    val state: StateFlow<HookRuntimeState> = mutableState.asStateFlow()

    private lateinit var preferences: SharedPreferences
    private var bootCount: Int = INVALID_BOOT_COUNT

    private val preferenceListener =
        SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key in STATUS_KEYS) publishCurrentState()
        }

    fun initialize(context: Context) {
        synchronized(lock) {
            if (::preferences.isInitialized) return
            val deviceContext = context.createDeviceProtectedStorageContext()
            bootCount = Settings.Global.getInt(
                deviceContext.contentResolver,
                Settings.Global.BOOT_COUNT,
                INVALID_BOOT_COUNT,
            )
            preferences = deviceContext.getSharedPreferences(
                PREFERENCES_NAME,
                Context.MODE_PRIVATE,
            )
            preferences.registerOnSharedPreferenceChangeListener(preferenceListener)
            mutableState.value = readLocked()
        }
    }

    fun recordAppliedFeatures(context: Context, featureMask: Int) {
        initialize(context)
        val sanitizedMask = featureMask and HookStatusContract.ALL_FEATURES
        if (sanitizedMask == 0 || bootCount == INVALID_BOOT_COUNT) return

        synchronized(lock) {
            val previousMask = readLocked().appliedFeatureMask
            preferences.edit(commit = true) {
                putInt(KEY_VERSION_CODE, BuildConfig.VERSION_CODE)
                putInt(KEY_BOOT_COUNT, bootCount)
                putInt(KEY_APPLIED_FEATURES, previousMask or sanitizedMask)
                putLong(KEY_REPORTED_AT, System.currentTimeMillis())
            }
            mutableState.value = readLocked()
        }
    }

    /** Clears positive evidence after both scoped target packages have been force-stopped. */
    fun clear(context: Context) {
        initialize(context)
        synchronized(lock) {
            preferences.edit(commit = true) { clear() }
            mutableState.value = HookRuntimeState()
        }
    }

    private fun publishCurrentState() {
        synchronized(lock) {
            if (::preferences.isInitialized) mutableState.value = readLocked()
        }
    }

    private fun readLocked(): HookRuntimeState {
        if (!::preferences.isInitialized || bootCount == INVALID_BOOT_COUNT) {
            return HookRuntimeState()
        }
        if (
            preferences.getInt(KEY_VERSION_CODE, 0) != BuildConfig.VERSION_CODE ||
            preferences.getInt(KEY_BOOT_COUNT, INVALID_BOOT_COUNT) != bootCount
        ) {
            return HookRuntimeState()
        }
        return HookRuntimeState.fromMask(preferences.getInt(KEY_APPLIED_FEATURES, 0))
    }

    private const val PREFERENCES_NAME = "hook_runtime_status"
    private const val KEY_VERSION_CODE = "version_code"
    private const val KEY_BOOT_COUNT = "boot_count"
    private const val KEY_APPLIED_FEATURES = "applied_features"
    private const val KEY_REPORTED_AT = "reported_at"
    private const val INVALID_BOOT_COUNT = -1

    private val STATUS_KEYS = setOf(
        KEY_VERSION_CODE,
        KEY_BOOT_COUNT,
        KEY_APPLIED_FEATURES,
    )
}
