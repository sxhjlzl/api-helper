<p align="center">
  <img src="docs/images/cover.png" alt="ApiHelper" width="100%">
</p>

<h3 align="center">All-in-One Spring API Navigation, Documentation &amp; Debugging Assistant for IntelliJ IDEA</h3>

<p align="center">
  <a href="https://plugins.jetbrains.com/plugin/32327-apihelper"><img src="https://img.shields.io/jetbrains/plugin/v/32327-apihelper?style=flat-square&label=Marketplace&color=0ea5e9" alt="Marketplace Version"></a>
  <a href="https://plugins.jetbrains.com/plugin/32327-apihelper"><img src="https://img.shields.io/jetbrains/plugin/d/32327-apihelper?style=flat-square&label=Downloads&color=2563eb" alt="Downloads"></a>
  <img src="https://img.shields.io/badge/IDEA-2024.3%2B-10b981?style=flat-square" alt="IDEA 2024.3+">
  <a href="https://github.com/sxhjlzl/api-helper/blob/main/LICENSE"><img src="https://img.shields.io/github/license/sxhjlzl/api-helper?style=flat-square&color=64748b" alt="License"></a>
  <a href="https://github.com/sxhjlzl/api-helper/stargazers"><img src="https://img.shields.io/github/stars/sxhjlzl/api-helper?style=flat-square&color=f59e0b" alt="GitHub Stars"></a>
</p>

<p align="center">
  <a href="https://plugins.jetbrains.com/plugin/32327-apihelper"><b>Marketplace</b></a>
  &nbsp;·&nbsp;
  <a href="./CHANGELOG_en.md"><b>Changelog</b></a>
  &nbsp;·&nbsp;
  <a href="https://github.com/sxhjlzl/api-helper/issues"><b>Issues</b></a>
  &nbsp;·&nbsp;
  <a href="./README.md"><b>简体中文</b></a>
</p>

---

**ApiHelper** is built for developers working with **Spring MVC**, **Spring Cloud OpenFeign**, and **Spring 6 `@HttpExchange`**. It brings endpoint discovery, bidirectional source navigation, Markdown API documentation, cURL export, and multi-environment HTTP debugging straight into IntelliJ IDEA—eliminating constant context switching.

## Core Capabilities

| Module | Highlights |
| :--- | :--- |
| **Bidirectional Gutter Navigation** | One-click jump between `@RestController` and `@FeignClient` / `@HttpExchange` methods; tolerates differing path variable names (`{id}` vs `{orderId}`) and recognizes custom composite meta-annotations (e.g. `@ApiV1RestController`). |
| **Global Endpoint Search** | Press `Ctrl + Alt + \` (`Cmd + Opt + \` on macOS) anywhere to search across HTTP methods, URLs, methods, classes, and modules: `Enter` jumps to source, `Shift + Enter` jumps to counterpart, `Ctrl/Cmd + Enter` opens the debugger. |
| **4-Tab Split Workspace** | Native top-level tabs for **Interfaces**, **Base URL**, **History**, and **Global Params**. The **Interfaces** tab features a collapsible vertical split (**Interfaces** tree on top + **Debug** pane on bottom), module grouping, and orphan endpoint filtering. |
| **Smart API Debugger** | Auto-prefills Path / Query / Header / Cookie parameters and **recursively expands nested DTOs & `List<T>` into JSON drafts** (honoring `@JsonProperty` and `@Schema(example)`); includes environment switching, draft persistence, 30-request history replay, and streaming file uploads. |
| **cURL & Markdown Docs** | One-click export of ready-to-run **cURL commands** (with active Base URL and global headers) and standard **Markdown API docs** (extracting Javadocs, `@param`, and Swagger annotations, and expanding generic wrappers like `DllResult<T>` and `DllPageResult<T>`). |
| **Spring Config Aware** | Automatically resolves `application.yml`, `bootstrap.yml`, and `application-{profile}.yml` for `server.port`, `context-path`, `servlet.path`, and `${var:default}` placeholders; supports both **Java & Kotlin** via UAST. |

---

## Quick Start

### 1. Installation

- **From JetBrains Marketplace (Recommended)**: Open `Settings` → `Plugins` → `Marketplace` in IDEA, search for **ApiHelper**, and click **Install**, or visit [JetBrains Marketplace](https://plugins.jetbrains.com/plugin/32327-apihelper).
- **From Disk**: Run `./gradlew :buildPlugin` and install `build/distributions/api-helper-<version>.zip` via `Install Plugin from Disk...`.

### 2. Common Workflows

| Workflow | Trigger | What It Does |
| :--- | :--- | :--- |
| **Search Any Endpoint** | `Ctrl + Alt + \` / `Cmd + Opt + \` | Multi-keyword search (e.g. `POST order`) to jump to source, counterpart, or debugger |
| **Client ↔ Server Jump** | Click editor gutter icon | Left-click to jump to counterpart; right-click Controller gutter to debug or copy |
| **Export cURL / API Docs** | Controller gutter / Tree context menu | Copies a standard `curl` command or Markdown documentation table to clipboard |
| **Find Orphan Endpoints** | Toolbar `Filter by Pairing Status` | Filters `Unpaired (Orphan) Only` to spot Feign methods without a matching Controller |
| **Switch Env & Global Token** | `Base URL` / `Global Params` tabs | Manage environment Base URLs and common headers (`Authorization`) in visual tables |
| **Replay Debug History** | `History` tab / Debugger `History` button | Double-click any recent request to restore its parameters and response in the debugger |

---

## Settings

Path: `Settings` → `Tools` → `ApiHelper`

- **Spring Active Profile**: Override active profiles (e.g. `dev,test`), or leave empty for automatic detection.
- **Connect & Request Timeouts**: Customize HTTP connect timeout (default `30s`) and request timeout (default `60s`), with automatic IDE HTTP proxy support.
- **Environments & Global Headers**: Synced bidirectionally with the **Base URL** and **Global Params** tool window tabs.

---

## Compatibility & Build

| Item | Requirement |
| :--- | :--- |
| **Target IDE** | IntelliJ IDEA **2024.3+** (build 243+, verified compatible with **2026.3 EAP**) |
| **Bundled Dependencies** | Java Plugin, Kotlin Plugin |
| **Supported Frameworks** | Spring MVC · Spring Boot · Spring Cloud OpenFeign · Spring 6 `@HttpExchange` |
| **Build Stack** | Kotlin 2.3.20 · JDK 21 · Gradle 9.5.0 · IntelliJ Platform Gradle Plugin 2.12.0 |

<details>
<summary><b>Development Commands &amp; Project Structure (Click to expand)</b></summary>

```bash
# Run compilation and unit tests
./gradlew :check

# Verify plugin configuration and structure
./gradlew :verifyPluginProjectConfiguration :verifyPluginStructure

# Build distributable plugin archive
./gradlew :buildPlugin

# Launch sandbox IDE
./gradlew :runIde
```

```text
src/main/kotlin/com/lizhuolun/apihelper/
├── ApiHelperBundle.kt        # Bilingual i18n resource bundle
├── cache/                    # Inverted-index mapping cache & SmartPointer cache
├── config/                   # Spring config reader, profile resolver & placeholder parser
├── core/                     # HTTP mapping model, path builder & annotation parser
├── listener/                 # Async startup warmup & debounced PSI listener
├── provider/                 # Icons & Java/Kotlin bidirectional LineMarker providers
├── scanner/                  # Index-backed project endpoint scanner
├── settings/                 # Persistent settings state & Configurable UI
└── ui/                       # Multi-tab tool window, endpoint tree, search popup & debugger
```

</details>

---

## Feedback & Support

- Current Version: **`1.1.0`** (see [CHANGELOG_en.md](./CHANGELOG_en.md) for full release notes)
- Found a bug or have a feature request? Feel free to open an issue on [GitHub Issues](https://github.com/sxhjlzl/api-helper/issues).
- If **ApiHelper** saves you time, a **Star** on GitHub is greatly appreciated!
