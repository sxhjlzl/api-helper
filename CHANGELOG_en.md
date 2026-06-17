# Changelog

All notable changes to the ApiHelper plugin are documented in this file. Versions follow [Semantic Versioning](https://semver.org/).

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

[1.0.1]: https://github.com/sxhjlzl/api-helper/releases/tag/v1.0.1
[1.0.0]: https://github.com/sxhjlzl/api-helper/releases/tag/v1.0.0
