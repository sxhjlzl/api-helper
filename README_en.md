<p align="center">
  <img src="docs/images/cover.png" alt="ApiHelper Cover" width="100%">
</p>

<p align="center">
  <a href="https://plugins.jetbrains.com/plugin/32327-apihelper"><img src="https://img.shields.io/jetbrains/plugin/v/32327-apihelper?label=Marketplace&color=0ea5e9" alt="JetBrains Marketplace version"></a>
  <a href="https://plugins.jetbrains.com/plugin/32327-apihelper"><img src="https://img.shields.io/jetbrains/plugin/d/32327-apihelper?label=Downloads&color=2563eb" alt="JetBrains Marketplace downloads"></a>
  <a href="https://github.com/sxhjlzl/api-helper/blob/main/LICENSE"><img src="https://img.shields.io/github/license/sxhjlzl/api-helper?color=10b981" alt="License"></a>
  <a href="https://github.com/sxhjlzl/api-helper/stargazers"><img src="https://img.shields.io/github/stars/sxhjlzl/api-helper?style=social" alt="GitHub stars"></a>
</p>

<p align="center">
  <a href="https://plugins.jetbrains.com/plugin/32327-apihelper">JetBrains Marketplace</a>
  ·
  <a href="./CHANGELOG_en.md">Changelog</a>
  ·
  <a href="https://github.com/sxhjlzl/api-helper/issues">Report an Issue</a>
  ·
  <a href="./README.md">简体中文</a>
</p>

---

**ApiHelper** is an all-in-one IntelliJ IDEA assistant for **Spring MVC Controllers**, **Spring Cloud OpenFeign**, and **Spring 6 `@HttpExchange`** declarative HTTP clients. It brings **bidirectional source navigation**, **global endpoint search**, **multi-environment API debugging**, **cURL export**, and **Markdown API documentation generation** directly into your IDE.

## Key Highlights

### 1. Bidirectional Gutter Navigation & Global Endpoint Search

<p align="center">
  <img src="docs/images/feature-navigation.png" alt="Bidirectional Gutter Navigation and Global Search" width="100%">
</p>

- **Controller ↔ Feign / HttpExchange Navigation**: Left-click the editor gutter icon to jump between client declarations and Controller methods, even when path variable names differ (`{id}` vs `{orderId}`) or custom composite meta-annotations (e.g. `@ApiV1RestController`) are used.
- **Controller Gutter Context Menu**: Right-click any Controller gutter icon to **Debug endpoint**, **Copy URL**, **Copy as cURL**, or **Copy API Docs** directly from the editor.
- **Global Search API Endpoint Popup (`Ctrl+Alt+\` / `Cmd+Opt+\`)**: Invoke `Navigate → Search API Endpoint...` from anywhere:
  - `Enter`: Jump to endpoint source code
  - `Shift + Enter`: Jump directly to the counterpart endpoint
  - `Ctrl / Cmd + Enter`: Open the endpoint in the API debugger

### 2. Multi-Tab & Collapsible Split Workspace

<p align="center">
  <img src="docs/images/feature-workspace.png" alt="Multi-Tab and Collapsible Workspace" width="100%">
</p>

- **Four Native Top-Level Tabs**:
  - **Interfaces**: Features a collapsible vertical split workspace with the endpoint tree on top and the **Debug** pane on the bottom (collapsed by default to maximize browsing space, and expanded automatically when debugging).
  - **Base URL**: Manage multiple environment Base URLs (`Local`, `Dev`, `Staging`, `Prod`) in a visual table and switch the active environment with one click.
  - **History**: Automatically records the last 30 debug requests and responses in the current session; double-click or press `Enter` on any entry to replay the full request and response.
  - **Global Params**: Manage global common request headers (such as `Authorization: Bearer ...` and `X-Tenant-Id`) that are automatically merged into every debug request and exported cURL command.
- **Module Grouping & Orphan Endpoint Detection**:
  - Toggle **Group by Module** (`Module → Class → Endpoint`) and filter by **Pairing Status** (`All Endpoints` / `Paired Only` / `Unpaired (Orphan) Only`) to quickly spot unmatched Feign declarations.
  - Includes **Select Opened Endpoint**, native `SearchTextField` (`ESC` to dismiss), and `TreeSpeedSearch`.

### 3. Smart Nested JSON Drafts, cURL Export & Markdown API Docs

<p align="center">
  <img src="docs/images/feature-debugger.png" alt="Smart JSON Drafts, cURL Export and Markdown Docs" width="100%">
</p>

- **Recursive Nested JSON Drafts**: Automatically prefills Path, Query, Header, and Cookie parameters, and recursively expands nested DTOs and collections (`List<T>`) into formatted JSON request bodies—honoring `@JsonProperty` / `@SerializedName` / `@JSONField` names and `@Schema(example)` / `@ApiModelProperty(example)` / `@Parameter(example)` values.
- **Per-Endpoint Draft Persistence**: Remembers modified parameters per endpoint across switches, with a one-click **Reset** button to restore defaults from source code.
- **One-Click Markdown API Docs**: Extracts descriptions from Javadocs, `@param` tags, line comments, and Swagger/OpenAPI annotations; expands `@SpringQueryMap` / `@QueryMap` DTOs as well as generic response wrappers such as `DllResult<T>`, `DllPageResult<T>`, and `List<T>` (`data[].field`, `records[].field`).
- **Production-Ready Debugger**: Supports `none`, `form-data` (streaming file uploads), `x-www-form-urlencoded`, `json`, `xml`, `raw`, and `binary` modes, in-flight request cancellation, IDE global HTTP proxy integration, configurable timeouts, and 5 MB response preview protection.

## Installation

### Option 1: JetBrains Marketplace (Recommended)

- Marketplace Page: [ApiHelper - JetBrains Marketplace](https://plugins.jetbrains.com/plugin/32327-apihelper)
- In IntelliJ IDEA, go to `Settings` -> `Plugins` -> `Marketplace`, search for **ApiHelper**, and click **Install**.

### Option 2: Build from Source

```bash
./gradlew :buildPlugin
```

Then install `build/distributions/api-helper-<version>.zip` via `Settings` -> `Plugins` -> `⚙️` -> `Install Plugin from Disk...`.

## Shortcuts & Quick Actions

| Action | Shortcut / Entry | Description |
| --- | --- | --- |
| Search API Endpoint | `Ctrl + Alt + \` (`Cmd + Opt + \` on macOS) | Multi-keyword search across HTTP method, URL, method name, class, and module |
| Jump to Counterpart | Gutter left-click / Search popup `Shift + Enter` | Navigate between `@FeignClient` / `@HttpExchange` and `@RestController` |
| Debug Endpoint | Controller gutter right-click / Tree right-click / `Cmd + Enter` | Expands the bottom debug split pane and prefills parameters & nested JSON body |
| Copy as cURL | Controller gutter right-click / Tree right-click / Debugger `cURL` | Exports a standard `curl` command with active Base URL and global headers |
| Copy API Docs | Controller gutter right-click / Tree right-click | Generates standard Markdown API documentation with nested request/response fields |
| Select Opened Endpoint | Tool window toolbar `Select opened endpoint` | Locates and selects the endpoint at the current editor caret |

## Compatibility & Tech Stack

| Item | Details |
| --- | --- |
| Target IDE | IntelliJ IDEA 2024.3+ (build 243+, verified compatible with 2026.3 EAP) |
| Required Plugins | Java (bundled), Kotlin (bundled) |
| Source Languages | Java & Kotlin (powered by UAST) |
| Frameworks | Spring MVC, Spring Cloud OpenFeign, Spring 6 `@HttpExchange` |
| Build Stack | Kotlin 2.3.20 · JDK 21 · Gradle 9.5.0 · IntelliJ Platform Gradle Plugin 2.12.0 |

## Development

```bash
./gradlew :compileKotlin
./gradlew :check :verifyPluginProjectConfiguration :verifyPluginStructure
./gradlew :buildPlugin
./gradlew :runIde
```

## Version & Feedback

- Current version: `1.1.0` (see [CHANGELOG_en.md](./CHANGELOG_en.md))
- Issues and feature requests are welcome at [GitHub Issues](https://github.com/sxhjlzl/api-helper/issues). If ApiHelper helps your daily workflow, please consider giving the repository a **Star**!
