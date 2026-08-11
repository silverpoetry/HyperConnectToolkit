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

MiLink uses `LyraDiscover.isCastServiceSupport(deviceId)` for both its local startup prerequisite and later remote-phone validation. A thread-confined depth scope wraps `isLocalServiceDataSupport(ServiceName)`. Only a nested Cast check performed while that local scope is active returns `true`; all remote calls proceed unchanged. This preserves the stock local `NotificationTrans` bit check and every remote capability check without reflecting `mNetworkingManager`, local device records or device IDs. The depth counter is restored in `finally`, so exceptions and re-entrant calls cannot leak the override to unrelated work on a pooled thread.

For a notification click, the external client calls the stable Mirror provider method `launchAppFromPendingIntentSink`. (The downstream helper logs this operation as `launchNotificationSink`, but that is not the provider protocol name.) Its stock internal branch rejects the request when Xiaomi's phone-form-factor predicate is false before opening reason 17. Rather than spoofing that predicate or patching the rejected helper, `CallProvider.call(String, String, Bundle)` is intercepted at the protocol boundary only for that exact method and only when both `deviceId` and `package` are present. The bridge copies the bundle, maps `deviceId` to the stock pin-app key `remoteDeviceId`, and lets the original provider dispatch `performPinIconClick`. The provider then runs Xiaomi's complete `PIN_APP` (reason 9) flow, including connection/conflict checks, trust and password handling, sub-screen allocation and native failure UI. Other provider methods and malformed requests proceed unchanged.

The bridge preserves the notification caller's expected `value = 0` acknowledgement while retaining any stock provider result such as `result_activity_component`. It does not call the sink manager or trust manager directly and does not impersonate the tablet as a phone.

## 4. Portrait stream establishment

Changing `SENSOR_LANDSCAPE` to `FULL_SENSOR` fixes the Activity window but does not by itself configure the phone encoder. The native pinned-app reason 9 selected by the provider bridge takes the tablet's existing application-screen branch and sends `RequestAppScreenMessage` with window width, height and density before the remote display exists.

No session-open method is hooked. Xiaomi's stock coordinator reads `WindowMetrics`, builds the request and sends it through the stock transport. The same path is used by a desktop pinned-app shortcut, so authentication and initial dimensions no longer depend on notification-specific patches.

Consequently there are no hooks on the mixed session coordinator, sink manager, trust manager, remote-display creation, first frame, authentication visibility, or the later resize sender, and no delayed retries. The framework `Activity.setRequestedOrientation` interception is additionally guarded by `com.xiaomi.mirror.sink.g.isInstance(activity)`, so other Mirror activities preserve their stock orientation policy.

## 5. Failure isolation

Every hook installer catches reflection or signature failure independently and logs one actionable tag. Hook bodies call the original implementation unchanged whenever an input is outside the narrowly defined compatibility case. Once a valid notification request has been rerouted, the native provider is invoked exactly once: an exception is not followed by a second, potentially state-changing notification launch. The module declares `exceptionMode=protective` so an incompatible vendor update favors the stock process over a crash.
