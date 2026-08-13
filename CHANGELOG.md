# Changelog

All notable changes to this project are documented in this file.

## [1.6.0] - 2026-08-13

### Fixed

- Restored fresh phone discovery after MiLink data is cleared by installing the
  `LyraDiscover` compatibility hooks in their actual owner process,
  `com.milink.service:ui`.

### Changed

- Split device discovery and notification-click circulation into independent,
  exact-process Hook groups.
- Cross-device notification status is reported as applied only after both process components have
  installed successfully.

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

[1.6.0]: https://github.com/silverpoetry/HyperConnectToolkit/releases/tag/v1.6.0
[1.5.0]: https://github.com/silverpoetry/HyperConnectToolkit/releases/tag/v1.5.0
