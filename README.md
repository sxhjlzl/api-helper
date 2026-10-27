<p align="center">
  <img src="docs/images/cover.png" alt="ApiHelper" width="100%">
</p>

<h3 align="center">Spring 微服务接口导航、文档与调试一体化 IDEA 插件</h3>

<p align="center">
  <a href="https://plugins.jetbrains.com/plugin/32327-apihelper"><img src="https://img.shields.io/jetbrains/plugin/v/32327-apihelper?style=flat-square&label=Marketplace&color=0ea5e9" alt="Marketplace Version"></a>
  <a href="https://plugins.jetbrains.com/plugin/32327-apihelper"><img src="https://img.shields.io/jetbrains/plugin/d/32327-apihelper?style=flat-square&label=Downloads&color=2563eb" alt="Downloads"></a>
  <img src="https://img.shields.io/badge/IDEA-2024.3%2B-10b981?style=flat-square" alt="IDEA 2024.3+">
  <a href="https://github.com/sxhjlzl/api-helper/blob/main/LICENSE"><img src="https://img.shields.io/github/license/sxhjlzl/api-helper?style=flat-square&color=64748b" alt="License"></a>
  <a href="https://github.com/sxhjlzl/api-helper/stargazers"><img src="https://img.shields.io/github/stars/sxhjlzl/api-helper?style=flat-square&color=f59e0b" alt="GitHub Stars"></a>
</p>

<p align="center">
  <a href="https://plugins.jetbrains.com/plugin/32327-apihelper"><b>插件市场</b></a>
  &nbsp;·&nbsp;
  <a href="./CHANGELOG.md"><b>更新日志</b></a>
  &nbsp;·&nbsp;
  <a href="https://github.com/sxhjlzl/api-helper/issues"><b>反馈建议</b></a>
  &nbsp;·&nbsp;
  <a href="./README_en.md"><b>English</b></a>
</p>

---

**ApiHelper** 专为使用 **Spring MVC**、**Spring Cloud OpenFeign** 与 **Spring 6 `@HttpExchange`** 的开发者打造。它将日常开发中最频繁的「找接口、跳对端、抄路径、写文档、发请求」全部收敛进 IntelliJ IDEA，让你无需在代码编辑器、YML 配置文件与外部接口工具之间来回切换。

## 核心能力一览

| 模块 | 核心特性 |
| :--- | :--- |
| **双向源码导航** | `@RestController` 与 `@FeignClient` / `@HttpExchange` 方法间 Gutter 图标一键互跳；自动容忍路径变量异名（如 `{id}` 与 `{orderId}`），支持自定义组合元注解（如 `@ApiV1RestController`）。 |
| **全局接口搜索** | 快捷键 `Ctrl + Alt + \`（macOS `Cmd + Opt + \`）随时唤起搜索弹窗，支持多关键词过滤：`Enter` 跳源码、`Shift + Enter` 跳对端、`Ctrl/Cmd + Enter` 直接调试。 |
| **六合一工作区** | 顶部集成 **`接口`**、**`体检`**、**`调试`**、**`前置 URL`**、**`请求历史`**、**`全局参数`** 六大原生标签页；`接口` 页支持按模块分组与孤儿接口一键筛查，`调试` 页采用上下可调节分栏（上半区请求配置 + 下半区带行号响应展示）。 |
| **接口契约体检** | 自动比对 Feign / HttpExchange 与 Controller 两端的参数名、路径变量、必填项与重复映射，把上线后才暴露的 400 / 404 / 启动失败提前到编辑器波浪线；支持一键修复参数名、复制 Markdown 体检报告。 |
| **反向调用方查询** | Controller 方法右键「查看调用方」，列出所有调用该接口的 Feign 客户端（含模块、路径与 HTTP 方法），改接口前先看清谁会挂——这是 IDE 原生 Find Usages 覆盖不到的能力。 |
| **智能 API 调试** | 自动解析方法入参预填 Path / Query / Header / Cookie，**递归展开嵌套 DTO 与 `List<T>` 生成 JSON 草稿**（识别 `@JsonProperty` 与 `@Schema(example)`）；支持多环境切换、草稿记忆、30 条历史回放与流式文件上传。 |
| **cURL & 文档导出** | 在编辑器 Gutter、接口树或调试面板均可一键导出标准 **cURL 命令**（自动合并全局 Header 与当前环境域名）及带字段注释的 **Markdown 接口文档**（支持展开 `DllResult<T>`、`DllPageResult<T>` 等泛型包装响应）。 |
| **Spring 配置感知** | 自动读取 `application.yml` / `bootstrap.yml` / `application-{profile}.yml`，解析 `server.port`、`context-path`、`servlet.path` 及 `${var:default}` 占位符；基于 UAST 同时支持 **Java 与 Kotlin**。 |

---

## 快速上手

### 1. 安装插件

- **插件市场安装（推荐）**：在 IDEA 中打开 `Settings` → `Plugins` → `Marketplace`，搜索 **ApiHelper** 安装；或访问 [JetBrains Marketplace](https://plugins.jetbrains.com/plugin/32327-apihelper)。
- **本地构建安装**：执行 `./gradlew :buildPlugin`，在 IDEA 中通过 `Install Plugin from Disk...` 选择 `build/distributions/api-helper-<version>.zip`。

### 2. 高频操作速查

| 场景 | 触发方式 | 效果说明 |
| :--- | :--- | :--- |
| **全局查找任意接口** | `Ctrl + Alt + \` / `Cmd + Opt + \` | 输入如 `POST order` 多词组合检索，一键跳转源码、跳对端或打开调试 |
| **客户端 ↔ 服务端互跳** | 点击代码行号旁 Gutter 箭头 | 左键直接跳转（多实现时弹出列表）；Controller 侧右键可直接调试或复制 |
| **一键导出 cURL / 文档** | Controller Gutter 右键 / 接口树右键 | 复制可直接执行的 `curl` 命令，或包含请求/响应字段说明表格的 Markdown 文档 |
| **筛查失效孤儿接口** | 工具窗口工具栏 `按配对状态筛选` | 一键过滤「仅显示未配对接口」，快速定位无对应 Controller 的 Feign 声明 |
| **切换环境与公共 Token** | 工具窗口 `前置 URL` / `全局参数` 标签页 | 表格化配置多环境域名与 `Authorization` 等公共 Header，调试与导出 cURL 自动生效 |
| **回放历史调试请求** | 工具窗口 `请求历史` 标签页 / 调试栏 `历史` | 双击任意历史记录，自动切回调试分栏并完整还原当时的请求参数与响应内容 |

---

## 插件配置

路径：`Settings` → `Tools` → `ApiHelper`

- **Spring Active Profile**：手动指定激活的 profile（如 `dev,test`）；留空时自动从工程配置推断。
- **调试连接 / 请求超时**：自定义 HTTP 连接超时（默认 `30s`）与整体请求超时（默认 `60s`），同时自动遵循 IDE 全局 HTTP 代理。
- **环境与全局请求头**：与工具窗口内的「前置 URL」「全局参数」标签页双向实时同步。

---

## 兼容性与构建

| 环境项 | 版本与要求 |
| :--- | :--- |
| **支持 IDE** | IntelliJ IDEA **2024.3+**（build 243+，已验证兼容 **2026.3 EAP**） |
| **内置依赖** | Java Plugin（bundled）、Kotlin Plugin（bundled） |
| **支持框架** | Spring MVC · Spring Boot · Spring Cloud OpenFeign · Spring 6 `@HttpExchange` |
| **构建技术栈** | Kotlin 2.3.20 · JDK 21 · Gradle 9.5.0 · IntelliJ Platform Gradle Plugin 2.12.0 |

<details>
<summary><b>本地开发命令与工程结构（点击展开）</b></summary>

```bash
# 编译与单元测试
./gradlew :check

# 校验插件配置与结构
./gradlew :verifyPluginProjectConfiguration :verifyPluginStructure

# 打包可分发 ZIP
./gradlew :buildPlugin

# 启动沙箱 IDE 调试
./gradlew :runIde
```

```text
src/main/kotlin/com/lizhuolun/apihelper/
├── ApiHelperBundle.kt        # 中英双语国际化资源入口
├── cache/                    # 双边映射快照倒排索引缓存与 SmartPointer 缓存
├── config/                   # Spring 配置读取、profile 推断与占位符解析
├── core/                     # HTTP 映射模型、路径规范化与注解解析（含组合元注解）
├── listener/                 # 启动异步预热与 PSI 增量防抖监听
├── provider/                 # 图标资源与 Java/Kotlin 双向 LineMarker
├── scanner/                  # 基于 AnnotatedElementsSearch 的全工程端点扫描
├── settings/                 # 全局持久化配置与 Settings 页面
└── ui/                       # 多标签页工具窗口、接口树、全局搜索弹窗与调试面板
```

</details>

---

## 反馈与支持

- 当前版本：**`1.2.0`**（完整变更记录见 [CHANGELOG.md](./CHANGELOG.md)）
- 遇到问题或有新功能想法？欢迎提交 [GitHub Issue](https://github.com/sxhjlzl/api-helper/issues)。
- 如果 **ApiHelper** 提升了你的开发效率，欢迎给仓库点亮一颗 **Star** 支持！
