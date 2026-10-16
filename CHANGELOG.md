# 更新日志

本文件记录 ApiHelper 插件的版本变更。版本号遵循 [Semantic Versioning](https://semver.org/lang/zh-CN/)。

## [1.1.0] - 2026-10-16

### 新增

- **工具窗口多标签页与可折叠分栏工作区**：
  - 顶部采用 IDE 原生一级标签页：**`接口`**、**`前置 URL`**、**`请求历史`**、**`全局参数`**，可在工具窗口内直接通过表格管理多环境前置 URL、全局公共 Header 与查看回放历史请求。
  - `接口` 主工作区改为上下可折叠分栏（上半区 **`接口`** + 下半区 **`调试`**），默认折叠底部调试面板以最大化接口浏览视野，触发调试时自动展开并支持拖拽调节高度比例。
- **全局快捷接口搜索弹窗（Search API Endpoint）**：新增 `Navigate → 搜索 API 接口...`（快捷键 `Ctrl+Alt+\` / `Cmd+Opt+\` 及工具窗口标题栏按钮），支持多关键词检索全工程接口，按 `Enter` 跳转源码、`Shift+Enter` 直接跳转对端接口、`Ctrl/Cmd+Enter` 直接进入接口调试。
- **全局公共 Header 与多环境 Base URL 切换**：在 Settings → Tools → ApiHelper 及工具窗口独立标签页中支持配置全局公共请求头（如 `Authorization`、`X-Tenant-Id`）与多环境 Base URL（如 `Local`、`Dev`、`Test`），调试面板顶部支持一键切换环境。
- **全场景一键复制为 cURL**：在端点列表右键菜单、编辑器 Controller Gutter 右键菜单与调试面板工具栏均支持一键导出包含 Path、Query、全局与接口级 Header、Cookie、Body 的标准 `curl` 命令；Controller Gutter 右键菜单同步支持直接“复制接口文档”。
- **调试请求历史记录（History）与草稿记忆**：自动记录当前会话最近 30 条已发送的请求与响应快照，支持在“请求历史”标签页或调试栏弹窗中一键回放任意历史请求与响应结果；同时按端点记忆未发送的参数草稿，支持“重置”回源码初始参数。
- **按模块分组视图与配对状态筛选**：工具栏新增“按模块分组”开关与“按配对状态筛选”（全部接口 / 仅显示已配对 / 仅显示未配对孤儿接口），并在已配对的端点树节点旁展示方向标记，方便快速排查失效的孤儿接口。
- **定位当前编辑器接口**：工具栏新增“定位当前编辑器接口”按钮，可根据当前打开源码的光标位置自动在 Controller 或 Feign 树中展开并选中对应接口节点。
- **智能嵌套 JSON 生成与组合元注解识别**：支持递归展开嵌套 DTO 与集合（`List<T>`）生成结构化 JSON 请求体草稿，识别 `@JsonProperty` / `@SerializedName` / `@JSONField` 字段名以及 `@Schema(example)` / `@ApiModelProperty(example)` 示例值；同时支持识别一层自定义 Spring 组合元注解（如封装了 `@RestController` 与 `@RequestMapping` 的 `@ApiV1RestController`）。

## [1.0.4] - 2026-10-15

### 修复

- 修复在 IntelliJ IDEA 2026.3 EAP 下的废弃 API 告警：用 `JdkProxyProvider` 替代已标记移除的 `CommonProxy`，用 `DynamicBundle(Class, String)` 构造器替代单参构造器。
- 修复 `EndpointParameterResolver` 展开嵌套响应对象字段时局部变量遮蔽导致源码注释缓存未复用的问题。
- 修复调试参数表格在编辑单元格后直接点击“发送”按钮时未自动提交当前单元格输入的问题。
- 修复中文语言包中 `debug.action.upload` 未汉化的问题。

### 优化

- 工具窗口搜索框升级为 IDE 原生 `SearchTextField`（自带清空按钮，支持按 `ESC` 快捷收起），并为端点树接入 `TreeSpeedSearch` 键盘直输检索。
- 工具窗口 `Controller` 与 `Feign / HttpExchange` Tab 在过滤时实时显示 `[过滤数/总数]`，调试页请求参数 Tab（`Query`、`Path`、`Headers`、`Cookies`）动态显示参数数量。
- 端点列表右键菜单新增“跳转到源码”，并为复制 URL、复制接口文档、未找到对端接口等操作统一补齐气泡通知反馈。
- 调试面板体验升级：支持 URL 输入框按 `Enter` 直接发送、发送前校验 URL 与本地文件是否存在、响应状态码按 `2xx`/`3xx`/`4xx-5xx` 语义化着色、根据响应头与内容自动切换响应格式视图，以及新增响应内容一键复制按钮。

## [1.0.3] - 2026-08-26

### 修复

- 修复配置了 `server.servlet.context-path` 的工程两端永远无法匹配的问题：两端匹配改用不含 context-path 的相对路径，展示与复制仍保留完整 URL。
- 修复两端路径变量命名不一致（如 `{id}` 与 `{userId}`）时匹配失败的问题：路径变量统一归一化后再比较。
- 修复缓存全量重建瞬间出现空窗期，导致 gutter 图标闪烁或跳转误报无对端的问题：改为不可变快照整体原子替换。
- 修复调试页请求发出后无法取消、只能等待超时的问题：发送中按钮变为"停止"，可随时中止在途请求。
- 修复调试请求不走 IDE 代理导致企业内网环境不可达的问题：现跟随 IDE 全局 HTTP 代理配置。
- 修复调试请求超时硬编码的问题：连接超时与整体超时移至 Settings → Tools → ApiHelper 配置。

### 优化

- 两端匹配查询改为基于归一化路径的倒排索引，单次查询由 O(N) 降为 O(1)，提升大型工程 LineMarker 渲染性能。
- 连续输入触发的 PSI 增量缓存重建合并防抖，同一窗口期内的变更只执行一次后台重建。
- 调试面板销毁时自动取消在途请求，避免后台线程继续占用连接。

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

[1.1.0]: https://github.com/sxhjlzl/api-helper/releases/tag/v1.1.0
[1.0.4]: https://github.com/sxhjlzl/api-helper/releases/tag/v1.0.4
[1.0.3]: https://github.com/sxhjlzl/api-helper/releases/tag/v1.0.3
[1.0.2]: https://github.com/sxhjlzl/api-helper/releases/tag/v1.0.2
[1.0.1]: https://github.com/sxhjlzl/api-helper/releases/tag/v1.0.1
[1.0.0]: https://github.com/sxhjlzl/api-helper/releases/tag/v1.0.0
