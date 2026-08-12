# Contributing

HyperConnect Toolkit changes vendor-private HyperOS behavior. Narrow scope, failure isolation,
recoverability, and evidence take priority over broad but unverified compatibility.

## Development environment

- JDK 17;
- Android SDK Platform 36;
- a Modern LSPosed API 101+ test environment;
- a recoverable HyperOS device with MiLink and Xiaomi Mirror installed.

Run this before opening a pull request:

```powershell
.\gradlew.bat --no-daemon clean :app:testDebugUnitTest :app:lintDebug :app:assembleDebug
```

## Design rules

1. Hook the narrowest stable boundary that preserves Xiaomi's native transport and lifecycle.
2. Never broaden a tablet-only compatibility rule to remote phones or unrelated processes.
3. Resolve all required private members before registering a feature's hooks.
4. Preserve stock behavior for unknown inputs, incompatible signatures, and failed verification.
5. Do not call an original implementation twice or suppress a second state-changing transport.
6. Keep user preferences, root commands, runtime reports, and vendor reflection in their dedicated
   layers.
7. Add unit tests for pure policy, configuration, or transaction-lifetime changes.

## Evidence required for compatibility changes

- Android/HyperOS and LSPosed versions;
- target package, process, class, and complete member signature;
- rationale that the hook is a shared lifecycle boundary rather than a superficial or application-
  specific workaround;
- expected stock fallback when the signature is absent or inputs are outside scope;
- redacted before/after logs and a regression result for unrelated paths.

Never commit proprietary APKs, decompiled vendor source, full build fingerprints, device serials,
IP addresses, account data, screenshots with private notifications, captured stream content, or
signing material.

## Code and commits

- Java and Kotlin use 4 spaces; Markdown, XML, and YAML use 2 spaces;
- use LF except Windows batch files;
- keep each class focused on one responsibility;
- use short imperative commit subjects;
- describe impact, evidence, and validation in the pull request.

By contributing, you agree that your contribution may be distributed under the Apache License 2.0.
