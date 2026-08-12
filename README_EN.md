# HyperConnect Toolkit

[简体中文](README.md) · [Installation](docs/installation.md) · [Architecture](ARCHITECTURE.md) · [Troubleshooting](docs/troubleshooting.md) · [About](#about)

[![CI](https://github.com/silverpoetry/HyperConnectToolkit/actions/workflows/ci.yml/badge.svg)](https://github.com/silverpoetry/HyperConnectToolkit/actions/workflows/ci.yml)
[![Release](https://img.shields.io/github/v/release/silverpoetry/HyperConnectToolkit?display_name=tag)](https://github.com/silverpoetry/HyperConnectToolkit/releases)
[![License: Apache-2.0](https://img.shields.io/badge/license-Apache--2.0-blue.svg)](LICENSE)

HyperConnect Toolkit is one Modern LSPosed compatibility module and Material 3 companion app for HyperOS tablets. It combines cross-device notification circulation, portrait application streaming, and MiLink Multi-Channel into three independently controlled features, and exposes HyperOS's hidden cross-device circulation settings page.

> [!WARNING]
> This project hooks ROM-private MiLink and Xiaomi Mirror implementations. A changed private signature or incompatible HyperOS build can crash a target service, break circulation, or cause a restart loop. Keep a working root/LSPosed recovery path before enabling it, and disable it before a system update until compatibility is verified again. It does not bypass Xiaomi account, device-trust, password, or system-permission checks.

## Features

- **Unlock cross-device notification circulation**: corrects only the tablet's local discovery prerequisite. Remote devices still negotiate native capabilities. Notification clicks reuse Xiaomi's normal `PIN_APP` circulation path, including connection, authentication, passwords, conflicts, and error handling.
- **Allow portrait app streaming on tablets**: corrects swapped portrait geometry at the initial request-message boundary and allows the target Mirror activity to use the full orientation sensor. Xiaomi's native resize path continues to handle later rotations.
- **MiLink Multi-Channel**: raises AndroidPad's shared-channel limit from one to two and performs official Host binding only after the native `HostNotBound (215)` result. Enable the matching option on the phone before using it.
- **Independent configuration and verified status**: features report an applied state only after their complete hook group is registered in MiLink Notification, Xiaomi Mirror, or MiLink Core. Disabled features register no hooks. A new installation enables notification circulation only.
- **System cross-device circulation settings**: opens the hidden system settings page. A fixed, audited root fallback is used only if the ROM rejects direct launch.

## Requirements

- Android 14 (API 34) or newer;
- Modern LSPosed API 101 or newer;
- HyperOS MiLink (`com.milink.service`) and Xiaomi Mirror (`com.xiaomi.mirror`) installed;
- a recovery path capable of disabling the module and restarting the device;
- a Xiaomi HyperOS tablet implementation matching the current compatibility baseline. Other ROMs, phones, and unverified system versions are not guaranteed to work.

The static module scope contains only MiLink and Xiaomi Mirror. Runtime routing narrows injection further to exactly `com.milink.crossdeviceservice`, `com.milink.service:core`, and `com.xiaomi.mirror`; unrelated processes never read settings or register hooks.

## Installation

1. Download the APK and matching `.sha256` from [Releases](https://github.com/silverpoetry/HyperConnectToolkit/releases), then verify the digest.
2. Install the APK, enable **HyperConnect Toolkit** in LSPosed, and retain its automatically declared MiLink and Xiaomi Mirror static scopes.
3. Reboot, or use “Restart services” in the app to reload both target services.
4. Leave “Unlock cross-device notification circulation” enabled first and verify notification reception, authentication, and circulation.
5. Enable portrait streaming or Multi-Channel only when needed; restart services and check each feature's reported application state.
6. Disable old HyperNotify, HyperconnectRotation, and MiLink Multi-Channel modules to prevent multiple modules from hooking the same private member.

Loaded target processes do not replace old hooks automatically after a module-code upgrade. Restart the services or reboot. A feature-toggle change also requires a target-service restart so a new process can register the selected hook set. See the [installation guide](docs/installation.md) for upgrade and removal details.

## How it works

```text
Material 3 companion UI
        │  Remote Preferences / constrained root boundary
        ▼
Modern LSPosed static scope
        │  exact package and process routing
        ▼
HyperConnectModule
        ├── MiLink Notification: discovery and notification-click PIN_APP bridge
        ├── Xiaomi Mirror: portrait first-frame geometry and activity orientation policy
        └── MiLink Core: AndroidPad channel policy and official Host-binding repair
```

Each feature resolves its complete private-member set before registering hooks. Reflection failures, unknown inputs, and non-matching cases retain the stock system path. See the [architecture document](ARCHITECTURE.md) for boundary, runtime-report validation, transaction-lifetime, and failure-isolation details.

## Limitations

- This is not a generic cross-device streaming implementation; it targets only audited HyperOS MiLink and Xiaomi Mirror paths.
- Notification and application circulation still require the same account, discovery, network, Bluetooth/WLAN state, device trust, and Xiaomi's native authentication.
- A ROM update can change obfuscated symbols or message layouts. An affected feature should remain inactive rather than guessing.
- Multi-Channel changes only the tablet-channel limit to two; it does not guarantee concurrent circulation for every phone, account, or network.
- Uninstalling the APK does not modify accounts, device trust, notification content, or system circulation settings.

## Build

JDK 17 and Android SDK Platform 36 are required:

```powershell
.\gradlew.bat --no-daemon clean :app:testDebugUnitTest :app:lintDebug :app:assembleDebug
```

The debug APK is written to `app/build/outputs/apk/debug/app-debug.apk`. GitHub Actions builds signed Release APKs and SHA-256 files from a tag with repository Secrets. See [release signing](docs/release-signing.md) for the local equivalent.

## About

The in-app About page shows the current version, Apache License 2.0, and this GitHub repository: <https://github.com/silverpoetry/HyperConnectToolkit>. The link is opened by the system browser. The app requests no network permission and includes no telemetry or remote crash reporting.

## Documentation and contributing

- [Installation, upgrades, and removal](docs/installation.md)
- [Troubleshooting and log collection](docs/troubleshooting.md)
- [Architecture and compatibility policy](ARCHITECTURE.md)
- [Release signing](docs/release-signing.md)
- [Contributing](CONTRIBUTING.md)
- [Privacy](PRIVACY.md) and [Security](SECURITY.md)

## License

Licensed under the [Apache License 2.0](LICENSE). See [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md) for direct dependency notices.
