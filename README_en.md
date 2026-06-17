# ApiHelper

An IntelliJ IDEA plugin for Spring APIs, providing endpoint navigation, endpoint browsing, and lightweight API debugging.

ApiHelper supports Spring MVC Controllers, Spring Cloud OpenFeign, and Spring 6 `@HttpExchange` declarative HTTP clients. It helps you locate endpoints, copy resolved paths, and send debug requests directly from the IDE.

## Features

- Bidirectional gutter navigation between `@FeignClient` / `@HttpExchange` methods and `@RestController` methods
- Copy resolved endpoint URLs from gutter actions
- Browse Controller and Feign / HttpExchange endpoints in the ApiHelper tool window
- Search, expand, collapse, debug endpoints, and copy URLs from the endpoint list
- Lightweight API debugger with Query, Path, Header, Cookie, and multiple Body modes
- Automatic JSON response formatting
- Spring context-path, servlet path, profile, and placeholder resolution
- Java and Kotlin support via UAST
- English and Simplified Chinese UI

## Installation

Install ApiHelper from JetBrains Marketplace by searching for `ApiHelper`.

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
- Right-click gutter arrows to copy resolved URLs.
- Open the right-side `ApiHelper` tool window to browse endpoints or use the debugger.
- Right-click a concrete endpoint row to debug, navigate to counterpart, or copy URL.

## Settings

Path: `Settings` -> `Tools` -> `ApiHelper`

You can manually specify the Spring active profile. Leave it empty to let the plugin infer profiles from project configuration.

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

Current version: `1.0.0`
