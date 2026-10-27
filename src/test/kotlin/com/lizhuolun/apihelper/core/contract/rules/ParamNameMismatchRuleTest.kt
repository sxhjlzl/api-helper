package com.lizhuolun.apihelper.core.contract.rules

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.util.Computable
import com.intellij.psi.PsiJavaFile
import com.intellij.psi.PsiMethod
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.lizhuolun.apihelper.core.EndpointKind
import com.lizhuolun.apihelper.core.HttpMappingInfo
import com.lizhuolun.apihelper.core.HttpMethod
import com.lizhuolun.apihelper.core.contract.BilateralSnapshot
import com.lizhuolun.apihelper.core.contract.ContractChecker
import com.lizhuolun.apihelper.core.contract.ContractFixKind
import com.lizhuolun.apihelper.core.contract.ContractIssue
import com.lizhuolun.apihelper.core.contract.ContractParam
import com.lizhuolun.apihelper.core.contract.ContractParamKind
import com.lizhuolun.apihelper.core.contract.ContractSignature
import com.lizhuolun.apihelper.core.contract.ContractSignatureProvider
import com.lizhuolun.apihelper.core.contract.PathTemplate

/**
 * 参数名不一致规则测试。
 *
 * 重点验证「宁可漏报」的四条守卫：
 * 未显式命名不比、数量不一致不比、名字相同不报、多实现时逐个对端比较。
 */
class ParamNameMismatchRuleTest : BasePlatformTestCase() {

    fun testReportsWhenExplicitNamesDiffer() {
        val client = newMapping("client")
        val controller = newMapping("controller")

        val issues = check(
            client = client,
            controller = controller,
            clientSignature = signature(client, queryParam("userId")),
            controllerSignature = signature(controller, queryParam("id")),
        )

        val issue = issues.single { it.ruleId == "param-name-mismatch" }
        assertEquals(ContractFixKind.SYNC_PARAM_NAME, issue.fixes.single().kind)
        assertEquals("id", issue.fixes.single().targetName)
    }

    fun testNoIssueWhenNamesMatch() {
        val client = newMapping("client")
        val controller = newMapping("controller")

        val issues = check(
            client = client,
            controller = controller,
            clientSignature = signature(client, queryParam("id")),
            controllerSignature = signature(controller, queryParam("id")),
        )

        assertEmpty(issues.filter { it.ruleId == "param-name-mismatch" })
    }

    fun testNoIssueWhenControllerNameIsImplicit() {
        val client = newMapping("client")
        val controller = newMapping("controller")

        val issues = check(
            client = client,
            controller = controller,
            clientSignature = signature(client, queryParam("userId")),
            controllerSignature = signature(controller, queryParam(explicitName = null, sourceName = "id")),
        )

        assertEmpty(issues.filter { it.ruleId == "param-name-mismatch" })
    }

    fun testNoIssueWhenParameterCountsDiffer() {
        val client = newMapping("client")
        val controller = newMapping("controller")

        val issues = check(
            client = client,
            controller = controller,
            clientSignature = signature(client, queryParam("userId"), queryParam("tenantId")),
            controllerSignature = signature(controller, queryParam("id")),
        )

        assertEmpty(issues.filter { it.ruleId == "param-name-mismatch" })
    }

    /**
     * 构造上下文并执行全部 P0 规则。
     */
    private fun check(
        client: HttpMappingInfo,
        controller: HttpMappingInfo,
        clientSignature: ContractSignature,
        controllerSignature: ContractSignature,
    ): List<ContractIssue> {
        val signatures = ContractSignatureProvider { mapping ->
            when (mapping.qualifier) {
                client.qualifier -> clientSignature
                controller.qualifier -> controllerSignature
                else -> null
            }
        }
        val snapshot = BilateralSnapshot(
            clients = listOf(client),
            controllers = listOf(controller),
            clientsByMatchUrl = mapOf(client.matchUrl to listOf(client)),
            controllersByMatchUrl = mapOf(controller.matchUrl to listOf(controller)),
            version = 1L,
        )
        return ContractChecker().check(ContractChecker.buildContext(snapshot, signatures))
    }

    private fun signature(mapping: HttpMappingInfo, vararg params: ContractParam): ContractSignature =
        ContractSignature(qualifier = mapping.qualifier, params = params.toList())

    private fun queryParam(explicitName: String?, sourceName: String = "param"): ContractParam =
        ContractParam(
            kind = ContractParamKind.QUERY,
            explicitName = explicitName,
            sourceName = sourceName,
            required = true,
            typeText = "java.lang.String",
        )

    private fun newMapping(className: String): HttpMappingInfo {
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
                    url = "/api/user",
                    httpMethod = HttpMethod.GET,
                    method = method,
                    kind = if (className == "client") EndpointKind.FEIGN else EndpointKind.CONTROLLER,
                    pathTemplate = PathTemplate.parse("/api/user"),
                )
            },
        )
    }
}