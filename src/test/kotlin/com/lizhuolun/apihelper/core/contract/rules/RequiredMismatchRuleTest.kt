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
import com.lizhuolun.apihelper.core.contract.ContractIssue
import com.lizhuolun.apihelper.core.contract.ContractParam
import com.lizhuolun.apihelper.core.contract.ContractParamKind
import com.lizhuolun.apihelper.core.contract.ContractSignature
import com.lizhuolun.apihelper.core.contract.ContractSignatureProvider
import com.lizhuolun.apihelper.core.contract.ContractSeverity
import com.lizhuolun.apihelper.core.contract.PathTemplate

/**
 * 必填性不一致规则测试。
 *
 * 重点验证「宁可漏报」的守卫：仅比较两侧都显式声明 required 的参数，
 * 按协议名匹配不上、客户端显式必填、服务端显式可选等情况一律不报。
 */
class RequiredMismatchRuleTest : BasePlatformTestCase() {

    fun testReportsWhenClientOptionalButControllerRequired() {
        val issues = check(
            clientParam = queryParam(required = false, requiredDeclared = true),
            controllerParam = queryParam(required = true, requiredDeclared = true),
        )

        val issue = issues.single { it.ruleId == "required-mismatch" }
        assertEquals(ContractSeverity.WARNING, issue.severity)
    }

    fun testNoIssueWhenBothOptional() {
        val issues = check(
            clientParam = queryParam(required = false, requiredDeclared = true),
            controllerParam = queryParam(required = false, requiredDeclared = true),
        )

        assertEmpty(issues.filter { it.ruleId == "required-mismatch" })
    }

    fun testNoIssueWhenClientRequired() {
        val issues = check(
            clientParam = queryParam(required = true, requiredDeclared = true),
            controllerParam = queryParam(required = true, requiredDeclared = true),
        )

        assertEmpty(issues.filter { it.ruleId == "required-mismatch" })
    }

    fun testNoIssueWhenRequiredNotDeclaredExplicitly() {
        val issues = check(
            clientParam = queryParam(required = false, requiredDeclared = true),
            controllerParam = queryParam(required = true, requiredDeclared = false),
        )

        assertEmpty(issues.filter { it.ruleId == "required-mismatch" })
    }

    fun testNoIssueWhenNamesDoNotMatch() {
        val issues = check(
            clientParam = queryParam(name = "userId", required = false, requiredDeclared = true),
            controllerParam = queryParam(name = "id", required = true, requiredDeclared = true),
        )

        assertEmpty(issues.filter { it.ruleId == "required-mismatch" })
    }

    private fun check(clientParam: ContractParam, controllerParam: ContractParam): List<ContractIssue> {
        val client = newMapping("client", EndpointKind.FEIGN)
        val controller = newMapping("controller", EndpointKind.CONTROLLER)
        val clientSignature = ContractSignature(client.qualifier, listOf(clientParam))
        val controllerSignature = ContractSignature(controller.qualifier, listOf(controllerParam))
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
        return ContractChecker(listOf(RequiredMismatchRule())).check(ContractChecker.buildContext(snapshot, signatures))
    }

    private fun queryParam(
        name: String = "verbose",
        required: Boolean,
        requiredDeclared: Boolean,
    ): ContractParam = ContractParam(
        kind = ContractParamKind.QUERY,
        explicitName = name,
        sourceName = name,
        required = required,
        typeText = "java.lang.Boolean",
        requiredDeclared = requiredDeclared,
    )

    private fun newMapping(className: String, kind: EndpointKind): HttpMappingInfo {
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
                    kind = kind,
                    pathTemplate = PathTemplate.parse("/api/user"),
                )
            },
        )
    }
}
