# 贡献指南

> **中文** | [English](./CONTRIBUTING_en.md)

感谢你对 ApiHelper 的兴趣！本文档说明如何在本地开发与提交贡献。

## 开发环境

| 工具 | 版本 |
| --- | --- |
| JDK | 21（推荐 Temurin 21.0.11） |
| Gradle | 9.5.0（仓库内含 wrapper，无需手动安装） |
| IDEA | 2024.3 或更新 |

## 本地开发流程

1. Fork 本仓库到你的 GitHub 账号下，然后克隆到本地
2. 用 IDEA 打开（`File` → `Open` → 选择仓库根目录的 `build.gradle.kts`，按 `Open as Project`）
3. 等待 Gradle 同步完成（首次会下载 IntelliJ Platform SDK，约 1-2 GB）
4. 在终端运行 `./gradlew :runIde`，或在 Gradle 面板找到 `intellij platform → runIde`，会拉起沙箱 IDE 实例

## 常用 Gradle 任务

```bash
./gradlew :compileKotlin   # 仅编译 Kotlin 源码
./gradlew :build           # 完整构建（含 instrumentCode / processResources）
./gradlew :buildPlugin     # 产出 build/distributions/api-helper-<version>.zip
./gradlew :runIde          # 沙箱 IDE 调试
./gradlew :verifyPlugin    # 校验插件与目标 SDK 兼容性
```

## 代码规范

### Kotlin

- 严格按 `kotlin.code.style=official`（已配置在 `gradle.properties`）
- 注释使用中文，类与方法注释需写明 `@param` / `@return`
- 关键业务逻辑必须打 `thisLogger().info(...)` / `warn(...)` 日志，便于线上排查
- 日志分隔符统一使用英文逗号与冒号
- 禁止行尾注释，注释独立成行

### 注解 / 模型

- 新增 Spring 注解请同时更新 [`SpringAnnotations`](src/main/kotlin/com/lizhuolun/apihelper/core/annotation/SpringAnnotations.kt) 并补全 `METHOD_ANNOTATION_TO_HTTP` 映射
- 新增端点类型需要扩展 [`EndpointKind`](src/main/kotlin/com/lizhuolun/apihelper/core/HttpMappingInfo.kt) 枚举

### IDEA 扩展点

- 在 [`plugin.xml`](src/main/resources/META-INF/plugin.xml) 中注册新扩展点时，遵循已有顺序
- 同时支持 Java 与 Kotlin 的 LineMarker 必须注册两次（`language="JAVA"` 与 `language="kotlin"`）
- 使用 `@Service(Service.Level.PROJECT)` 而非手动单例
- 所有 PSI 访问必须包在 `ApplicationManager.getApplication().runReadAction(Computable { ... })` 内，且不可在 read action 外保留 PSI 派生数据

### 国际化

- 新增任何用户可见字符串都必须经过 [`ApiHelperBundle`](src/main/kotlin/com/lizhuolun/apihelper/ApiHelperBundle.kt)
- 同时在 `messages/ApiHelperBundle.properties`（英文）与 `ApiHelperBundle_zh_CN.properties`（中文）补全 key

## 提交流程

1. 从 `main` 分支拉取一个 feature 分支：`git checkout -b feat/<short-name>`
2. 完成开发并自测
3. 提交信息建议使用 [Conventional Commits](https://www.conventionalcommits.org/zh-hans/v1.0.0/)：`feat: ...` / `fix: ...` / `docs: ...`
4. 提 PR 时附上：
   - 变更说明
   - 测试覆盖（手动 / 单元）
   - 如涉及 UI，附上截图或动图
5. 等待 maintainer review

## 报告问题

请在 [GitHub Issues](https://github.com/sxhjlzl/api-helper/issues) 中提交，并尽量包含：

- IDEA 版本
- ApiHelper 版本
- 最小可复现示例（项目 zip 或代码片段）
- 期望行为与实际行为
- 异常堆栈（如有）
