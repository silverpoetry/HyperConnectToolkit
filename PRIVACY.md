# Privacy

HyperConnect Toolkit is an offline companion application and LSPosed compatibility module.

## Data handled locally

The companion application stores three user-controlled feature switches, one diagnostic switch,
and a random diagnostic-session label through LSPosed Remote
Preferences. The module evaluates only the in-memory process, orientation, feature-flag, and
MiLink transaction metadata needed to apply its narrow compatibility rules.

The application may execute five fixed root operations: a root availability probe, launch of one
audited HyperOS settings component, and force-stopping the two audited target packages after user
confirmation. The fifth operation reads at most 2 MiB of this module's entries from the LSPosed
daemon log for an explicit user-requested export. It never accepts a shell command from user input.

Detailed diagnostics are disabled by default. When enabled, injected processes use libxposed's
official logging interface to record bounded discovery decisions, non-identifying runtime state,
and component versions. Raw Xiaomi device identifiers and application package names involved in a
notification click are replaced with session-scoped hashes. Notification content is never logged.
The exported report passes through an additional redaction layer for account-like identifiers,
email addresses, IP addresses, MAC addresses, and UUIDs. Export is always initiated by the user and
saved through Android's system document picker; the application has no upload capability.

## Data not collected

- no `INTERNET` permission;
- no analytics, telemetry, advertising, or remote crash-reporting SDK;
- no account, location, contacts, storage, microphone, or media permission;
- no notification content, application content, credentials, passwords, stream frames, or MiLink
  traffic is read, stored, or transmitted by this project;
- no upload endpoint or background network task.

Android, LSPosed, MiLink, and Xiaomi Mirror may record their own separate logs. Do not share logs
exported by other tools without checking them for account names, device identifiers, notification
content, or framework stack traces.

## Removal

Disable the module in LSPosed, restart the target services or reboot, then uninstall the APK.
Companion-app settings are removed with the app; LSPosed may retain module metadata according to
its own lifecycle.
