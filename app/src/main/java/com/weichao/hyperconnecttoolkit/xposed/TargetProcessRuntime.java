package com.weichao.hyperconnecttoolkit.xposed;

import android.annotation.SuppressLint;
import android.app.Application;
import android.app.BroadcastOptions;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

import com.weichao.hyperconnecttoolkit.BuildConfig;
import com.weichao.hyperconnecttoolkit.platform.HookStatusContract;

import java.lang.reflect.Method;

import io.github.libxposed.api.XposedModule;

/** Captures one target process context and reports only successfully installed feature groups. */
final class TargetProcessRuntime {
    private static final String TAG = "HyperConnectToolkit";
    private static final String MODULE_PACKAGE = "com.weichao.hyperconnecttoolkit";
    private static final String RECEIVER_CLASS =
            "com.weichao.hyperconnecttoolkit.platform.HookStatusReceiver";

    private final XposedModule module;
    private final DiagnosticLogger diagnostics;
    private final String sourcePackage;
    private final String sourceProcess;
    private volatile Context applicationContext;
    private int installedCapabilities;
    private int reportedCapabilities;

    TargetProcessRuntime(
            XposedModule module,
            DiagnosticLogger diagnostics,
            String sourcePackage,
            String sourceProcess) {
        this.module = module;
        this.diagnostics = diagnostics;
        this.sourcePackage = sourcePackage;
        this.sourceProcess = sourceProcess;
    }

    @SuppressLint("DiscouragedPrivateApi")
    boolean installLifecycleHook() {
        try {
            Method attach = Application.class.getDeclaredMethod("attach", Context.class);
            attach.setAccessible(true);
            module.hook(attach).intercept(chain -> {
                Object result = chain.proceed();
                if (chain.getArg(0) instanceof Context context) {
                    Context appContext = context.getApplicationContext();
                    applicationContext = appContext != null ? appContext : context;
                    DiagnosticEnvironment.record(diagnostics, applicationContext);
                    reportPendingFeatures();
                }
                return result;
            });
            module.log(Log.INFO, TAG, "Installed target-process lifecycle reporter");
            return true;
        } catch (Throwable error) {
            module.log(Log.ERROR, TAG, "Target-process lifecycle reporter is incompatible", error);
            return false;
        }
    }

    Context applicationContext() {
        return applicationContext;
    }

    synchronized void recordInstalledCapability(int capability) {
        installedCapabilities |= capability & HookStatusContract.ALL_REPORT_BITS;
        reportPendingFeatures();
    }

    private synchronized void reportPendingFeatures() {
        Context context = applicationContext;
        int pendingFeatures = installedCapabilities & ~reportedCapabilities;
        if (context == null || pendingFeatures == 0) {
            return;
        }

        try {
            Intent report = new Intent(HookStatusContract.ACTION_HOOKS_ACTIVE)
                    .setComponent(new ComponentName(MODULE_PACKAGE, RECEIVER_CLASS))
                    .putExtra(HookStatusContract.EXTRA_SOURCE_PACKAGE, sourcePackage)
                    .putExtra(HookStatusContract.EXTRA_SOURCE_PROCESS, sourceProcess)
                    .putExtra(HookStatusContract.EXTRA_MODULE_VERSION, BuildConfig.VERSION_CODE)
                    .putExtra(HookStatusContract.EXTRA_ACTIVE_FEATURES, pendingFeatures)
                    .addFlags(Intent.FLAG_INCLUDE_STOPPED_PACKAGES
                            | Intent.FLAG_RECEIVER_FOREGROUND);
            BroadcastOptions options = BroadcastOptions.makeBasic();
            options.setShareIdentityEnabled(true);
            context.sendBroadcast(report, null, options.toBundle());
            reportedCapabilities |= pendingFeatures;
            module.log(Log.INFO, TAG,
                    "Reported Hook capabilities=" + pendingFeatures + " from " + sourceProcess);
        } catch (Throwable error) {
            module.log(Log.WARN, TAG, "Could not report installed Hook features", error);
        }
    }
}
