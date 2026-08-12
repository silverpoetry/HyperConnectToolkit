# HyperConnect Toolkit

[English](README_EN.md) · [安装指南](docs/installation.md) · [架构](ARCHITECTURE.md) · [问题排查](docs/troubleshooting.md) · [关于](#关于)

[![CI](https://github.com/silverpoetry/HyperConnectToolkit/actions/workflows/ci.yml/badge.svg)](https://github.com/silverpoetry/HyperConnectToolkit/actions/workflows/ci.yml)
[![Release](https://img.shields.io/github/v/release/silverpoetry/HyperConnectToolkit?display_name=tag)](https://github.com/silverpoetry/HyperConnectToolkit/releases)
[![License: Apache-2.0](https://img.shields.io/badge/license-Apache--2.0-blue.svg)](LICENSE)

HyperConnect Toolkit 是面向 HyperOS 平板的单一 Modern LSPosed 兼容模块与 Material 3 管理界面。它把解锁跨设备通知流转、允许平板竖屏流转应用和 MiLink Multi-Channel 整合为三个独立开关，并提供系统隐藏的跨设备流转设置页入口。

> [!WARNING]
> 本项目注入 MiLink 与 Xiaomi Mirror 的 ROM 私有实现。错误的 HyperOS 版本或已变化的私有签名可能导致目标服务崩溃、串流失败或重启循环。启用前请准备可用的 root/LSPosed 恢复路径；系统更新后先禁用模块，再重新验证兼容性。它不绕过 Xiaomi 账号、设备信任、密码验证或系统权限校验。

## 功能

- **解锁跨设备通知流转**：只在本机设备发现前置检查中纠正平板能力误判；远端设备仍走原生能力协商。通知点击复用普通应用流转的原生 `PIN_APP` 路径，因此保留连接、认证、密码、冲突与错误处理。
- **允许平板竖屏流转应用**：在初始请求消息构造边界纠正横竖尺寸倒置，并允许目标 Mirror Activity 按完整方向传感器运行；后续旋转继续使用 Xiaomi 原生 resize 链路。
- **MiLink Multi-Channel**：将 AndroidPad 的共享通道上限从 1 调整到 2，并且只在原生返回 `HostNotBound (215)` 时补做官方绑定。要使用此功能，必须在手机上开启同名选项。
- **独立配置与可验证状态**：三个功能分别在 MiLink Notification、Xiaomi Mirror 和 MiLink Core 进程完整注册后回报实际状态。关闭的功能不注册 Hook；新安装默认只启用通知流转。
- **系统跨设备流转设置页**：可直接打开系统原本隐藏的跨设备流转设置页；在普通启动被 ROM 拦截时才使用固定、经过审计的 root 兼容启动。

## 系统要求

- Android 14（API 34）或更高版本；
- Modern LSPosed API 101 或更高版本；
- 已安装 HyperOS 的 MiLink（`com.milink.service`）与 Xiaomi Mirror（`com.xiaomi.mirror`）；
- 具备在异常时禁用模块并重启设备的恢复方式；
- 当前兼容基线是 Xiaomi HyperOS 平板实现，其他 ROM、手机及未验证的系统版本不保证可用。

模块的静态作用域只有 MiLink 与 Xiaomi Mirror 两个包；实际注入进一步收窄到 `com.milink.crossdeviceservice`、`com.milink.service:core` 和 `com.xiaomi.mirror` 三个精确进程。无关进程不会读取配置或注册 Hook。

## 安装

1. 从 [Releases](https://github.com/silverpoetry/HyperConnectToolkit/releases) 下载 APK 与同名 `.sha256` 文件，并校验哈希；
2. 安装 APK，在 LSPosed 中启用 **HyperConnect Toolkit**，保留其自动声明的 MiLink 与 Xiaomi Mirror 静态作用域；
3. 重启设备，或在应用中点击“重启服务”以重新加载两个目标服务；
4. 首先保持默认的“解锁跨设备通知流转”开启，确认通知接收、认证和串流正常；
5. 仅在需要时再开启竖屏或 Multi-Channel，并重启服务后检查每项的实际应用状态；
6. 禁用旧的 HyperNotify、HyperconnectRotation 与 MiLink Multi-Channel 模块，避免同一私有成员被重复 Hook。

升级模块代码后，已加载的目标进程不会自动替换旧 Hook；需要重启服务或重启设备。仅修改应用内开关时，也需要重启目标服务才能使新进程按新配置注册 Hook。完整的安装、升级与移除步骤见[安装指南](docs/installation.md)。

## 工作方式

```text
Material 3 管理界面
        │  Remote Preferences / 受限 root 边界
        ▼
Modern LSPosed 静态作用域
        │  精确包名 + 进程路由
        ▼
HyperConnectModule
        ├── MiLink Notification：本机发现与通知点击 PIN_APP 桥接
        ├── Xiaomi Mirror：竖屏首包尺寸与 Activity 方向策略
        └── MiLink Core：AndroidPad 通道策略与官方 Host 绑定修复
```

每个功能先完整解析自身需要的私有成员，再原子注册对应 Hook。反射失败、未知输入或不满足条件时保留原始系统路径。详细边界、状态报告校验、事务生命周期与失败隔离说明见[架构文档](ARCHITECTURE.md)。

## 已知限制

- 这不是通用的跨设备串流实现；它只适配当前审计过的 HyperOS MiLink 与 Xiaomi Mirror 路径；
- 通知与应用流转仍依赖同账号、设备发现、网络、蓝牙/WLAN、信任关系和 Xiaomi 原生认证；
- ROM 更新可能更改混淆符号或消息布局，某项功能会主动保持关闭而不是尝试猜测；
- Multi-Channel 仅将平板通道上限调至 2，不承诺所有手机、账号或网络条件都可同时串流；
- 卸载 APK 不会修改账号、设备信任、通知内容或系统互联设置。

## 构建

需要 JDK 17 与 Android SDK Platform 36：

```powershell
.\gradlew.bat --no-daemon clean :app:testDebugUnitTest :app:lintDebug :app:assembleDebug
```

Debug APK 位于 `app/build/outputs/apk/debug/app-debug.apk`。标签发布由 GitHub Actions 使用仓库 Secrets 的专用签名密钥生成 Release APK 和 SHA-256；本地 release 签名方式见[发布签名](docs/release-signing.md)。

## 关于

应用右上角的“关于”页会显示当前版本、Apache License 2.0 与 GitHub 仓库入口：<https://github.com/silverpoetry/HyperConnectToolkit>。点击链接由系统浏览器打开；应用不申请网络权限，也不包含遥测或远程崩溃上报。

## 文档与参与

- [安装、升级与移除](docs/installation.md)
- [问题排查与日志采集](docs/troubleshooting.md)
- [架构与兼容策略](ARCHITECTURE.md)
- [发布签名](docs/release-signing.md)
- [贡献指南](CONTRIBUTING.md)
- [隐私说明](PRIVACY.md) 与 [安全策略](SECURITY.md)

## 许可证

项目以 [Apache License 2.0](LICENSE) 发布。第三方依赖及其许可证见 [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md)。
