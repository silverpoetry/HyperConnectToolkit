package com.weichao.hyperconnecttoolkit.platform

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

/** Receives activation evidence from a genuinely hooked target process. */
class HookStatusReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != HookStatusPolicy.ACTION_HOOKS_ACTIVE) return

        val senderPackages = context.packageManager
            .getPackagesForUid(sentFromUid)
            .orEmpty()
            .asList()
        if (!HookStatusPolicy.isTrustedSender(senderPackages)) {
            Log.w(TAG, "Ignored hook status from untrusted uid=$sentFromUid")
            return
        }

        val moduleVersion = intent.getIntExtra(EXTRA_MODULE_VERSION, 0)
        if (moduleVersion != HookStatusPolicy.EXPECTED_MODULE_VERSION) {
            Log.w(TAG, "Ignored stale hook status version=$moduleVersion")
            return
        }

        val sourcePackage = intent.getStringExtra(HookStatusPolicy.EXTRA_SOURCE_PACKAGE)
            ?.takeIf { it in senderPackages }
            ?: return
        ModuleRuntimeState.recordTargetProcessActive(context, sourcePackage)
        Log.i(TAG, "Recorded active hooks from $sourcePackage")
    }

    companion object {
        const val EXTRA_MODULE_VERSION = "module_version"
        private const val TAG = "HyperConnectToolkit"
    }
}
