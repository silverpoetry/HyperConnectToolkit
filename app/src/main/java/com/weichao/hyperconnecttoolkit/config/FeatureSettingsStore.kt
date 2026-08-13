@file:android.annotation.SuppressLint("ApplySharedPref", "UseKtx")

package com.weichao.hyperconnecttoolkit.config

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.UUID

/**
 * Owns the app-side mirror of libxposed RemotePreferences.
 *
 * Local storage keeps the UI usable while LSPosed is unavailable. A pending marker makes an
 * offline edit authoritative when the service reconnects; otherwise an existing remote value is
 * authoritative. Injected processes never read this local file.
 */
object FeatureSettingsStore {
    private const val LOCAL_PREFERENCES = "feature_settings_local"
    private const val KEY_REMOTE_WRITE_PENDING = "remote_write_pending"

    private val lock = Any()
    private val mutableConfiguration = MutableStateFlow(FeatureConfiguration())
    val configuration: StateFlow<FeatureConfiguration> = mutableConfiguration.asStateFlow()

    private lateinit var localPreferences: SharedPreferences
    private var remotePreferences: SharedPreferences? = null

    fun initialize(context: Context) {
        synchronized(lock) {
            if (::localPreferences.isInitialized) return
            localPreferences = context.getSharedPreferences(
                LOCAL_PREFERENCES,
                Context.MODE_PRIVATE,
            )
            mutableConfiguration.value = FeatureConfiguration(
                settings = read(localPreferences),
                hasPendingRemoteWrite = localPreferences.getBoolean(
                    KEY_REMOTE_WRITE_PENDING,
                    false,
                ),
            )
        }
    }

    fun bindRemote(preferences: SharedPreferences): Boolean = synchronized(lock) {
        try {
            check(::localPreferences.isInitialized) { "FeatureSettingsStore is not initialized" }
            remotePreferences = preferences

            val pending = localPreferences.getBoolean(KEY_REMOTE_WRITE_PENDING, false)
            val remoteConfigured = FEATURE_KEYS.any(preferences::contains)
            val effective = if (pending || !remoteConfigured) {
                val local = read(localPreferences)
                val remoteSaved = commitSettings(preferences, local)
                check(commitLocal(local, pendingRemoteWrite = !remoteSaved))
                local
            } else {
                read(preferences).also {
                    check(commitLocal(it, pendingRemoteWrite = false))
                }
            }

            mutableConfiguration.value = FeatureConfiguration(
                settings = effective,
                isRemoteAvailable = true,
                hasPendingRemoteWrite = localPreferences.getBoolean(
                    KEY_REMOTE_WRITE_PENDING,
                    false,
                ),
            )
            true
        } catch (_: RuntimeException) {
            remotePreferences = null
            mutableConfiguration.value = mutableConfiguration.value.copy(
                isRemoteAvailable = false,
            )
            false
        }
    }

    fun unbindRemote(preferences: SharedPreferences?) {
        synchronized(lock) {
            if (preferences != null && remotePreferences !== preferences) return
            remotePreferences = null
            mutableConfiguration.value = mutableConfiguration.value.copy(
                isRemoteAvailable = false,
            )
        }
    }

    /** Returns false only when the durable app-local write itself failed. */
    fun setEnabled(feature: Feature, enabled: Boolean): Boolean = synchronized(lock) {
        check(::localPreferences.isInitialized) { "FeatureSettingsStore is not initialized" }
        val updated = mutableConfiguration.value.settings.with(feature, enabled)
        save(updated)
    }

    /** Starts a fresh trace session whenever detailed logging transitions to enabled. */
    fun setDetailedLoggingEnabled(enabled: Boolean): Boolean = synchronized(lock) {
        check(::localPreferences.isInitialized) { "FeatureSettingsStore is not initialized" }
        val current = mutableConfiguration.value.settings
        val updated = current.copy(
            detailedLogging = enabled,
            diagnosticSessionId = if (enabled && !current.detailedLogging) {
                UUID.randomUUID().toString().replace("-", "").take(12)
            } else {
                current.diagnosticSessionId
            },
        )
        save(updated)
    }

    private fun save(updated: FeatureSettings): Boolean {
        val localSaved = commitLocal(updated, pendingRemoteWrite = true)
        if (!localSaved) return false

        val remote = remotePreferences
        val remoteSaved = remote != null && runCatching { commitSettings(remote, updated) }
            .getOrDefault(false)
        val fullySynchronized = remoteSaved && commitLocal(updated, pendingRemoteWrite = false)
        mutableConfiguration.value = FeatureConfiguration(
            settings = updated,
            isRemoteAvailable = remote != null,
            hasPendingRemoteWrite = !fullySynchronized,
        )
        return true
    }

    private fun read(preferences: SharedPreferences): FeatureSettings = FeatureSettings(
        crossDeviceNotification = preferences.getBoolean(
            FeaturePreferences.KEY_CROSS_DEVICE_NOTIFICATION,
            FeaturePreferences.DEFAULT_CROSS_DEVICE_NOTIFICATION_ENABLED,
        ),
        portraitStreaming = preferences.getBoolean(
            FeaturePreferences.KEY_PORTRAIT_STREAMING,
            FeaturePreferences.DEFAULT_PORTRAIT_STREAMING_ENABLED,
        ),
        miLinkMultiChannel = preferences.getBoolean(
            FeaturePreferences.KEY_MILINK_MULTI_CHANNEL,
            FeaturePreferences.DEFAULT_MILINK_MULTI_CHANNEL_ENABLED,
        ),
        detailedLogging = preferences.getBoolean(
            FeaturePreferences.KEY_DETAILED_LOGGING,
            FeaturePreferences.DEFAULT_DETAILED_LOGGING_ENABLED,
        ),
        diagnosticSessionId = preferences.getString(
            FeaturePreferences.KEY_DIAGNOSTIC_SESSION_ID,
            "",
        ).orEmpty(),
    )

    private fun commitLocal(
        settings: FeatureSettings,
        pendingRemoteWrite: Boolean,
    ): Boolean = writeSettings(localPreferences.edit(), settings)
        .putBoolean(KEY_REMOTE_WRITE_PENDING, pendingRemoteWrite)
        .commit()

    private fun commitSettings(
        preferences: SharedPreferences,
        settings: FeatureSettings,
    ): Boolean = writeSettings(preferences.edit(), settings).commit()

    private fun writeSettings(
        editor: SharedPreferences.Editor,
        settings: FeatureSettings,
    ): SharedPreferences.Editor = editor
        .putBoolean(
            FeaturePreferences.KEY_CROSS_DEVICE_NOTIFICATION,
            settings.crossDeviceNotification,
        )
        .putBoolean(
            FeaturePreferences.KEY_PORTRAIT_STREAMING,
            settings.portraitStreaming,
        )
        .putBoolean(
            FeaturePreferences.KEY_MILINK_MULTI_CHANNEL,
            settings.miLinkMultiChannel,
        )
        .putBoolean(
            FeaturePreferences.KEY_DETAILED_LOGGING,
            settings.detailedLogging,
        )
        .putString(
            FeaturePreferences.KEY_DIAGNOSTIC_SESSION_ID,
            settings.diagnosticSessionId,
        )

    private val FEATURE_KEYS = setOf(
        FeaturePreferences.KEY_CROSS_DEVICE_NOTIFICATION,
        FeaturePreferences.KEY_PORTRAIT_STREAMING,
        FeaturePreferences.KEY_MILINK_MULTI_CHANNEL,
        FeaturePreferences.KEY_DETAILED_LOGGING,
        FeaturePreferences.KEY_DIAGNOSTIC_SESSION_ID,
    )
}
