# Privacy

HyperConnect Toolkit is an offline companion application and LSPosed compatibility module.

## Data handled locally

The companion application stores three user-controlled feature switches through LSPosed Remote
Preferences. The module evaluates only the in-memory process, orientation, feature-flag, and
MiLink transaction metadata needed to apply its narrow compatibility rules.

The application may execute four fixed root operations: a root availability probe, launch of one
audited HyperOS settings component, and force-stopping the two audited target packages after user
confirmation. It never accepts a shell command from user input.

## Data not collected

- no `INTERNET` permission;
- no analytics, telemetry, advertising, or remote crash-reporting SDK;
- no account, location, contacts, storage, microphone, or media permission;
- no notification content, application content, credentials, passwords, stream frames, or MiLink
  traffic is read, stored, or transmitted by this project;
- no upload endpoint or background network task.

Android, LSPosed, MiLink, and Xiaomi Mirror may record their own logs. Logs can contain package
names, process names, device information, or framework stack traces; redact them before sharing.

## Removal

Disable the module in LSPosed, restart the target services or reboot, then uninstall the APK.
Companion-app settings are removed with the app; LSPosed may retain module metadata according to
its own lifecycle.
