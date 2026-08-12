# HyperConnect Toolkit

面向 HyperOS 平板的单一 LSPosed 兼容模块与 Material 3 管理界面。项目合并了此前独立的“跨设备通知解锁”“妙享横竖屏修复”和“MiLink Multi-Channel”，并提供系统隐藏的“互联中心 → 跨设备通知”顶层页面入口。

## 功能

- 跨设备通知：仅在 MiLink 的本机发现前置检查作用域内放行错误的 Cast 能力位，远端设备仍执行原生能力协商。
- 通知点击串流：在 MiLink 的通知点击工作线程把请求转入原生 `performPinIconClick`，并消费同一次点击自带的重复远端启动命令；串流只建立一条主动通道，完整复用普通应用流转的连接、认证和错误处理。
- 竖屏内容修复：在稳定消息构造边界校正全屏模式错误交换的首包宽高，使原生 `PIN_APP` 请求与实际屏幕方向一致；后续旋转继续使用系统 resize 链路。
- MiLink Multi-Channel：仅在 MiLink 核心进程把 AndroidPad 共享通道上限从 1 调整为 2，并只在原生检查返回 `HostNotBound (215)` 时补做官方绑定流程。
- 独立开关：上述三个功能组均可单独关闭；新配置默认只启用跨设备通知，竖屏串流与 MiLink Multi-Channel 默认关闭。目标进程启动时读取一次配置，关闭的功能不会注册 Hook。
- 隐藏设置入口：打开 `FeatureNotificationActivity`，即系统原本隐藏的“本机接收通知 / 同步本机通知”双标签页。
- 运行状态：跨设备通知、竖屏串流和 Multi-Channel 分别显示对应 MiLink Notification、Xiaomi Mirror 与 MiLink Core 进程的实际 Hook 应用状态；另行显示 Root、MiLink、Mirror 和两个通知安全设置的状态。

## 架构

项目坚持单向数据流，并把高权限与 ROM 私有实现隔离在边界层：

```text
Compose Material 3 UI
        ↓ intents / state
HomeViewModel (StateFlow + one-shot events)
        ├── FeatureSettingsStore ── libxposed RemotePreferences
        ├── HookRuntimeStore ←── validated target-process reports
        └── SystemStatusRepository ─── HiddenSettingsLauncher
                    ↓                         ↓
              fixed-command RootShell   explicit ComponentName

LSPosed process boundary
        ↓ exact package + process routing
HyperConnectModule
        ├── MiLinkNotificationHooks
        ├── PortraitStreamingHooks
        └── MiLinkMultiChannelHooks
```

`ui` 不执行 shell 或反射；`config` 通过 libxposed 官方服务同步开关；`data` 聚合只读设备状态；`platform` 只暴露枚举定义的固定 root 操作，不接收用户构造的命令；`xposed` 是唯一依赖 ROM 私有符号的代码区。

更详细的设计与兼容策略见 [ARCHITECTURE.md](ARCHITECTURE.md)。

## 构建

要求 JDK 17+、Android SDK 36。仓库使用 Gradle 8.14.5、AGP 8.13.1、Kotlin 2.2.10 和 Compose BOM 2025.06.01。最低系统为 Android 14（API 34）。

```powershell
./gradlew.bat :app:assembleDebug :app:testDebugUnitTest :app:lintDebug
```

本机如果设置了自定义 `GRADLE_USER_HOME`，首次构建可能需要通过仓库中配置的镜像补齐依赖缓存。

APK 输出：`app/build/outputs/apk/debug/app-debug.apk`。

## 安装与启用

1. 安装 APK。
2. 在 LSPosed 中启用“HyperConnect Toolkit”。
3. 保留两个静态推荐作用域：
   - `互联互通服务` / `com.milink.service`
   - `妙享中心` / `com.xiaomi.mirror`
4. 在 App 中点击“重启服务”，或重启设备。
5. 确认统一模块工作后，禁用旧的 HyperNotify、HyperconnectRotation 与 MiLink Multi-Channel，避免同一方法被多个模块重复 Hook。

功能开关通过 libxposed `RemotePreferences` 保存。更改开关后点击“重启服务”；已经注册到运行中进程的方法无法动态卸载，只有新进程会按新配置决定是否注册 Hook。

应用状态不是根据开关或 LSPosed 私有数据库猜测。MiLink Notification、MiLink Core 与 Xiaomi Mirror 只在各自完整功能 Hook 成功注册后独立回报。接收端同时校验系统签名权限、原始发送 UID、包名、精确进程、模块版本和进程允许的唯一功能位；证据还按当前 APK 版本和本次开机隔离。一个目标进程成功不会掩盖另一个目标进程未加载或不兼容的状态。

## 兼容与回滚

钩子针对当前 REDMI K Pad HyperOS `OS3.0.306.0.WAOCNXM` 的实现，并按类名、方法名和完整签名收窄。每个功能独立捕获兼容异常；ROM 更新导致某个混淆符号变化时，其余功能仍可加载，并可通过 LSPosed 日志中的 `HyperConnectToolkit` 标签诊断。

回滚时在 LSPosed 禁用本模块、重启两个目标作用域，再重新启用旧模块即可。卸载 App 不会修改通知设置、账号数据或信任记录。
