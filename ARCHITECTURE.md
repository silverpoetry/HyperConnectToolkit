# Architecture decision record

## 1. Module and application in one APK

The APK has two execution boundaries. The normal application process owns the Material 3 UI and diagnostics. LSPosed loads `HyperConnectModule` only into the two target-package scopes listed in `META-INF/xposed/scope.list`. The hook API remains `compileOnly`; the companion app includes only libxposed's service client so it can edit official `RemotePreferences` without reading LSPosed private storage.

`HyperConnectModule` is intentionally a small composition root. It resolves only three audited process identities—MiLink Notification, MiLink Core and Xiaomi Mirror—reads one immutable configuration snapshot, and delegates to one feature installer. Unrelated processes inside the two package scopes return before reading configuration or registering any Hook. Vendor reflection, thread-confined transaction state, lifecycle reporting and UI persistence live in separate components.

After a complete feature group is installed, `TargetProcessRuntime` hooks the private-final `Application.attach(Context)` lifecycle boundary and sends an explicit, versioned report directly to `HookStatusReceiver`. The receiver has no implicit intent filter and is guarded by the system-signature `INTERACT_ACROSS_USERS` permission held by both audited HyperOS targets. Android 16 sender identity sharing is explicitly enabled; the receiver then verifies the original UID's package set, claimed package, exact process, module version, known bit range and the single feature allowed for that process. A MiLink Notification process therefore cannot accidentally report the MiLink Core feature, even though they share a package UID.

`HookRuntimeStore` persists only the validated feature mask and scopes it to both the installed APK version and `Settings.Global.BOOT_COUNT`. Reports from an older build or a previous boot are discarded. Its `StateFlow` updates the UI immediately when a cold-started receiver records evidence; no polling, LSPosed database access, self-process Hook or global “module active” approximation remains. Successful explicit service restart clears evidence before target processes report again.

## 2. Feature configuration and registration gate

The companion application keeps an offline UI mirror and synchronizes it with one libxposed `RemotePreferences` group. A fresh configuration enables only cross-device notification; portrait streaming and MiLink Multi-Channel default to disabled. Existing explicit values remain authoritative. An explicit pending-write bit prevents a setting changed while the LSPosed service is unavailable from being overwritten when the service reconnects. Writes use synchronous commits because the UI must not report success before the cross-process preference transaction completes.

Each supported target process reads one immutable feature snapshot in `onPackageLoaded`; hook callbacks never query preferences. Registration is partitioned by feature:

- cross-device notification: MiLink notification-process discovery scope plus its single-channel click bridge;
- portrait streaming: Mirror initial-message geometry plus the sink Activity orientation policy;
- MiLink Multi-Channel: MiLink core-process channel policy plus Host binding repair.

A disabled group never calls `hook(...)`. Reflection bindings for Notification and Multi-Channel are resolved as complete compatibility units before their feature Hooks are registered. If RemotePreferences cannot be read, the entry fails closed and registers no functional Hook. Changing a switch requires a target-process restart because libxposed cannot unregister an already installed method interception.

## 3. Least-privilege root boundary

`RootShell` accepts `RootCommand`, an enum whose command strings are compile-time constants. There is no public arbitrary-shell method and no user input is interpolated. Root is used for:

- a read-only UID probe;
- launching one audited HyperOS component if direct launch is denied;
- force-stopping the two explicit scope packages after confirmation.

Unit tests lock down this command set and the hidden component target.

## 4. Cross-device notification compatibility

MiLink uses `LyraDiscover.isCastServiceSupport(deviceId)` for both its local startup prerequisite and later remote-phone validation. A thread-confined depth scope wraps `isLocalServiceDataSupport(ServiceName)`. Only a nested Cast check performed while that local scope is active returns `true`; all remote calls proceed unchanged. This preserves the stock local `NotificationTrans` bit check and every remote capability check without reflecting `mNetworkingManager`, local device records or device IDs. The depth counter is restored in `finally`, so exceptions and re-entrant calls cannot leak the override to unrelated work on a pooled thread.

MiLink handles a notification click on a single-thread executor by making two synchronous SDK calls. It first calls the stable `MiuiSynergySdk.launchAppFromPendingIntentSink(Context, String, String, RelayAppCallback)` to prepare a reason-17 sink, then calls `NotifTransClient.handleNotification(String, NotificationMessage, INotificationTransCallback)` to tell the phone to create the passive stream. The reason-17 preparation is rejected on tablets by Xiaomi's form-factor gate. Replacing only that provider request with the complete reason-9 `PIN_APP` flow is also incorrect: it leaves the second command intact, races an active and a passive Lyra channel, and closes the sink whenever the active channel loses even if the passive channel succeeds milliseconds later.

The module therefore intercepts the two stable MiLink SDK boundaries together, only in `com.milink.crossdeviceservice`. A valid first call dispatches `performPinIconClick` directly through the public Mirror provider protocol and records a thread-confined, one-shot transaction keyed by device and package. The immediately following matching `handleNotification` is acknowledged locally instead of being published to the phone. The transaction is consumed on every match attempt and expires after five seconds, so a changed vendor control flow cannot suppress a later unrelated command on the pooled worker. Invalid inputs and unmatched publishes remain stock. This creates exactly one active reason-9 channel and reuses Xiaomi's connection, conflict, trust, password, sub-screen and native failure handling without calling an obfuscated sink or trust object. The old Mirror `CallProvider.call` interception is no longer installed.

## 5. Portrait stream establishment

Changing `SENSOR_LANDSCAPE` to `FULL_SENSOR` fixes the Activity window but does not by itself configure the phone encoder. The native pinned-app reason 9 selected by the MiLink click bridge takes the tablet's existing application-screen branch and sends `RequestAppScreenMessage` with window width, height and density before the remote display exists.

On the audited tablet build, the stock coordinator first reads the correct portrait `WindowMetrics` (`1880 x 3008`) but then treats fullscreen mode as a request to force landscape bounds. It swaps the values and emits a contradictory vertical open message with `screenWidth = 3008` and `screenHeight = 1880`. The module does not hook that obfuscated coordinator. Instead, it intercepts the stable `RequestAppScreenMessage.getRequestAppScreenOpenMessage(...)` factory: only a reason-9 request made while the real Mirror configuration is portrait and whose dimensions are landscape is swapped back. Already-correct portrait requests, physical landscape, other start reasons and every resize request proceed unchanged.

Consequently there are no hooks on the mixed session coordinator, sink manager, trust manager, remote-display creation, first frame, authentication visibility, or the later resize sender, and no delayed retries. Initial geometry is corrected once before the first request is created; subsequent orientation changes continue through Xiaomi's native resize path. The framework `Activity.setRequestedOrientation` interception is additionally guarded by `com.xiaomi.mirror.sink.g.isInstance(activity)`, so other Mirror activities preserve their stock orientation policy.

## 6. MiLink Multi-Channel

This feature is restricted to the exact `com.milink.service:core` process. The stable `SharedChannelImpl.sharedChannelPolicy` field supplies the private policy type; its sole constructor is intercepted after initialization. `ChannelLimitPolicy` is a structural adapter with explicit `UPDATED`, `ALREADY_SUPPORTED` and `UNSUPPORTED` results. It accepts only the exact three-entry `{1, 2, 4}` numeric limit-table shape and changes key `2` (AndroidPad) only when the current value is `1`. Existing value `2` is accepted idempotently; values above `2` and unknown layouts are untouched.

For Host binding, a weak reference to the stock `DiscoveryHost` is captured at its sole constructor. `MultiplatformProcessor.circulateCheck(String)` first runs normally. Only a numeric `215` result looks up an already-created official `DiscoveryProxy` for that Host and invokes its own `doBind`. Only official result `100` updates `MultiplatformProcessor.override`, the proxy's `boundResult`, and `startDiscovery`; failures and unknown Hosts preserve the original result. There is no forged proxy, polling loop, custom transport or unconditional success response.

## 7. Failure isolation

Every feature installer isolates reflection or signature failure and logs one actionable tag. A feature is reported as applied only after all required Hooks are registered. Hook bodies call the original implementation unchanged whenever an input is outside the narrowly defined compatibility case. Once a valid notification click has entered PIN_APP, its paired passive-stream publish is consumed even if the provider reports failure; an uncertain first request is never followed by a second state-changing transport attempt. The module declares `exceptionMode=protective` so an incompatible vendor update favors the stock process over a crash.
