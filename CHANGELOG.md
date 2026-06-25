# 更新日志

本文件记录 ApiHelper 插件的版本变更。版本号遵循 [Semantic Versioning](https://semver.org/lang/zh-CN/)。

## [1.0.2] - 2026-06-24

### 新增

- 工具窗口支持手动全量刷新端点，并显示 Controller / Feign 端点数量。
- 端点列表右键菜单改为 IDE 原生弹窗样式，并新增“复制接口文档”。
- “复制接口文档”生成标准 Markdown 接口文档，不再附加前端代码生成要求。
- 接口文档支持识别方法 Javadoc、字段 Javadoc、`@param`、Swagger `@Operation`、`@Parameter`、`@Schema`、`@ApiOperation`、`@ApiModelProperty` 等说明。
- 接口文档支持展开 Path、Query、Header、Cookie、请求体与返回参数字段。
- Query 参数支持展开 `@SpringQueryMap` / `@QueryMap` DTO，以及 GET / DELETE / HEAD / ANY 未标注 DTO 参数。
- 请求体参数支持展开 `@RequestBody` DTO，以及 POST / PUT / PATCH 等非 Query 场景下的未标注 DTO 参数。
- 返回参数支持展开 DTO 字段，并支持 `DllResult<T>`、`DllPageResult<T>`、`List<T>` 等常见泛型结构，例如 `data[].field`、`records[].field`。
- 工具窗口搜索支持多关键词组合匹配，可按 HTTP 方法、URL、类名、方法名与模块名过滤。

### 修复

- 修复 IntelliJ 索引瞬态不一致导致 `Outdated stub in index` 异常影响端点扫描的问题。
- 修复 PSI 变更事件中创建 Smart Pointer 导致 `Smart pointers must not be created during PSI changes` 异常的问题。
- 优化扫描容错，单个注解查询或单个类解析失败时跳过当前项，不中断整次扫描。
- 调试页 Path 参数预填会合并 URL 占位符与 `@PathVariable`，并优先保留注解解析出的示例值。
- 优化调试页大文件上传与 form-data 文件上传，改为流式发送，避免一次性读入内存。
- 优化调试页响应体读取，大响应默认只预览前 5 MB，避免占用过多内存。
- 优化接口文档字段注释解析，同一次解析中缓存源码字段注释，减少大 DTO / 大文件下的重复源码扫描。
- 优化工具窗口刷新逻辑，避免项目本身缺少 Controller 或 Feign 时反复触发全量扫描。
- 去掉接口列表类名后面的模块名称展示，去掉顶部更新时间展示，保持界面更简洁。

## [1.0.1] - 2026-06-17

### 变更

- Feign / HttpExchange 接口列表右键菜单不再提供调试入口，避免从客户端声明侧误进入调试。
- 从接口跳转到调试页时，会根据方法参数自动预填 Path、Query、Header、Cookie 与 JSON Body 草稿。
- Controller 方法 gutter 图标右键菜单支持“调试接口”和“复制 URL”两个操作。
- Feign / HttpExchange 方法 gutter 图标只保留左键跳转，不再提供右键菜单操作。

## [1.0.0] - 2026-06-17

### 新增

- 支持 Spring MVC Controller、OpenFeign 与 Spring 6 `@HttpExchange` 端点扫描
- 支持 Controller 与 Feign / HttpExchange 方法之间双向 gutter 导航
- 支持 gutter 右键复制解析后的接口 URL
- 新增 ApiHelper 工具窗口，集中展示 Controller 与 Feign / HttpExchange 端点
- 支持接口搜索、展开、收起、右键调试、右键跳转对端和右键复制 URL
- 新增轻量 API 调试页，支持 Query、Path、Header、Cookie 与多种 Body 类型
- 支持 form-data 文件上传、binary 文件上传、JSON 请求格式化与 JSON 响应自动格式化
- 支持读取 Spring 配置中的 context-path、servlet path、profile、server.port 与占位符
- 支持 Java 与 Kotlin 源码
- 支持中英双语界面

[1.0.2]: https://github.com/sxhjlzl/api-helper/releases/tag/v1.0.2
[1.0.1]: https://github.com/sxhjlzl/api-helper/releases/tag/v1.0.1
[1.0.0]: https://github.com/sxhjlzl/api-helper/releases/tag/v1.0.0
