# Contributing Guide

> [中文](./CONTRIBUTING.md) | **English**

Thanks for your interest in ApiHelper! This document explains how to set up your dev environment and submit contributions.

## Development environment

| Tool | Version |
| --- | --- |
| JDK | 21 (Temurin 21.0.11 recommended) |
| Gradle | 9.5.0 (wrapper included, no manual install required) |
| IDEA | 2024.3 or newer |

## Local workflow

1. Fork this repository under your GitHub account and clone it locally
2. Open it in IDEA (`File` → `Open` → select `build.gradle.kts` at repo root → `Open as Project`)
3. Wait for the Gradle sync to finish (the first run downloads ~1-2 GB of IntelliJ Platform SDK)
4. Run `./gradlew :runIde` in a terminal, or use the Gradle panel under `intellij platform → runIde`, to launch a sandbox IDE

## Common Gradle tasks

```bash
./gradlew :compileKotlin   # compile Kotlin sources only
./gradlew :build           # full build (incl. instrumentCode / processResources)
./gradlew :buildPlugin     # produces build/distributions/api-helper-<version>.zip
./gradlew :runIde          # sandbox IDE for debugging
./gradlew :verifyPlugin    # validate against the target SDK
```

## Coding conventions

### Kotlin

- Strictly follow `kotlin.code.style=official` (already set in `gradle.properties`)
- Comments are written in Chinese; class and method KDocs must include `@param` / `@return`
- Critical business logic must emit `thisLogger().info(...)` / `warn(...)` logs for production triage
- Use ASCII commas and colons in log messages (no full-width punctuation)
- No trailing comments — every comment must live on its own line

### Annotations / model

- New Spring annotations must be added to [`SpringAnnotations`](src/main/kotlin/com/lizhuolun/apihelper/core/annotation/SpringAnnotations.kt) with the corresponding `METHOD_ANNOTATION_TO_HTTP` entry
- New endpoint categories must extend the [`EndpointKind`](src/main/kotlin/com/lizhuolun/apihelper/core/HttpMappingInfo.kt) enum

### IDEA extension points

- When adding a new extension to [`plugin.xml`](src/main/resources/META-INF/plugin.xml), follow the existing ordering
- Any `LineMarker` that supports both Java and Kotlin must be registered twice (`language="JAVA"` and `language="kotlin"`)
- Use `@Service(Service.Level.PROJECT)` rather than hand-rolled singletons
- All PSI access must be wrapped in `ApplicationManager.getApplication().runReadAction(Computable { ... })`, and PSI-derived data must not escape the read action

### Internationalization

- Every user-visible string must go through [`ApiHelperBundle`](src/main/kotlin/com/lizhuolun/apihelper/ApiHelperBundle.kt)
- Add the corresponding key to both `messages/ApiHelperBundle.properties` (English) and `ApiHelperBundle_zh_CN.properties` (Chinese)

## Submission workflow

1. Create a feature branch off `main`: `git checkout -b feat/<short-name>`
2. Implement and self-test
3. Use [Conventional Commits](https://www.conventionalcommits.org/en/v1.0.0/) for commit messages: `feat: ...` / `fix: ...` / `docs: ...`
4. When opening a PR, include:
   - A short summary of changes
   - Test coverage (manual / unit)
   - Screenshots or animated GIFs for any UI change
5. Wait for maintainer review

## Reporting issues

Please open an issue via [GitHub Issues](https://github.com/sxhjlzl/api-helper/issues), and include:

- IDEA version
- ApiHelper version
- A minimal reproducer (project zip or code snippet)
- Expected vs. actual behavior
- Stack trace, if any

## Code of Conduct

By participating you agree to abide by the [Contributor Covenant Code of Conduct](./CODE_OF_CONDUCT.md).
