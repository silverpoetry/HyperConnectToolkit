# Architecture decision record

## 1. Module and application in one APK

The APK has two execution boundaries. The normal application process owns the Material 3 UI and diagnostics. LSPosed loads `HyperConnectModule` only into the three static scopes listed in `META-INF/xposed/scope.list`. The UI never links against libxposed at runtime because the API dependency is `compileOnly`.

After the functional Mirror hooks load, the module hooks the private-final `Application.attach(Context)` lifecycle boundary and sends one explicit versioned broadcast to `HookStatusReceiver`. `attach` is invoked by `LoadedApk` for every application and cannot be skipped by a custom `Instrumentation`, so no timer or polling fallback is required. Android 16 does not expose a broadcast sender identity by default, so the target process opts in with `BroadcastOptions.setShareIdentityEnabled(true)`. The receiver is additionally protected by the system-only `MANAGE_ACTIVITY_TASKS` permission, resolves all packages for the original sending UID, and accepts only Mirror or MiLink identities before persisting device-protected activation evidence. A self-process ClassLoader handshake remains as a compatibility fallback; it is not authoritative because some LSPosed builds intentionally skip injecting a module into its own application process. This design reports the real target scope without reading or mutating LSPosed's private database.

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

The tablet's generic sub-screen activity does not perform the phone-only trust refresh. At the shared session-open coordinator, the module calls the existing `RemoteTrustManager.m0(deviceId)` immediately before the first open request, matching the ordering in Xiaomi's phone mirror activities and restoring password and trust checks rather than bypassing authentication.

## 4. Portrait stream establishment

Changing `SENSOR_LANDSCAPE` to `FULL_SENSOR` fixes the Activity window but does not by itself configure the phone encoder. The stock generic tablet sink treats notification reason 17 as an ordinary full-screen mirror and sends `ScreenActionMessage`, which has no width or height. By contrast, pinned-app reason 9 takes the application-screen branch and sends `RequestAppScreenMessage` with window width, height and density before the remote display exists.

The module corrects this at the shared `j4.h#a(...)` session-open policy boundary. For reason 17 only, it selects the existing application-screen branch and disables the stock portrait-to-landscape bounds swap. Xiaomi's own coordinator then reads `WindowMetrics`, builds the native `RequestAppScreenMessage`, and sends it through the stock transport. Landscape sessions retain landscape bounds naturally; portrait sessions carry portrait bounds in their first request.

Consequently there are no hooks on remote-display creation, first frame, authentication visibility, or the later `g.k1(...)` resize sender, and no delayed retries. The framework `Activity.setRequestedOrientation` interception is additionally guarded by `com.xiaomi.mirror.sink.g.isInstance(activity)`, so other Mirror activities preserve their stock orientation policy.

## 5. Frame-rate initialization

The 120 Hz policy is applied at object construction boundaries: `VirtualDisplayConfig.Builder.build()`, Mirror's `r3.J.Builder` frame-rate setter, and the codec capability initializer. Existing configuration getters are never rewritten and all non-60-fps values are preserved. This leaves low-tier 30-fps capability decisions intact while upgrading the ROM's explicit 60-fps ceiling.

## 6. Failure isolation

Every hook installer catches reflection or signature failure independently and logs one actionable tag. Hook bodies fall back to `chain.proceed()` whenever input validation or reflection fails. The module declares `exceptionMode=protective` so an incompatible vendor update favors the stock process over a crash.
