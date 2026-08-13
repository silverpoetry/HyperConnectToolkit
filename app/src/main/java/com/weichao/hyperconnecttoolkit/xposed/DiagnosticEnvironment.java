package com.weichao.hyperconnecttoolkit.xposed;

import android.annotation.SuppressLint;
import android.accounts.AccountManager;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothManager;
import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.wifi.WifiManager;
import android.os.Build;
import android.provider.Settings;

import com.weichao.hyperconnecttoolkit.BuildConfig;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

/** Captures non-identifying prerequisites and component versions after Application.attach. */
final class DiagnosticEnvironment {
    private static final String[] COMPONENT_PACKAGES = {
            "com.milink.service",
            "com.xiaomi.mirror",
            "com.xiaomi.continuity.sdkapp",
    };

    private DiagnosticEnvironment() {
    }

    static void record(DiagnosticLogger logger, Context context) {
        if (!logger.isEnabled()) return;
        logger.event(
                "runtime_environment",
                "moduleVersion", BuildConfig.VERSION_NAME + "(" + BuildConfig.VERSION_CODE + ")",
                "manufacturer", safe(Build.MANUFACTURER),
                "model", safe(Build.MODEL),
                "device", safe(Build.DEVICE),
                "product", safe(Build.PRODUCT),
                "android", Build.VERSION.RELEASE + "(sdk=" + Build.VERSION.SDK_INT + ")",
                "incremental", safe(Build.VERSION.INCREMENTAL),
                "hyperOs", safe(systemProperty("ro.mi.os.version.name")),
                "hyperOsIncremental", safe(systemProperty("ro.mi.os.version.incremental")));

        PackageManager packageManager = context.getPackageManager();
        for (String packageName : COMPONENT_PACKAGES) {
            recordPackage(logger, packageManager, packageName);
        }

        logger.event(
                "discovery_prerequisites",
                "wifi", wifiState(context),
                "bluetooth", bluetoothState(context),
                "network", networkState(context),
                "airplaneMode", integerSetting(
                        Settings.Global.getInt(
                                context.getContentResolver(),
                                Settings.Global.AIRPLANE_MODE_ON,
                                0)),
                "xiaomiAccount", xiaomiAccountState(context));
    }

    private static void recordPackage(
            DiagnosticLogger logger,
            PackageManager packageManager,
            String packageName) {
        try {
            PackageInfo info = packageManager.getPackageInfo(
                    packageName,
                    PackageManager.PackageInfoFlags.of(0));
            logger.event(
                    "component_version",
                    "package", packageName,
                    "versionName", safe(info.versionName),
                    "versionCode", Long.toString(info.getLongVersionCode()));
        } catch (Throwable error) {
            logger.event(
                    "component_version",
                    "package", packageName,
                    "state", "unavailable",
                    "error", error.getClass().getSimpleName());
        }
    }

    private static String wifiState(Context context) {
        try {
            WifiManager manager = context.getSystemService(WifiManager.class);
            return manager == null ? "unavailable" : Boolean.toString(manager.isWifiEnabled());
        } catch (Throwable error) {
            return "error:" + error.getClass().getSimpleName();
        }
    }

    private static String bluetoothState(Context context) {
        try {
            BluetoothManager manager = context.getSystemService(BluetoothManager.class);
            BluetoothAdapter adapter = manager == null ? null : manager.getAdapter();
            return adapter == null ? "unavailable" : Boolean.toString(adapter.isEnabled());
        } catch (Throwable error) {
            return "error:" + error.getClass().getSimpleName();
        }
    }

    // This code runs inside MiLink, whose declared permission set owns the queried service. The
    // entire observation remains guarded because vendor builds may still restrict the call.
    @SuppressLint("MissingPermission")
    private static String networkState(Context context) {
        try {
            ConnectivityManager manager = context.getSystemService(ConnectivityManager.class);
            if (manager == null) return "unavailable";
            Network network = manager.getActiveNetwork();
            NetworkCapabilities capabilities = manager.getNetworkCapabilities(network);
            if (capabilities == null) return "disconnected";
            List<String> transports = new ArrayList<>();
            if (capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) transports.add("wifi");
            if (capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)) transports.add("cellular");
            if (capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)) transports.add("ethernet");
            if (capabilities.hasTransport(NetworkCapabilities.TRANSPORT_VPN)) transports.add("vpn");
            return String.join(",", transports)
                    + ":validated="
                    + capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED);
        } catch (Throwable error) {
            return "error:" + error.getClass().getSimpleName();
        }
    }

    private static String xiaomiAccountState(Context context) {
        try {
            int count = AccountManager.get(context).getAccountsByType("com.xiaomi").length;
            return count > 0 ? "present" : "absent";
        } catch (Throwable error) {
            return "unavailable:" + error.getClass().getSimpleName();
        }
    }

    // HyperOS version properties are diagnostic hints only; failure falls back to "unknown".
    @SuppressLint("PrivateApi")
    private static String systemProperty(String key) {
        try {
            Class<?> properties = Class.forName("android.os.SystemProperties");
            Method get = properties.getDeclaredMethod("get", String.class, String.class);
            return (String) get.invoke(null, key, "unknown");
        } catch (Throwable ignored) {
            return "unknown";
        }
    }

    private static String integerSetting(int value) {
        return Integer.toString(value);
    }

    private static String safe(String value) {
        if (value == null || value.isBlank()) return "unknown";
        return value.replace('|', '/').replace('\n', '_').replace('\r', '_');
    }
}
