# 互联增强（HyperConnectToolkit）

面向 HyperOS 平板的单一 LSPosed 兼容模块与 Material 3 管理界面。项目合并了此前独立的“跨设备通知解锁”和“妙享横竖屏修复”，并提供系统隐藏的“互联中心 → 跨设备通知”顶层页面入口。

## 功能

- 跨设备通知：只对 MiLink 查询本机设备能力时绕过错误的平板 Cast 能力门槛，远端设备仍必须具备原生通知与串流能力。
- 通知点击串流：复用 Mirror 原始参数和启动调用，绕过平板专属的 phone-only 提前返回。
- 冷启动认证：在通知原因（`start_reason = 17`）的串流建立前刷新远端信任状态，保留原生密码/可信设备流程。
- 竖屏内容修复：允许 `FULL_SENSOR`，并在远端显示创建、首帧和认证 UI 真正退出后同步竖屏窗口尺寸。
- 120 Hz：统一调整虚拟显示刷新率、串流显示配置和编码帧率上限。
- 隐藏设置入口：打开 `FeatureNotificationActivity`，即系统原本隐藏的“本机接收通知 / 同步本机通知”双标签页。
- 环境诊断：显示由真实 Mirror 目标进程报告的模块加载状态、Root、MiLink、Mirror 及两个通知安全设置的状态。

## 架构

项目坚持单向数据流，并把高权限与 ROM 私有实现隔离在边界层：

```text
Compose Material 3 UI
        ↓ intents / state
HomeViewModel (StateFlow + one-shot events)
        ↓
SystemStatusRepository ─── HiddenSettingsLauncher
        ↓                         ↓
fixed-command RootShell      explicit ComponentName

LSPosed process boundary
        ↓
HyperConnectModule (signature-constrained hooks)
```

`ui` 不执行 shell 或反射；`data` 聚合只读设备状态；`platform` 只暴露枚举定义的固定 root 操作，不接收用户构造的命令；`xposed` 是唯一依赖 libxposed API 和 ROM 混淆符号的代码区。

更详细的设计与兼容策略见 [ARCHITECTURE.md](ARCHITECTURE.md)。

## 构建

要求 JDK 17+、Android SDK 36。仓库使用 Gradle 8.14.5、AGP 8.13.1、Kotlin 2.2.10 和 Compose BOM 2025.06.01。最低系统为 Android 14（API 34），与 `VirtualDisplayConfig.Builder` 的实际平台 API 下限一致。

```powershell
./gradlew.bat :app:assembleDebug :app:testDebugUnitTest :app:lintDebug
```

本机如果设置了自定义 `GRADLE_USER_HOME`，首次构建可能需要通过仓库中配置的镜像补齐依赖缓存。

APK 输出：`app/build/outputs/apk/debug/app-debug.apk`。

## 安装与启用

1. 安装 APK。
2. 在 LSPosed 中启用“互联增强”。
3. 保留三个静态推荐作用域：
   - `互联增强`（仅作旧版 LSPosed 的兼容握手；新版状态以 Mirror 目标进程回报为准）
   - `互联互通服务` / `com.milink.service`
   - `妙享中心` / `com.xiaomi.mirror`
4. 在 App 中点击“重启服务”，或重启设备。
5. 确认统一模块工作后，禁用旧的 HyperNotify 与 HyperconnectRotation，避免重复日志与重复尺寸消息。

“模块已生效”状态不是根据开关或 LSPosed 私有数据库猜测，而是在 Mirror 完成应用初始化、全部目标 Hook 已装载后，由系统进程携带原始 UID 回报。部分 LSPosed 版本不注入模块自身 App，这不会再造成“等待激活”的误报。

## 兼容与回滚

钩子针对当前 REDMI K Pad HyperOS `OS3.0.306.0.WAOCNXM` 的实现，并按类名、方法名和完整签名收窄。每个功能独立捕获兼容异常；ROM 更新导致某个混淆符号变化时，其余功能仍可加载，并可通过 LSPosed 日志中的 `HyperConnectToolkit` 标签诊断。

回滚时在 LSPosed 禁用本模块、重启两个目标作用域，再重新启用旧模块即可。卸载 App 不会修改通知设置、账号数据或信任记录。
