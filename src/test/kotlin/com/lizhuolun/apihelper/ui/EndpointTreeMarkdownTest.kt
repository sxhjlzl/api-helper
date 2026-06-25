package com.lizhuolun.apihelper.ui

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.util.Computable
import com.intellij.psi.PsiJavaFile
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.lizhuolun.apihelper.core.EndpointKind
import com.lizhuolun.apihelper.core.HttpMethod

class EndpointTreeMarkdownTest : BasePlatformTestCase() {

    fun testCopiedApiMarkdownIncludesFieldJavadocDescription() {
        val file = myFixture.configureByText(
            "DemoController.java",
            """
                package example;

                class ScreenQuery {
                    /**
                     * 屏幕编码
                     */
                    private String screenCode;
                }

                class DemoController {
                    Object list(ScreenQuery query) {
                        return null;
                    }
                }
            """.trimIndent(),
        ) as PsiJavaFile

        val method = file.classes
            .single { it.name == "DemoController" }
            .methods
            .single()
        val item = EndpointTreeItem(
            url = "/screen/list",
            httpMethod = HttpMethod.GET,
            methodName = "list",
            className = "example.DemoController",
            moduleName = "",
            kind = EndpointKind.CONTROLLER,
            pointer = null,
        )
        val markdown = readAction {
            EndpointTree(project, EndpointKind.CONTROLLER).buildAiMarkdown(item, method)
        }

        assertTrue(markdown.contains("| `screenCode` | `String` |"))
        assertTrue(markdown.contains("屏幕编码"))
    }

    fun testCopiedApiMarkdownIncludesResponseFieldsAndDescriptions() {
        val file = myFixture.configureByText(
            "DemoController.java",
            """
                package example;

                class DllPageResult<T> {
                    /**
                     * 总数
                     */
                    private Long total;

                    /**
                     * 数据列表
                     */
                    private java.util.List<T> records;
                }

                class ScreenRes {
                    /**
                     * 屏幕编码
                     */
                    private String screenCode;
                }

                class DemoController {
                    DllPageResult<ScreenRes> page() {
                        return null;
                    }
                }
            """.trimIndent(),
        ) as PsiJavaFile

        val method = file.classes
            .single { it.name == "DemoController" }
            .methods
            .single()
        val item = EndpointTreeItem(
            url = "/screen/page",
            httpMethod = HttpMethod.GET,
            methodName = "page",
            className = "example.DemoController",
            moduleName = "",
            kind = EndpointKind.CONTROLLER,
            pointer = null,
        )
        val markdown = readAction {
            EndpointTree(project, EndpointKind.CONTROLLER).buildAiMarkdown(item, method)
        }

        assertTrue(markdown.contains("total"))
        assertTrue(markdown.contains("总数"))
        assertTrue(markdown.contains("records"))
        assertTrue(markdown.contains("数据列表"))
        assertTrue(markdown.contains("records[].screenCode"))
        assertTrue(markdown.contains("屏幕编码"))
    }

    fun testCopiedApiMarkdownIncludesResultDataListFields() {
        val file = myFixture.configureByText(
            "DemoController.java",
            """
                package example;

                class DllResult<T> {
                    /**
                     * 状态码
                     */
                    private int code;

                    /**
                     * 数据对象
                     */
                    private T data;
                }

                class MainboardExcel {
                    /**
                     * 设备编码
                     */
                    private String deviceCode;
                }

                class DemoController {
                    DllResult<java.util.List<MainboardExcel>> export() {
                        return null;
                    }
                }
            """.trimIndent(),
        ) as PsiJavaFile

        val method = file.classes
            .single { it.name == "DemoController" }
            .methods
            .single()
        val item = EndpointTreeItem(
            url = "/mainboard/export",
            httpMethod = HttpMethod.GET,
            methodName = "export",
            className = "example.DemoController",
            moduleName = "",
            kind = EndpointKind.CONTROLLER,
            pointer = null,
        )
        val markdown = readAction {
            EndpointTree(project, EndpointKind.CONTROLLER).buildAiMarkdown(item, method)
        }

        assertTrue(markdown.contains("code"))
        assertTrue(markdown.contains("状态码"))
        assertTrue(markdown.contains("data"))
        assertTrue(markdown.contains("数据对象"))
        assertTrue(markdown.contains("data[].deviceCode"))
        assertTrue(markdown.contains("设备编码"))
    }

    private fun <T> readAction(action: () -> T): T =
        ApplicationManager.getApplication().runReadAction(Computable { action() })
}
