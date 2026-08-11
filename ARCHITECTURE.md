# Architecture decision record

## 1. Module and application in one APK

The APK has two execution boundaries. The normal application process owns the Material 3 UI and diagnostics. LSPosed loads `HyperConnectModule` only into the three static scopes listed in `META-INF/xposed/scope.list`. The UI never links against libxposed at runtime because the API dependency is `compileOnly`.

After all functional Mirror hooks load, the module waits for `Instrumentation.callApplicationOnCreate` to return before sending an explicit versioned broadcast to `HookStatusReceiver`; bounded delayed retries cover vendor Instrumentation variants. Android 16 does not expose a broadcast sender identity by default, so the target process opts in with `BroadcastOptions.setShareIdentityEnabled(true)`. The receiver is additionally protected by the system-only `MANAGE_ACTIVITY_TASKS` permission, resolves all packages for the original sending UID, and accepts only Mirror or MiLink identities before persisting device-protected activation evidence. A self-process ClassLoader handshake remains as a compatibility fallback; it is not authoritative because some LSPosed builds intentionally skip injecting a module into its own application process. This design reports the real target scope without reading or mutating LSPosed's private database.

## 2. Least-privilege root boundary

`RootShell` accepts `RootCommand`, an enum whose command strings are compile-time constants. There is no public arbitrary-shell method and no user input is interpolated. Root is used for:

- a read-only UID probe;
- two read-only `Settings.Secure` fallbacks;
- launching one audited HyperOS component if direct launch is denied;
- force-stopping the two explicit scope packages after confirmation.

Unit tests lock down this command set and the hidden component target.

## 3. Cross-device notification compatibility

`LyraDiscover.isCastServiceSupport(deviceId)` is overridden only when `deviceId` equals MiLink's own local networking device ID. This is intentionally narrower than returning `true` globally: remote capability negotiation remains stock.

For a notification click, `G3.g.v(Bundle)` receives the same `deviceId` and application package used by the stock phone implementation. On a tablet, the module invokes the existing sink manager with reason 17 and returns the same success bundle. Missing or empty extras always fall through to stock behavior.

The tablet's generic sub-screen activity does not perform the phone-only trust refresh. The module calls the existing `RemoteTrustManager.m0(deviceId)` before the original connection callback continues, restoring password and trust checks rather than bypassing authentication.

## 4. Portrait stream convergence

Changing `SENSOR_LANDSCAPE` to `FULL_SENSOR` fixes the Activity window but does not guarantee the phone encoder dimensions. If the tablet launches while already portrait, Android has no configuration transition to report.

The module therefore reuses Mirror's own `g.k1(orientation, force)` resize sender at three lifecycle points:

1. remote display creation, after screen ID and transport assignment;
2. decoder first-frame display, as a compatibility fallback;
3. `g.u1(false)`, the common path where authentication UI exits and the unlock message is sent.

The third point fixes the observed race: authentication can restore the initial landscape encoder after the first-frame callback. A bounded 180/700/1600 ms sequence converges after message ordering settles. Every attempt first verifies both resource orientation and actual window bounds are portrait, and the stock resize call is idempotent.

## 5. Failure isolation

Every hook installer catches reflection or signature failure independently and logs one actionable tag. Hook bodies fall back to `chain.proceed()` whenever input validation or reflection fails. The module declares `exceptionMode=protective` so an incompatible vendor update favors the stock process over a crash.
