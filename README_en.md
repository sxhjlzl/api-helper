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
  <a href="https://plugins.jetbrains.com/plugin/32327-apihelper">Marketplace</a>
  ·
  <a href="https://github.com/sxhjlzl/api-helper/issues">Issues</a>
  ·
  <a href="./README.md">中文</a>
</p>

ApiHelper supports Spring MVC Controllers, Spring Cloud OpenFeign, and Spring 6 `@HttpExchange` declarative HTTP clients. It helps you locate endpoints, copy resolved paths, and send debug requests directly from the IDE.

## Why ApiHelper

In Spring projects, endpoint-related work is often scattered across Controllers, Feign clients, configuration files, API docs, debug tools, and global search. ApiHelper brings these common actions back into IDEA:

- Jump from a client declaration to its Controller method.
- Navigate back from a Controller to the matching client declaration.
- Copy the resolved endpoint URL directly.
- Copy standard Markdown API documentation with parameter and response field descriptions.
- Browse project endpoints from a focused tool window.
- Send a lightweight debug request without leaving the IDE.

## Features

- Bidirectional gutter navigation between `@FeignClient` / `@HttpExchange` methods and `@RestController` methods
- Copy resolved endpoint URLs from gutter actions
- Browse Controller and Feign / HttpExchange endpoints in the ApiHelper tool window
- Multi-keyword search, manual refresh, expand, collapse, counterpart navigation, and URL copy actions; Controller endpoints support context-menu debugging
- Copy standard Markdown API documentation from endpoint context menus
- API docs extract method comments, field Javadocs, Swagger annotation descriptions, and `@param` descriptions
- API docs expand Query DTOs, Request Body DTOs, and response DTO fields
- Supports `@SpringQueryMap` / `@QueryMap` and expands unannotated GET / DELETE / HEAD DTO parameters as Query parameters
- Supports common generic response structures such as `DllResult<T>`, `DllPageResult<T>`, and `List<T>`
- Lightweight API debugger with Query, Path, Header, Cookie, and multiple Body modes
- Automatically prefill Path, Query, Header, Cookie, and JSON Body drafts when opening the debugger from an endpoint
- Automatic JSON response formatting; large responses preview only the first 5 MB to avoid excessive memory usage
- Binary and form-data file requests are sent with streaming publishers instead of loading whole files into memory
- Spring context-path, servlet path, profile, and placeholder resolution
- Java and Kotlin support via UAST
- English and Simplified Chinese UI

## Preview

| Bidirectional navigation | Tool window | Lightweight debugging |
| --- | --- | --- |
| Navigate between Feign / HttpExchange and Controller methods | Browse Controller and client endpoints in one place | Quickly verify endpoint responses inside IDEA |

## Installation

ApiHelper is available on JetBrains Marketplace: [ApiHelper - JetBrains Marketplace](https://plugins.jetbrains.com/plugin/32327-apihelper)

You can also install it from IDEA via `Settings` -> `Plugins` by searching for `ApiHelper`.

Local installation:

```bash
./gradlew :buildPlugin
```

The plugin zip is generated at:

```text
build/distributions/api-helper-<version>.zip
```

Install it in IDEA via `Settings` -> `Plugins` -> `Install Plugin from Disk...`.

## Usage

After opening a Spring Web, OpenFeign, or `@HttpExchange` project, ApiHelper scans endpoints asynchronously and warms its cache.

- Click gutter arrows to navigate to counterpart endpoints.
- Right-click Controller gutter icons to debug endpoints or copy resolved URLs.
- Feign / HttpExchange gutter icons support left-click navigation only.
- Open the right-side `ApiHelper` tool window to browse endpoints or use the debugger.
- Right-click a Controller endpoint row to debug, navigate to counterpart, or copy URL.
- Right-click a Feign / HttpExchange endpoint row to navigate to counterpart or copy URL.
- Right-click an endpoint row and choose "Copy API Docs" to copy standard Markdown documentation with basic info, description, Path / Query / Header / Cookie parameters, request body, response fields, and field descriptions.
- Unannotated DTO parameters in GET / DELETE / HEAD endpoints are expanded as Query parameters; unannotated DTO parameters in POST / PUT / PATCH endpoints are expanded as request body fields.
- Generic wrapper responses keep wrapper fields and expand actual data fields. For example, `DllResult<List<UserRes>>` produces response fields such as `data[].id` and `data[].name`.

## Settings

Path: `Settings` -> `Tools` -> `ApiHelper`

You can manually specify the Spring active profile. Leave it empty to let the plugin infer profiles from project configuration.

## Compatibility

| Item | Description |
| --- | --- |
| Target IDE | IntelliJ IDEA 2024.3+ |
| Required plugins | Java, Kotlin |
| Source support | Java, Kotlin |
| Supported frameworks | Spring MVC, Spring Cloud OpenFeign, Spring 6 `@HttpExchange` |

## Development

```bash
./gradlew :compileKotlin
./gradlew :test
./gradlew :check
./gradlew :buildPlugin
./gradlew :runIde
```

Stack:

- Kotlin 2.3.20
- JDK 21
- Gradle 9.5.0
- IntelliJ Platform Gradle Plugin 2.12.0
- Target IDE: IntelliJ IDEA 2024.3+

## Structure

```text
src/main/kotlin/com/lizhuolun/apihelper/
  cache/        Cache services
  config/       Spring config and placeholder resolution
  core/         HTTP mapping model and annotation parsing
  listener/     Startup warmup and PSI listeners
  provider/     Gutter icons and navigation
  scanner/      Endpoint scanning
  settings/     Settings UI and persistent state
  ui/           Tool window, endpoint tree, and debugger

src/main/resources/
  META-INF/plugin.xml
  icons/
  messages/
```

## Version

Current version: `1.0.2`

## Feedback And Support

You are welcome to try ApiHelper and share suggestions or issues through [GitHub Issues](https://github.com/sxhjlzl/api-helper/issues). If the plugin helps your daily workflow, a GitHub Star is appreciated.
