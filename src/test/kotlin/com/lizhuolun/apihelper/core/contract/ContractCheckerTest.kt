package com.lizhuolun.apihelper.core.contract

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.util.Computable
import com.intellij.psi.PsiJavaFile
import com.intellij.psi.PsiMethod
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.lizhuolun.apihelper.core.EndpointKind
import com.lizhuolun.apihelper.core.HttpMappingInfo
import com.lizhuolun.apihelper.core.HttpMethod

/**
 * 契约体检引擎测试：配对、孤儿、重复映射。
 *
 * 规则层通过注入假的签名提供者隔离 PSI，只验证配对与规则判定逻辑。
 */
class ContractCheckerTest : BasePlatformTestCase() {

    fun testMatchingEndpointProducesNoIssue() {
        val client = newMapping("client", "/api/user/list", EndpointKind.FEIGN)
        val controller = newMapping("controller", "/api/user/list", EndpointKind.CONTROLLER)

        val issues = check(snapshotOf(clients = listOf(client), controllers = listOf(controller)))

        assertEmpty(issues)
    }

    fun testUnmatchedClientIsReportedAsOrphan() {
        val client = newMapping("client", "/api/user/detail", EndpointKind.FEIGN)
        val controller = newMapping("controller", "/api/user/list", EndpointKind.CONTROLLER)

        val issues = check(snapshotOf(clients = listOf(client), controllers = listOf(controller)))

        val orphan = issues.filter { it.ruleId == "orphan-client" }
        assertEquals(1, orphan.size)
        assertEquals(ContractSeverity.WARNING, orphan.single().severity)
        assertSame(client, orphan.single().client)
    }

    fun testOrphanRuleIsSkippedWhenProjectHasNoControllers() {
        val client = newMapping("client", "/api/user/detail", EndpointKind.FEIGN)

        val issues = check(snapshotOf(clients = listOf(client), controllers = emptyList()))

        assertEmpty(issues.filter { it.ruleId == "orphan-client" })
    }

    fun testDuplicateMappingIsReportedOncePerPair() {
        val first = newMapping("first", "/api/user/list", EndpointKind.CONTROLLER)
        val second = newMapping("second", "/api/user/list", EndpointKind.CONTROLLER)
        val third = newMapping("third", "/api/user/list", EndpointKind.CONTROLLER)

        val issues = check(snapshotOf(clients = emptyList(), controllers = listOf(first, second, third)))

        val duplicates = issues.filter { it.ruleId == "duplicate-mapping" }
        assertEquals(3, duplicates.size)
        assertTrue(duplicates.all { it.severity == ContractSeverity.ERROR })
        assertTrue(duplicates.all { it.controllers.size == 2 })
    }

    fun testDifferentHttpMethodIsNotDuplicate() {
        val first = newMapping("first", "/api/user/list", EndpointKind.CONTROLLER, HttpMethod.GET)
        val second = newMapping("second", "/api/user/list", EndpointKind.CONTROLLER, HttpMethod.POST)

        val issues = check(snapshotOf(clients = emptyList(), controllers = listOf(first, second)))

        assertEmpty(issues.filter { it.ruleId == "duplicate-mapping" })
    }

    /**
     * 构造以 URL 为键的倒排索引快照。
     */
    private fun snapshotOf(
        clients: List<HttpMappingInfo>,
        controllers: List<HttpMappingInfo>,
    ): BilateralSnapshot = BilateralSnapshot(
        clients = clients,
        controllers = controllers,
        clientsByMatchUrl = clients.groupBy { it.matchUrl },
        controllersByMatchUrl = controllers.groupBy { it.matchUrl },
        version = 1L,
    )

    /**
     * 用不提供签名的上下文执行体检，聚焦配对类规则。
     */
    private fun check(snapshot: BilateralSnapshot): List<ContractIssue> {
        val context = ContractChecker.buildContext(snapshot) { null }
        return ContractChecker().check(context)
    }

    /**
     * 基于夹具中的一个无参方法创建映射。
     */
    private fun newMapping(
        className: String,
        url: String,
        kind: EndpointKind,
        httpMethod: HttpMethod = HttpMethod.GET,
    ): HttpMappingInfo {
        val file = myFixture.configureByText(
            "$className.java",
            """
                package example;

                class $className {
                    void handle() {}
                }
            """.trimIndent(),
        ) as PsiJavaFile
        val method: PsiMethod = file.classes.single().methods.single()
        return ApplicationManager.getApplication().runReadAction(
            Computable {
                HttpMappingInfo.create(
                    url = url,
                    httpMethod = httpMethod,
                    method = method,
                    kind = kind,
                    pathTemplate = PathTemplate.parse(url),
                )
            },
        )
    }
}