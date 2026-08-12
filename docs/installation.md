# 安装、升级与移除

## 前提条件

- Android 14（API 34）或更高版本的 HyperOS 平板；
- 可用的 Modern LSPosed（API 101+）与 root 恢复环境；
- 已安装的 `com.milink.service` 与 `com.xiaomi.mirror`；
- 能够在目标服务反复退出时禁用 LSPosed 模块并重启设备。

私有 MiLink 与 Xiaomi Mirror 签名会随 ROM 更新变化。即使 Android API 级别相同，新系统也不自动视为兼容。

## 安装公开 Release

1. 在 [Releases](https://github.com/silverpoetry/HyperConnectToolkit/releases) 下载 APK 与同名 `.sha256` 文件。
2. 校验 SHA-256：

   ```powershell
   Get-FileHash .\HyperConnectToolkit-vX.Y.Z.apk -Algorithm SHA256
   ```

3. 安装 APK。
4. 在 LSPosed 启用 **HyperConnect Toolkit**。保留模块自动声明的 MiLink 和 Xiaomi Mirror 静态作用域；不要额外选择普通应用或本模块自己的应用进程。
5. 重启设备，或打开应用并选择“重启服务”。
6. 打开应用，首先确认默认开启的“解锁跨设备通知流转”已由 MiLink Notification 报告为已应用。
7. 按需启用“允许平板竖屏流转应用”或 “MiLink Multi-Channel”，然后再次重启服务。

## 首次验证

按以下顺序验证，便于定位失败边界：

1. 不启用其他功能，确认两端设备发现与平板接收通知；
2. 点击通知卡片，确认原生认证/密码窗口仍正常出现；
3. 从桌面普通应用快捷入口验证应用流转；
4. 只在有实际需求时开启竖屏功能，并以纵向应用确认首帧内容方向；
5. 只在手机端已启用相应选项时开启 Multi-Channel，验证第二台平板发现与官方绑定；
6. 重新测试未涉及的普通 MiLink 功能，确认无回归。

如果 MiLink、Mirror 或系统界面反复退出，立即在 LSPosed 禁用模块并重启。不要修改系统分区、删除系统包或替换 ROM 文件；本项目不需要这些操作。

## 升级

安装新 APK 不能替换已经载入目标进程的 Hook。模块代码更新后执行以下步骤：

1. 在 LSPosed 暂时禁用旧模块；
2. 重启设备，或确保两个目标服务已停止；
3. 安装新 APK；
4. 重新启用模块；
5. 重启设备或点击“重启服务”；
6. 检查每个已开启功能的独立状态。

公开 Release 使用专用签名密钥。Android 不能原地覆盖本地 debug 签名 APK；需要先禁用旧模块并重启，再卸载 debug 包、安装 Release、重新启用并重启。

## 关闭功能

关闭一个开关后，应用不会尝试撤销已注册的 Hook。点击“重启服务”或重启设备后，新的目标进程不会注册该功能。这样可以避免在正在进行的串流中异步拆除私有事务。

## 完全移除

1. 在 LSPosed 禁用模块；
2. 重启设备或重启目标服务；
3. 卸载 APK。

卸载不会删除 Xiaomi 账号、设备信任、通知内容或系统跨设备流转设置。LSPosed 的模块元数据按其自身生命周期处理。
