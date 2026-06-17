# 更新日志

本文件记录 ApiHelper 插件的版本变更。版本号遵循 [Semantic Versioning](https://semver.org/lang/zh-CN/)。

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

[1.0.0]: https://github.com/sxhjlzl/api-helper/releases/tag/v1.0.0
