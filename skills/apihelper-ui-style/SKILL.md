---
name: apihelper-ui-style
description: 统一 ApiHelper IntelliJ 插件的 UI 风格与交互密度。只要任务涉及工具窗口、调试面板、设置页、Swing 表单、列表、输入框、下拉框、按钮、表格编辑态，或者用户提出“美化 UI”“统一风格”“调整控件高度”“贴近 IDE 风格”等诉求，就应使用这个 skill，而不是继续在各处手写尺寸和边框。
---

# ApiHelper UI Style

在 ApiHelper 项目里做 UI 改动时，先遵守这套规则，再写代码。目标不是做一套新的视觉语言，而是让插件看起来像 IntelliJ 自己带的功能，同时保持信息密度、可读性和一致性。

## 先做什么

1. 先看现有公共样式入口：`src/main/kotlin/com/lizhuolun/apihelper/ui/component/ApiHelperUiStyle.kt`
2. 如果是输入框、下拉框、按钮、工具栏条带，优先复用公共样式方法，不要直接写新的 `preferredSize`、圆角、边框色。
3. 如果需要新增一种可复用控件风格，先扩展 `ApiHelperUiStyle`，再回到具体面板接入。

## 样式原则

- 保持 IDE 原生气质：颜色优先取自 `UIUtil`、`UIManager`、`JBColor`，不要额外引入品牌色背景。
- 保持信息密度：能用 12px 字体解决的，不要放大到 13px、14px。
- 保持统一节奏：同一层级的控件高度、圆角、描边、左右内边距必须一致。
- 保持弱装饰：列表行默认不加装饰底色，只在选中态使用 IDE 选中背景。
- 保持语义重点：只有真正需要区分语义的元素才允许带颜色，例如 HTTP 方法标签。

## 控件规范

### 输入框和下拉框

- 文本输入框、地址栏、搜索框、设置页输入框、顶部请求方法下拉、响应类型下拉统一为 `30px` 高。
- 统一走 `ApiHelperUiStyle.applyTextField(...)` 和 `ApiHelperUiStyle.applyComboBox(...)`。
- 不要在业务面板里重复写 `RoundedCornerBorder` 或额外套一层 `EmptyBorder` 去补齐高度。

### 按钮

- 与输入框并排出现的按钮，优先与输入框同高。
- 二级按钮使用描边样式，不要自己在各处重新写 `LineBorder + EmptyBorder`。
- 统一走 `ApiHelperUiStyle.applyOutlinedButton(...)`，再按需要补图标。

### 工具栏和条带

- 顶部主导航、Title Actions 对应的内容条、接口分类条统一为 `30px` 高。
- 统一走 `ApiHelperUiStyle.applyHeaderHeight(...)`。
- 条带只承担分组和切换职责，不要堆额外背景块。

### 表格编辑区

- 可编辑参数表是输入区的一部分，行高与主控件保持同一密度。
- 如果表格里出现下拉编辑器或上传占位态，优先通过行高保证一致，不要单独把单元格控件做得更矮。

## 列表和树

- 默认行背景透明，只在选中态显示选中背景。
- 包名和方法名可以弱化，但不能弱化到影响扫描效率。
- HTTP 方法标签允许使用浅底描边，目的是加快扫描，不是做装饰。

## 文本和字体

- 普通 UI 文本使用 12px。
- 次级说明文本使用同字号但弱化颜色，不要靠缩小字号制造层级。
- 只有代码、JSON、响应体、请求体编辑区使用等宽字体。

## 不要这样做

- 不要在不同文件里散落 `preferredSize = Dimension(..., 24)`、`30`、`32` 这类魔法数字。
- 不要给普通列表行上灰底、卡片底或大面积色块。
- 不要把调试面板和工具窗口做成两套完全不同的控件语言。
- 不要为了“美化”引入和 IDE 主体无关的蓝色、紫色渐变、阴影或大圆角。

## 推荐工作流

1. 找到当前面板是否已经用了 `ApiHelperUiStyle`。
2. 没有的话，先接入公共样式，再讨论局部视觉调整。
3. 同时检查相邻面板有没有同类控件，如果有，一起统一。
4. 改完后至少执行 `./gradlew :compileKotlin`；涉及交互或共享逻辑时再跑 `./gradlew :test`。

## 适用范围

- `EndpointToolWindowPanel`
- `EndpointDebugPanel`
- `ApiHelperConfigurable`
- 后续新增的工具窗口面板、设置页、列表过滤区、调试区子面板

## 产出要求

当使用这个 skill 完成一次 UI 改动时，结果应该满足：

- 同层级输入控件高度一致
- 同层级下拉控件高度一致
- 交互区边框风格一致
- 颜色来源与 IDE 主题一致
- 不再出现新的散落魔法尺寸
