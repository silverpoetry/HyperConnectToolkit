package com.weichao.hyperconnecttoolkit.app

import android.app.Application
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.SharedPreferences
import android.os.UserManager
import com.weichao.hyperconnecttoolkit.config.FeaturePreferences
import com.weichao.hyperconnecttoolkit.config.FeatureSettingsStore
import com.weichao.hyperconnecttoolkit.diagnostics.XposedFrameworkStore
import com.weichao.hyperconnecttoolkit.platform.HookRuntimeStore
import io.github.libxposed.service.XposedService
import io.github.libxposed.service.XposedServiceHelper
import java.util.concurrent.atomic.AtomicBoolean

/** Owns the companion application's connection to the official libxposed service. */
class HyperConnectApplication : Application(), XposedServiceHelper.OnServiceListener {
    private val userServicesInitialized = AtomicBoolean(false)

    @Volatile
    private var service: XposedService? = null

    @Volatile
    private var remotePreferences: SharedPreferences? = null

    private val userUnlockedReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action != Intent.ACTION_USER_UNLOCKED) return
            unregisterReceiver(this)
            initializeUserServices()
        }
    }

    override fun onCreate() {
        super.onCreate()
        HookRuntimeStore.initialize(this)
        val userManager = getSystemService(UserManager::class.java)
        if (userManager.isUserUnlocked) {
            initializeUserServices()
        } else {
            registerReceiver(
                userUnlockedReceiver,
                IntentFilter(Intent.ACTION_USER_UNLOCKED),
                RECEIVER_NOT_EXPORTED,
            )
        }
    }

    private fun initializeUserServices() {
        if (!userServicesInitialized.compareAndSet(false, true)) return
        FeatureSettingsStore.initialize(this)
        XposedServiceHelper.registerListener(this)
    }

    override fun onServiceBind(boundService: XposedService) {
        service = boundService
        XposedFrameworkStore.bind(boundService)
        val previousPreferences = remotePreferences
        val preferences = runCatching {
            boundService.getRemotePreferences(FeaturePreferences.GROUP)
        }.getOrNull()
        val synchronized = preferences != null && FeatureSettingsStore.bindRemote(preferences)
        remotePreferences = preferences.takeIf { synchronized }
        if (!synchronized) {
            FeatureSettingsStore.unbindRemote(previousPreferences)
        }
    }

    override fun onServiceDied(deadService: XposedService) {
        if (service !== deadService) return
        service = null
        XposedFrameworkStore.unbind()
        val preferences = remotePreferences
        remotePreferences = null
        FeatureSettingsStore.unbindRemote(preferences)
    }
}
