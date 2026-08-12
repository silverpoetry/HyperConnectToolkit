# 发布签名

GitHub 标签发布工作流构建已签名的 Release APK、执行签名验证，并上传同名 SHA-256 文件。发布前需要在仓库 Secrets 中配置：

| Secret | 用途 |
|---|---|
| `RELEASE_KEYSTORE_BASE64` | Base64 编码的专用 release keystore |
| `RELEASE_KEYSTORE_PASSWORD` | keystore 密码 |
| `RELEASE_KEY_ALIAS` | key alias |
| `RELEASE_KEY_PASSWORD` | key 密码 |

私钥、keystore、密码、Base64 值及 debug keystore 都不能提交到仓库、Issue、日志或截图中。

## 本地签名构建

PowerShell 示例：

```powershell
$env:HYPERCONNECT_KEYSTORE_PATH = "D:\Keys\hyperconnect-release.jks"
$env:HYPERCONNECT_KEYSTORE_PASSWORD = "<keystore password>"
$env:HYPERCONNECT_KEY_ALIAS = "<key alias>"
$env:HYPERCONNECT_KEY_PASSWORD = "<key password>"
.\gradlew.bat --no-daemon :app:testDebugUnitTest :app:lintRelease :app:assembleRelease
```

全部四项环境变量存在时，`assembleRelease` 生成签名后的 `app-release.apk`。未配置时 Gradle 仍可生成未签名的 `app-release-unsigned.apk`，便于本地静态检查，但该文件不能作为公开安装包。

使用 Android SDK Build Tools 的 `apksigner` 检查签名：

```powershell
& "$env:ANDROID_HOME\build-tools\<version>\apksigner.bat" verify --verbose --print-certs `
  .\app\build\outputs\apk\release\app-release.apk
Get-FileHash .\app\build\outputs\apk\release\app-release.apk -Algorithm SHA256
```

## 标签发布

1. 将 `app/build.gradle.kts` 的 `versionName` 和 `versionCode` 更新为待发布版本。
2. 完成测试、Lint 与签名构建验证。
3. 提交并推送 `main`。
4. 创建且推送与 `versionName` 完全一致的标签，例如 `v1.5.0`。
5. GitHub Actions 校验标签、构建与签名 APK，生成 SHA-256，并从 `CHANGELOG.md` 提取该版本的发布说明。

标签和 `versionName` 不一致、没有配置 Secrets、签名验证失败或变更日志中没有该版本标题时，工作流会停止而不会发布不完整的 Release。
