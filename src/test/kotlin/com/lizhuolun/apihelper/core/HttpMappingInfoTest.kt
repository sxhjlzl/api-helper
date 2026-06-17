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

    private fun <T> readAction(block: () -> T): T =
        ApplicationManager.getApplication().runReadAction(Computable { block() })
}
