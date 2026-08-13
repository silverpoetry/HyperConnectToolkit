# Changelog

All notable changes to this project are documented in this file.

## [1.6.0-diagnostic.1] - 2026-08-13

### Added

- Opt-in, session-scoped device-discovery tracing through libxposed's official logging interface.
- Decision-level coverage for discovery startup, local NotificationTrans capability, service callbacks, device-type filters, MiLink/Cast capability checks, region filtering, and final candidate acceptance.
- One-tap sanitized export of bounded LSPosed and companion-app logs through Android's document picker.
- Self-contained component snapshot for Android/HyperOS, LSPosed framework/API/scope, MiLink, Xiaomi Mirror, Continuity SDK, and system settings versions.

### Security

- Detailed observers are installed only in the audited target process after explicit opt-in and service restart.
- Diagnostic events are rate-limited and globally bounded; raw device IDs and notification package names are replaced with session-scoped hashes.
- Root log access is one fixed, read-only, size-bounded command. Exported text receives a second privacy-redaction pass.

## [1.5.0] - 2026-08-12

### Added

- One Modern LSPosed module for cross-device notification circulation, portrait application streaming, and MiLink Multi-Channel.
- Independent feature settings with fail-closed target-process registration and validated per-feature runtime reports.
- Native `PIN_APP` notification-click bridge, portrait first-message geometry correction, and official Host-binding recovery for `HostNotBound (215)`.
- Material 3 companion UI, hidden system circulation-settings entry point, in-app About page, privacy/security documentation, issue templates, CI, and signed tag-release workflow.

### Changed

- Default configuration enables only cross-device notification circulation.
- Notification feature names and system-settings wording now match the user-facing circulation flow.
- Runtime status no longer performs unused Secure Settings probes.

### Security

- Target-process reports verify sender identity, package, process, module version, feature bit, and current boot before updating the UI.
- Root access is limited to fixed commands for a probe, one audited system activity, and two explicit target-service stops.
- Incompatible reflection bindings preserve the stock system path rather than registering a partial hook group.

### Validation

- Unit tests cover settings, status policy, feature masks, notification transaction lifetime, orientation policy, and Host-binding/channel policy.
- `testDebugUnitTest`, `lintDebug`, `assembleDebug`, and `git diff --check` pass before the initial public publication.

[1.6.0-diagnostic.1]: https://github.com/silverpoetry/HyperConnectToolkit/releases/tag/v1.6.0-diagnostic.1
[1.5.0]: https://github.com/silverpoetry/HyperConnectToolkit/releases/tag/v1.5.0
