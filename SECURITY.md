# Security Policy / 安全策略

## English

### Supported Versions

Only the latest minor release receives security fixes.

| Version | Supported |
| --- | --- |
| 1.0.x   | ✅ |
| < 1.0   | ❌ |

### Reporting a Vulnerability

If you discover a security issue in ApiHelper, **please do NOT open a public GitHub issue**. Instead:

1. Email the maintainer privately at **maybe.zhuo@qq.com** (or open a [private security advisory](https://github.com/sxhjlzl/api-helper/security/advisories/new) on GitHub).
2. Provide:
   - A clear description of the issue and its impact
   - Steps to reproduce, including IDE version, plugin version, and a minimal sample project
   - Any proof-of-concept code or stack trace
3. You will receive an acknowledgement within 5 working days.
4. A fix and coordinated disclosure timeline will be agreed before any public announcement.

We follow a **90-day responsible disclosure window** by default.

### Scope

In-scope:

- Code execution vulnerabilities in the plugin
- PSI / index manipulation that leaks file contents outside the project scope
- Credential exfiltration via the plugin's configuration or HTTP behavior

Out-of-scope:

- Issues in IntelliJ Platform itself (please report to JetBrains)
- Issues in third-party libraries unless they directly compromise this plugin

---

## 中文

### 受支持版本

仅最新的 minor 版本接收安全补丁。

| 版本 | 是否支持 |
| --- | --- |
| 1.0.x   | ✅ |
| < 1.0   | ❌ |

### 报告漏洞

如果你发现 ApiHelper 存在安全问题，**请不要直接在 GitHub 公开 Issue 中描述**，而是：

1. 私信邮件到 **maybe.zhuo@qq.com**，或在 GitHub 上发起 [私密安全公告（Security Advisory）](https://github.com/sxhjlzl/api-helper/security/advisories/new)
2. 报告内容请包含：
   - 问题清晰描述与影响范围
   - 复现步骤，含 IDE 版本、插件版本、最小复现项目
   - POC 代码或异常堆栈（如有）
3. 你将在 **5 个工作日内** 收到回执
4. 修复时间表与公开披露节奏会在修复前与你达成一致

默认遵循 **90 天负责任披露窗口**。

### 范围

在范围内：

- 插件内的代码执行漏洞
- PSI / 索引被恶意操纵导致工程外文件内容泄漏
- 通过插件配置或 HTTP 行为造成的凭证外泄

不在范围内：

- IntelliJ Platform 本身的问题（请反馈给 JetBrains）
- 第三方库的问题，除非它直接危害到本插件
