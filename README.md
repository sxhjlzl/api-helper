<p align="center">
  <img src="docs/images/cover.png" alt="ApiHelper cover">
</p>

<p align="center">
  <a href="https://plugins.jetbrains.com/plugin/32327-apihelper"><img src="https://img.shields.io/jetbrains/plugin/v/32327-apihelper?label=Marketplace&color=0f766e" alt="JetBrains Marketplace version"></a>
  <a href="https://plugins.jetbrains.com/plugin/32327-apihelper"><img src="https://img.shields.io/jetbrains/plugin/d/32327-apihelper?label=downloads&color=2563eb" alt="JetBrains Marketplace downloads"></a>
  <a href="https://github.com/sxhjlzl/api-helper/blob/main/LICENSE"><img src="https://img.shields.io/github/license/sxhjlzl/api-helper?color=64748b" alt="License"></a>
  <a href="https://github.com/sxhjlzl/api-helper/stargazers"><img src="https://img.shields.io/github/stars/sxhjlzl/api-helper?style=social" alt="GitHub stars"></a>
</p>

<p align="center">
  <a href="https://plugins.jetbrains.com/plugin/32327-apihelper">插件市场</a>
  ·
  <a href="https://github.com/sxhjlzl/api-helper/issues">反馈问题</a>
  ·
  <a href="./README_en.md">English</a>
</p>

ApiHelper 支持 Spring MVC Controller、Spring Cloud OpenFeign 以及 Spring 6 `@HttpExchange` 声明式 HTTP 客户端，适合在日常开发中快速定位接口、复制路径并直接发起调试请求。

## 为什么需要 ApiHelper

在 Spring 项目里，接口相关操作经常分散在多个地方：Controller、Feign Client、配置文件、接口文档、调试工具和全局搜索。ApiHelper 把这些高频动作收回到 IDEA 里：

- 从客户端声明快速跳到 Controller。
- 从 Controller 反向找到调用方声明。
- 直接复制解析后的接口 URL。
- 复制标准 Markdown 接口文档，包含参数与返回字段说明。
- 在工具窗口里集中浏览项目接口。
- 对接口发起一次轻量调试请求。

## 功能

- `@FeignClient` / `@HttpExchange` 接口方法与 `@RestController` 方法双向 gutter 跳转
- Controller gutter 右键调试接口或复制解析后的接口 URL
- ApiHelper 工具窗口集中展示 Controller 与 Feign / HttpExchange 端点
- 支持多关键词搜索、手动刷新、展开、收起、跳转对端、复制 URL；Controller 接口支持右键调试
- 端点右键支持复制标准 Markdown 接口文档
- 接口文档自动识别方法注释、字段 Javadoc、Swagger 注解说明与 `@param` 参数说明
- 接口文档自动展开 Query DTO、Request Body DTO 与返回 DTO 字段
- 支持 `@SpringQueryMap` / `@QueryMap` 与 GET / DELETE / HEAD 未标注 DTO 参数展开为 Query 参数
- 支持 `DllResult<T>`、`DllPageResult<T>`、`List<T>` 等常见泛型返回结构的字段展开
- 内置轻量 API 调试页，支持 Query、Path、Header、Cookie 和多种 Body 类型
- 从接口进入调试页时自动预填 Path、Query、Header、Cookie 与 JSON Body 草稿
- 支持 JSON 响应自动格式化；大响应默认只预览前 5 MB，避免占用过多内存
- binary 与 form-data 文件调试请求使用流式发送，避免大文件一次性读入内存
- 自动解析 Spring 配置中的 context-path、servlet path、profile 与占位符
- 基于 UAST 同时支持 Java 与 Kotlin
- 中英双语界面

## 预览

| 双向导航 | 工具窗口 | 轻量调试 |
| --- | --- | --- |
| Feign / HttpExchange 与 Controller 互相跳转 | 集中展示 Controller 与客户端接口 | 在 IDEA 内快速验证接口响应 |

## 安装

插件已上架 JetBrains Marketplace：[ApiHelper - JetBrains Marketplace](https://plugins.jetbrains.com/plugin/32327-apihelper)

也可以在 IDEA 中选择 `Settings` -> `Plugins`，搜索 `ApiHelper` 后直接安装。

本地安装：

```bash
./gradlew :buildPlugin
```

构建产物位于：

```text
build/distributions/api-helper-<version>.zip
```

在 IDEA 中选择 `Settings` -> `Plugins` -> `Install Plugin from Disk...` 安装该 zip。

## 使用

打开带有 Spring Web、OpenFeign 或 `@HttpExchange` 注解的项目后，ApiHelper 会异步扫描端点并预热缓存。

- 在编辑器 gutter 中点击箭头可跳转到对端接口。
- Controller gutter 右键可调试接口或复制解析后的 URL。
- Feign / HttpExchange gutter 仅支持左键跳转。
- 打开右侧 `ApiHelper` 工具窗口，可浏览接口列表或切换到调试页。
- 在 Controller 接口列表中右键具体接口，可调试、跳转对端或复制 URL。
- 在 Feign / HttpExchange 接口列表中右键具体接口，可跳转对端或复制 URL。
- 在接口列表中右键具体接口并选择“复制接口文档”，可复制标准 Markdown 文档。文档包含基本信息、接口说明、Path / Query / Header / Cookie 参数、请求体、返回参数以及字段说明。
- GET / DELETE / HEAD 等接口中未标注的 DTO 参数会按 Query 参数展开；POST / PUT / PATCH 等接口中未标注的 DTO 参数会按请求体字段展开。
- 返回值是包装泛型时，会保留包装字段并展开实际数据字段，例如 `DllResult<List<UserRes>>` 会生成 `data[].id`、`data[].name` 这类返回参数。

## 设置

路径：`Settings` -> `Tools` -> `ApiHelper`

可手动指定 Spring active profile。留空时插件会尝试从项目配置中自动推断。

## 兼容性

| 项 | 说明 |
| --- | --- |
| 目标 IDE | IntelliJ IDEA 2024.3+ |
| 依赖插件 | Java、Kotlin |
| 支持源码 | Java、Kotlin |
| 支持框架 | Spring MVC、Spring Cloud OpenFeign、Spring 6 `@HttpExchange` |

## 开发

```bash
./gradlew :compileKotlin
./gradlew :test
./gradlew :check
./gradlew :buildPlugin
./gradlew :runIde
```

技术栈：

- Kotlin 2.3.20
- JDK 21
- Gradle 9.5.0
- IntelliJ Platform Gradle Plugin 2.12.0
- Target IDE: IntelliJ IDEA 2024.3+

## 项目结构

```text
src/main/kotlin/com/lizhuolun/apihelper/
  cache/        缓存服务
  config/       Spring 配置读取与占位符解析
  core/         HTTP 映射模型与注解解析
  listener/     启动预热与 PSI 监听
  provider/     gutter 图标与导航
  scanner/      端点扫描
  settings/     设置页与持久化配置
  ui/           工具窗口、接口树与调试面板

src/main/resources/
  META-INF/plugin.xml
  icons/
  messages/
```

## 版本

当前版本：`1.0.2`

## 反馈与支持

欢迎下载使用，也欢迎通过 [GitHub Issues](https://github.com/sxhjlzl/api-helper/issues) 提出建议或反馈问题。如果 ApiHelper 对你有帮助，欢迎给项目点一个 Star。
