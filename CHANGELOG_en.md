# Changelog

All notable changes to the ApiHelper plugin are documented in this file. Versions follow [Semantic Versioning](https://semver.org/).

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

[1.0.2]: https://github.com/sxhjlzl/api-helper/releases/tag/v1.0.2
[1.0.1]: https://github.com/sxhjlzl/api-helper/releases/tag/v1.0.1
[1.0.0]: https://github.com/sxhjlzl/api-helper/releases/tag/v1.0.0
