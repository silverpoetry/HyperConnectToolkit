package com.weichao.hyperconnecttoolkit.platform

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

/** Receives activation evidence from a genuinely hooked target process. */
class HookStatusReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != HookStatusContract.ACTION_HOOKS_ACTIVE) return

        val senderUid = sentFromUid
        if (senderUid < 0) {
            Log.w(TAG, "Rejected Hook report without sender identity")
            return
        }
        val senderPackages = context.packageManager
            .getPackagesForUid(senderUid)
            .orEmpty()
            .asList()
        val report = HookStatusPolicy.validate(
            senderPackages = senderPackages,
            sourcePackage = intent.getStringExtra(HookStatusContract.EXTRA_SOURCE_PACKAGE),
            sourceProcess = intent.getStringExtra(HookStatusContract.EXTRA_SOURCE_PROCESS),
            moduleVersion = intent.getIntExtra(HookStatusContract.EXTRA_MODULE_VERSION, 0),
            reportedFeatureMask = intent.getIntExtra(
                HookStatusContract.EXTRA_ACTIVE_FEATURES,
                0,
            ),
        )
        if (report == null) {
            Log.w(TAG, "Rejected invalid Hook report from uid=$senderUid")
            return
        }
        HookRuntimeStore.recordAppliedFeatures(context, report.featureMask)
        Log.i(TAG, "Recorded Hook features=${report.featureMask} from ${report.target.processName}")
    }

    companion object {
        private const val TAG = "HyperConnectToolkit"
    }
}
