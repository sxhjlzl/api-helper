# Release Process / 发布流程

本文档面向维护者，记录 ApiHelper 的发布流程。

## English

### 0. One-Time Setup

- Prepare the GitHub repository: `sxhjlzl/api-helper`
- Prepare JetBrains Marketplace metadata for `ApiHelper`
- Configure signing variables when signing is needed: `CERTIFICATE_CHAIN`, `PRIVATE_KEY`, `PRIVATE_KEY_PASSWORD`
- Configure `PUBLISH_TOKEN` only after the Marketplace plugin entry exists

### 1. Verify Metadata

Check these files before release:

- `settings.gradle.kts`: `rootProject.name = "api-helper"`
- `gradle.properties`: `pluginName = ApiHelper`, `pluginVersion = 1.0.0`
- `src/main/resources/META-INF/plugin.xml`: plugin id `com.lizhuolun.apihelper`
- `CHANGELOG.md` and `CHANGELOG_en.md`: release notes are complete for the target version

### 2. Local Verification

```bash
./gradlew :check :verifyPluginProjectConfiguration :verifyPluginStructure
./gradlew :buildPlugin
```

The plugin artifact should be:

```text
build/distributions/api-helper-<version>.zip
```

Install the zip into a clean IDEA sandbox through `Install Plugin from Disk...` and smoke-test endpoint scanning, gutter navigation, and API debugging.

### 3. First GitHub Release

```bash
git tag -a v1.0.0 -m "ApiHelper 1.0.0"
git push origin v1.0.0
```

Create the first GitHub Release for `v1.0.0` and upload:

```text
build/distributions/api-helper-1.0.0.zip
```

Use `CHANGELOG_en.md` as the release notes source.

### 4. First Marketplace Upload

JetBrains requires the first plugin version to be uploaded manually:

1. Log in to JetBrains Marketplace.
2. Create the Marketplace plugin entry.
3. Upload the signed ApiHelper zip.
4. Fill in repository, issue tracker, privacy policy, license, screenshots, and tags.
5. Wait for review.

After the first plugin entry exists, later versions can use `./gradlew :publishPlugin` with `PUBLISH_TOKEN`.

## 中文

### 0. 一次性配置

- 准备 GitHub 仓库：`sxhjlzl/api-helper`
- 在 JetBrains Marketplace 准备 `ApiHelper` 插件信息
- 如需签名，配置 `CERTIFICATE_CHAIN`、`PRIVATE_KEY`、`PRIVATE_KEY_PASSWORD`
- Marketplace 插件条目创建后，再配置 `PUBLISH_TOKEN`

### 1. 检查元信息

发布前确认：

- `settings.gradle.kts`：`rootProject.name = "api-helper"`
- `gradle.properties`：`pluginName = ApiHelper`，`pluginVersion = 1.0.0`
- `src/main/resources/META-INF/plugin.xml`：插件 ID 为 `com.lizhuolun.apihelper`
- `CHANGELOG.md` 与 `CHANGELOG_en.md`：目标版本的发布说明完整

### 2. 本地验证

```bash
./gradlew :check :verifyPluginProjectConfiguration :verifyPluginStructure
./gradlew :buildPlugin
```

插件产物应为：

```text
build/distributions/api-helper-<version>.zip
```

通过 `Install Plugin from Disk...` 安装到干净 IDEA 中，手动冒烟测试端点扫描、gutter 导航和 API 调试。

### 3. 首次 GitHub Release

```bash
git tag -a v1.0.0 -m "ApiHelper 1.0.0"
git push origin v1.0.0
```

为 `v1.0.0` 创建首个 GitHub Release，并上传：

```text
build/distributions/api-helper-1.0.0.zip
```

Release Notes 使用 `CHANGELOG_en.md` 中的内容。

### 4. 首次 Marketplace 上传

JetBrains Marketplace 首个版本需要手工上传：

1. 登录 JetBrains Marketplace。
2. 创建 Marketplace 插件条目。
3. 上传签名后的 ApiHelper zip。
4. 补全仓库、Issue Tracker、隐私政策、License、截图和标签。
5. 等待审核。

首次插件条目创建完成后，后续版本可配置 `PUBLISH_TOKEN` 并使用 `./gradlew :publishPlugin`。
