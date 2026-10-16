# Changelog

All notable changes to the ApiHelper plugin are documented in this file. Versions follow [Semantic Versioning](https://semver.org/).

## [1.1.0] - 2026-10-16

### Added

- **Multi-Tab Tool Window & Collapsible Split Workspace**:
  - Top-level native tool window tabs: **Interfaces**, **Base URL**, **History**, and **Global Params**, with dedicated visual tables for managing environment Base URLs, global headers, and request history.
  - Collapsible vertical split workspace inside **Interfaces** (**Interfaces** on top + **Debug** on bottom); the debug pane starts collapsed to maximize endpoint browsing space and expands automatically when debugging an endpoint.
- **Global Search API Endpoint Popup**: Added `Navigate → Search API Endpoint...` (`Ctrl+Alt+\` / `Cmd+Opt+\` and tool window header button) to search endpoints across the project with keyboard shortcuts (`Enter` to jump to source, `Shift+Enter` to jump to counterpart, `Ctrl/Cmd+Enter` to debug).
- **Global Common Headers & Multi-Environment Base URLs**: Configure global common request headers (e.g. `Authorization`, `X-Tenant-Id`) and environment Base URLs (e.g. `Local`, `Dev`, `Test`) under Settings → Tools → ApiHelper or the dedicated tool window tabs, with a quick environment switcher in the API debugger.
- **Copy as cURL Everywhere & Gutter Copy API Docs**: Export standard `curl` commands from the endpoint tree context menu, Controller editor gutter menu, and the API debugger toolbar; also added "Copy API Docs" directly to the Controller gutter context menu.
- **Debug Request History & Per-Endpoint Draft Persistence**: Record up to 30 recent debug requests and responses in the current project session for one-click replay, alongside automatic per-endpoint draft persistence and a "Reset" button.
- **Group by Module & Pairing Status Filter**: Toggle `Module → Class` grouping, filter endpoints by pairing status (`All Endpoints` / `Paired Only` / `Unpaired (Orphan) Only`), and view pairing indicator icons directly on endpoint tree nodes.
- **Select Opened Endpoint**: Added a toolbar action to locate and select the endpoint corresponding to the current editor caret position.
- **Recursive Nested JSON Drafts, Annotation Examples & Composite Meta-Annotations**: Expand nested DTOs and collections (`List<T>`) into formatted JSON request bodies, honoring `@JsonProperty` / `@SerializedName` / `@JSONField` field names and `@Schema(example)` / `@ApiModelProperty(example)` / `@Parameter(example)` values, and recognize custom composite Spring meta-annotations (e.g. `@ApiV1RestController` annotated with `@RestController` and `@RequestMapping`).

## [1.0.4] - 2026-10-15

### Fixed

- Fixed deprecated IntelliJ Platform API usages in IntelliJ IDEA 2026.3 EAP by replacing `CommonProxy` with `JdkProxyProvider` and `DynamicBundle(String)` with `DynamicBundle(Class, String)`.
- Fixed variable shadowing in `EndpointParameterResolver` so source comment caches are properly reused when expanding nested response object fields.
- Fixed debug parameter tables losing uncommitted cell edits when clicking "Send" directly while a cell editor is active.
- Fixed missing Simplified Chinese translation for `debug.action.upload`.

### Improved

- Upgraded the tool window search bar to native `SearchTextField` (with built-in clear button and `ESC` to close) and enabled `TreeSpeedSearch` on endpoint trees.
- Display `[filtered/total]` counts on `Controller` and `Feign / HttpExchange` tabs while filtering, and show active parameter counts on Debug request tabs (`Query`, `Path`, `Headers`, `Cookies`).
- Added "Jump to Source" to endpoint tree context menus and unified balloon notifications for copying URLs, copying API docs, and counterpart navigation.
- Enhanced API debugger UX: press `Enter` in the URL field to send, validate empty URLs and missing upload files before sending, colorize response status codes (`2xx`/`3xx`/`4xx-5xx`), auto-detect response body format from `Content-Type` and payload, and copy response bodies with one click.

## [1.0.3] - 2026-08-26

### Fixed

- Fixed counterpart matching failing entirely in projects with a configured `server.servlet.context-path`: matching now uses the relative path without the context-path, while display and copy keep the full URL.
- Fixed matching failures when path variable names differ between the two sides (e.g. `{id}` vs `{userId}`): path variables are now normalized before comparison.
- Fixed a brief empty-cache window during full cache rebuilds that caused gutter icon flicker or false "no counterpart" results: caches are now replaced atomically as immutable snapshots.
- Fixed in-flight debug requests being impossible to cancel: the send button turns into "Stop" while sending and can abort the request.
- Fixed debug requests ignoring the IDE proxy, which broke corporate network environments: requests now follow the IDE global HTTP proxy settings.
- Fixed hardcoded debug timeouts: connect timeout and overall request timeout are now configurable under Settings → Tools → ApiHelper.

### Improved

- Counterpart matching queries now use an inverted index keyed by normalized path, reducing per-query cost from O(N) to O(1) and improving LineMarker rendering in large projects.
- Incremental cache rebuilds triggered by rapid typing are now debounced and merged into a single background rebuild per window.
- In-flight debug requests are cancelled automatically when the debug panel is disposed.

## [1.0.2] - 2026-06-24

### Added

- Manual full endpoint refresh in the tool window, with Controller / Feign endpoint counts.
- Native IDE-style endpoint context menus with a new "Copy API Docs" action.
- "Copy API Docs" now generates standard Markdown API documentation without frontend code generation instructions.
- API docs extract method Javadocs, field Javadocs, `@param` descriptions, and Swagger `@Operation`, `@Parameter`, `@Schema`, `@ApiOperation`, and `@ApiModelProperty` descriptions.
- API docs expand Path, Query, Header, Cookie, request body, and response fields.
- Query parameters expand `@SpringQueryMap` / `@QueryMap` DTOs and unannotated DTO parameters for GET / DELETE / HEAD / ANY endpoints.
- Request body parameters expand `@RequestBody` DTOs and unannotated DTO parameters in non-Query scenarios such as POST / PUT / PATCH.
- Response fields expand DTOs and common generic response structures such as `DllResult<T>`, `DllPageResult<T>`, and `List<T>`, including fields like `data[].field` and `records[].field`.
- Multi-keyword tool window search across HTTP method, URL, class name, method name, and module name.

### Fixed

- Fixed `Outdated stub in index` failures caused by transient IntelliJ index inconsistency during endpoint scans.
- Fixed `Smart pointers must not be created during PSI changes` failures by deferring PSI listener cache rebuilds.
- Made endpoint scanning more resilient so a failed annotation query or class parse skips only the current item instead of aborting the whole scan.
- Path parameter prefill now merges URL placeholders with `@PathVariable` parameters and preserves annotation-derived sample values first.
- Optimized binary and form-data file debugging requests to stream files instead of loading whole files into memory.
- Limited debug response body preview to the first 5 MB to avoid excessive memory usage.
- Cached source field comments during API doc generation to reduce repeated source text scans for large DTOs and files.
- Avoid repeated full scans in the tool window when a project genuinely has no Controller or Feign endpoints.
- Removed module suffixes from endpoint class group labels and removed the top update-time display for a cleaner UI.

## [1.0.1] - 2026-06-17

### Changed

- Removed the debug action from Feign / HttpExchange endpoint list context menus to avoid opening the debugger from client declarations.
- Prefill Path, Query, Header, Cookie, and JSON Body draft parameters automatically when opening the debugger from an endpoint.
- Controller gutter context menus now provide both "Debug endpoint" and "Copy URL" actions.
- Feign / HttpExchange gutter icons keep left-click navigation only and no longer provide context-menu actions.

## [1.0.0] - 2026-06-17

### Added

- Spring MVC Controller, OpenFeign, and Spring 6 `@HttpExchange` endpoint scanning
- Bidirectional gutter navigation between Controller and Feign / HttpExchange methods
- Copy resolved endpoint URLs from gutter actions
- ApiHelper tool window for browsing Controller and Feign / HttpExchange endpoints
- Endpoint search, expand, collapse, debug, counterpart navigation, and URL copy actions
- Lightweight API debugger with Query, Path, Header, Cookie, and multiple Body modes
- form-data file upload, binary file upload, JSON request formatting, and automatic JSON response formatting
- Spring context-path, servlet path, profile, server.port, and placeholder resolution
- Java and Kotlin source support
- English and Simplified Chinese UI

[1.1.0]: https://github.com/sxhjlzl/api-helper/releases/tag/v1.1.0
[1.0.4]: https://github.com/sxhjlzl/api-helper/releases/tag/v1.0.4
[1.0.3]: https://github.com/sxhjlzl/api-helper/releases/tag/v1.0.3
[1.0.2]: https://github.com/sxhjlzl/api-helper/releases/tag/v1.0.2
[1.0.1]: https://github.com/sxhjlzl/api-helper/releases/tag/v1.0.1
[1.0.0]: https://github.com/sxhjlzl/api-helper/releases/tag/v1.0.0
