# Security policy

## Supported versions

| Version | Security updates |
|---|---|
| Latest public release | Yes |
| Development and debug builds | Best effort |
| Older private test builds | No |

## Reporting a vulnerability

Use GitHub's private vulnerability reporting for this repository. Do not open a public Issue for a
problem that can crash or boot-loop MiLink/Xiaomi Mirror, bypass an authorization boundary, expose
streamed content, or disclose signing material.

Include the affected version, device and ROM version, LSPosed version, concise reproduction steps,
impact, and the smallest necessary redacted log excerpt. Do not provide proprietary APKs, complete
framework dumps, account data, device serials, IP addresses, Wi-Fi/Bluetooth addresses, passwords,
or signing material. Allow a reasonable remediation window before public disclosure.

## Operational safety

- Keep a tested LSPosed/root recovery path before enabling the module.
- Install only APKs from this repository's Releases and verify the matching SHA-256 file.
- Disable the module before a HyperOS update and revalidate it after private API changes.
- Do not use a debug keystore for a public release.
- Treat an untested MiLink or Xiaomi Mirror implementation as incompatible until it is verified.
