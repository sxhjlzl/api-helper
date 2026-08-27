package com.lizhuolun.apihelper.core

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.util.Computable
import com.intellij.psi.PsiJavaFile
import com.intellij.testFramework.fixtures.BasePlatformTestCase

class HttpMappingInfoTest : BasePlatformTestCase() {

    fun testCreateUsesSmartPointerForMethodLifecycle() {
        val file = myFixture.configureByText(
            "DemoController.java",
            """
                package example;

                class DemoController {
                    void find() {}
                }
            """.trimIndent(),
        ) as PsiJavaFile
        val method = file.classes.single().methods.single()

        val mapping = readAction {
            HttpMappingInfo.create(
                url = "/find",
                httpMethod = HttpMethod.GET,
                method = method,
                kind = EndpointKind.CONTROLLER,
            )
        }

        assertSame(method, readAction { mapping.resolveMethod() })

        ApplicationManager.getApplication().runWriteAction {
            file.virtualFile.delete(this)
        }

        assertNull(readAction { mapping.resolveMethod() })
    }

    fun testQualifierDistinguishesOverloadedMethods() {
        val file = myFixture.configureByText(
            "OverloadedController.java",
            """
                package example;

                class OverloadedController {
                    void find(String value) {}
                    void find(long value) {}
                }
            """.trimIndent(),
        ) as PsiJavaFile
        val methods = file.classes.single().methods

        val qualifiers = readAction {
            methods.map(HttpMappingInfo::qualifierOf)
        }

        assertSize(2, qualifiers.distinct())
        assertContainsElements(
            qualifiers,
            "example.OverloadedController#find(String)",
            "example.OverloadedController#find(long)",
        )
    }

    fun testMatchesUsesNormalizedMatchUrlInsteadOfDisplayUrl() {
        val file = myFixture.configureByText(
            "MatchController.java",
            """
                package example;

                class MatchController {
                    void list() {}
                    void feignSide() {}
                }
            """.trimIndent(),
        ) as PsiJavaFile
        val methods = file.classes.single().methods

        // Controller 侧展示 URL 含 context-path 前缀，匹配路径为去掉前缀的相对路径；
        // 客户端侧展示 URL 本身就是相对路径，matchUrl 缺省从 url 归一化得到。
        val controllerSide = readAction {
            HttpMappingInfo.create(
                url = "/gateway/user/list",
                matchUrl = "/user/list",
                httpMethod = HttpMethod.GET,
                method = methods[0],
                kind = EndpointKind.CONTROLLER,
            )
        }
        val clientSide = readAction {
            HttpMappingInfo.create(
                url = "/user/list",
                httpMethod = HttpMethod.GET,
                method = methods[1],
                kind = EndpointKind.FEIGN,
            )
        }

        assertTrue(controllerSide.matches(clientSide))
        assertEquals("/user/list", clientSide.matchUrl)
    }

    fun testMatchesRejectsDifferentMatchUrlOrHttpMethod() {
        val file = myFixture.configureByText(
            "MismatchController.java",
            """
                package example;

                class MismatchController {
                    void a() {}
                    void b() {}
                    void c() {}
                }
            """.trimIndent(),
        ) as PsiJavaFile
        val methods = file.classes.single().methods

        val base = readAction {
            HttpMappingInfo.create(
                url = "/user/list",
                matchUrl = "/user/list",
                httpMethod = HttpMethod.GET,
                method = methods[0],
                kind = EndpointKind.CONTROLLER,
            )
        }
        val differentPath = readAction {
            HttpMappingInfo.create(
                url = "/user/detail",
                matchUrl = "/user/detail",
                httpMethod = HttpMethod.GET,
                method = methods[1],
                kind = EndpointKind.FEIGN,
            )
        }
        val differentMethod = readAction {
            HttpMappingInfo.create(
                url = "/user/list",
                matchUrl = "/user/list",
                httpMethod = HttpMethod.POST,
                method = methods[2],
                kind = EndpointKind.FEIGN,
            )
        }

        assertFalse(base.matches(differentPath))
        assertFalse(base.matches(differentMethod))
    }

    private fun <T> readAction(block: () -> T): T =
        ApplicationManager.getApplication().runReadAction(Computable { block() })
}
