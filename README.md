<h1 align="center">ApiHelper</h1>

<p align="center"><b>Spring 微服务接口导航、文档与调试一体化 IDEA 插件</b></p>

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

**ApiHelper** 专为使用 **Spring MVC**、**Spring Cloud OpenFeign** 与 **Spring 6 `@HttpExchange`** 的开发者打造，把日常开发中最高频的「找接口、跳对端、写文档、发请求」收敛进 IntelliJ IDEA，无需在编辑器、配置文件与外部接口工具之间来回切换。

## 核心能力

| 模块 | 核心特性 |
| :--- | :--- |
| **双向源码导航** | `@RestController` 与 `@FeignClient` / `@HttpExchange` 方法间 Gutter 图标一键互跳，容忍路径变量异名，支持组合元注解。 |
| **全局接口搜索** | `Ctrl + Alt + \`（macOS `Cmd + Opt + \`）唤起多关键词搜索，`Enter` 跳源码、`Shift + Enter` 跳对端、`Ctrl/Cmd + Enter` 直接调试。 |
| **六合一工作区** | **`接口`**、**`体检`**、**`调试`**、**`前置 URL`**、**`请求历史`**、**`全局参数`** 六大原生标签页，各自拥有完整窗口高度。 |
| **接口契约体检** | 自动比对两端参数名、路径变量与重复映射，把上线后才暴露的 400 / 404 提前到编辑器波浪线，支持一键修复与 Markdown 报告导出。 |
| **反向调用方查询** | Controller 方法右键「查看调用方」，列出所有调用它的 Feign 客户端——IDE 原生 Find Usages 覆盖不到的能力。 |
| **智能 API 调试** | 自动预填参数并递归展开嵌套 DTO 生成 JSON 草稿，支持多环境切换、草稿记忆与 30 条历史回放；`{{变量}}` 插值与认证预设打通登录鉴权链路。 |
| **cURL / 文档 / OpenAPI** | 一键导出 cURL 命令、Markdown 接口文档与 OpenAPI 3.0 JSON，并支持从剪贴板导入 cURL 回填调试器。 |
| **Spring 配置感知** | 自动解析 `application*.yml` 的端口、`context-path` 与 `${var:default}` 占位符；基于 UAST 同时支持 Java 与 Kotlin。 |

## 快速上手

### 安装

- **插件市场（推荐）**：`Settings` → `Plugins` → `Marketplace`，搜索 **ApiHelper** 安装；或访问 [JetBrains Marketplace](https://plugins.jetbrains.com/plugin/32327-apihelper)。
- **本地构建**：执行 `./gradlew :buildPlugin`，然后在 IDEA 中通过 `Install Plugin from Disk...` 选择 `build/distributions/api-helper-<version>.zip`。

### 高频操作

| 场景 | 触发方式 | 效果说明 |
| :--- | :--- | :--- |
| **全局查找任意接口** | `Ctrl + Alt + \` / `Cmd + Opt + \` | 多关键词检索（如 `POST order`），跳源码 / 跳对端 / 开调试 |
| **客户端 ↔ 服务端互跳** | 点击代码行号旁 Gutter 箭头 | 左键跳转；Controller 侧右键可调试或复制 |
| **一键导出 cURL / 文档** | Controller Gutter 右键 / 接口树右键 | 复制可直接执行的 `curl` 命令或 Markdown 接口文档 |
| **筛查失效孤儿接口** | 工具窗口工具栏 `按配对状态筛选` | 一键过滤「仅显示未配对接口」 |
| **切换环境与公共 Token** | `前置 URL` / `全局参数` 标签页 | 表格化配置环境域名与公共 Header，调试与导出自动生效 |
| **回放历史调试请求** | `请求历史` 标签页 / 调试栏 `历史` | 双击还原当时的请求参数与响应内容 |

## 插件配置

路径：`Settings` → `Tools` → `ApiHelper`

- **Spring Active Profile**：手动指定激活的 profile（如 `dev,test`）；留空时自动从工程配置推断。
- **调试连接 / 请求超时**：自定义 HTTP 连接超时（默认 `30s`）与整体请求超时（默认 `60s`），自动遵循 IDE 全局 HTTP 代理。
- **环境与全局请求头**：与工具窗口的「前置 URL」「全局参数」标签页双向实时同步。

## 兼容性

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
├── core/                     # HTTP 映射模型、路径规范化、注解解析与契约体检
├── inspection/               # 契约一致性编辑器实时检查与 QuickFix
├── listener/                 # 启动异步预热与 PSI 增量防抖监听
├── provider/                 # 图标资源与 Java/Kotlin 双向 LineMarker
├── scanner/                  # 基于 AnnotatedElementsSearch 的全工程端点扫描
├── settings/                 # 全局持久化配置与 Settings 页面
└── ui/                       # 多标签页工具窗口、接口树、搜索弹窗、体检报告与调试面板
```

</details>

---

## 反馈与支持

- 当前版本：**`1.2.2`**（完整变更记录见 [CHANGELOG.md](./CHANGELOG.md)）
- 遇到问题或有新功能想法？欢迎提交 [GitHub Issue](https://github.com/sxhjlzl/api-helper/issues)。
- 如果 **ApiHelper** 提升了你的开发效率，欢迎给仓库点亮一颗 **Star** 支持！
