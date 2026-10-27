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
import com.lizhuolun.apihelper.core.contract.PathTemplate

/**
 * 路径变量缺失规则测试。
 *
 * 规则只看「本侧模板 vs 本侧参数」，因此测试也只在单侧构造数据。
 */
class PathVariableRuleTest : BasePlatformTestCase() {

    fun testReportsWhenPathVariableHasNoMatchingParameter() {
        val mapping = newMapping("/api/order/{orderId}")
        val signature = signature(mapping, pathParam("id"))

        val issues = check(mapping, signature)

        val issue = issues.single { it.ruleId == "path-variable-missing" }
        assertTrue(issue.message.contains("orderId"))
    }

    fun testNoIssueWhenPathVariableNameMatches() {
        val mapping = newMapping("/api/order/{orderId}")

        val issues = check(mapping, signature(mapping, pathParam("orderId")))

        assertEmpty(issues.filter { it.ruleId == "path-variable-missing" })
    }

    fun testNoIssueWhenParameterNameIsImplicit() {
        val mapping = newMapping("/api/order/{orderId}")

        val issues = check(mapping, signature(mapping, pathParam(null, "orderId")))

        assertEmpty(issues.filter { it.ruleId == "path-variable-missing" })
    }

    fun testMapParameterSuppressesReport() {
        val mapping = newMapping("/api/order/{orderId}")
        val mapParam = ContractParam(
            kind = ContractParamKind.PATH,
            explicitName = null,
            sourceName = "variables",
            required = true,
            typeText = "java.util.Map<java.lang.String,java.lang.String>",
        )

        val issues = check(mapping, signature(mapping, mapParam))

        assertEmpty(issues.filter { it.ruleId == "path-variable-missing" })
    }

    fun testNoVariablesProducesNoIssue() {
        val mapping = newMapping("/api/order/list")

        val issues = check(mapping, signature(mapping))

        assertEmpty(issues.filter { it.ruleId == "path-variable-missing" })
    }

    private fun check(mapping: HttpMappingInfo, signature: ContractSignature): List<ContractIssue> {
        val signatures = ContractSignatureProvider { if (it.qualifier == mapping.qualifier) signature else null }
        val snapshot = BilateralSnapshot(
            clients = listOf(mapping),
            controllers = emptyList(),
            clientsByMatchUrl = mapOf(mapping.matchUrl to listOf(mapping)),
            controllersByMatchUrl = emptyMap(),
            version = 1L,
        )
        return ContractChecker().check(ContractChecker.buildContext(snapshot, signatures))
    }

    private fun signature(mapping: HttpMappingInfo, vararg params: ContractParam): ContractSignature =
        ContractSignature(qualifier = mapping.qualifier, params = params.toList())

    private fun pathParam(explicitName: String?, sourceName: String = "id"): ContractParam =
        ContractParam(
            kind = ContractParamKind.PATH,
            explicitName = explicitName,
            sourceName = sourceName,
            required = true,
            typeText = "java.lang.String",
        )

    private fun newMapping(url: String): HttpMappingInfo {
        val file = myFixture.configureByText(
            "PathClient.java",
            """
                package example;

                interface PathClient {
                    void handle();
                }
            """.trimIndent(),
        ) as PsiJavaFile
        val method: PsiMethod = file.classes.single().methods.single()
        return ApplicationManager.getApplication().runReadAction(
            Computable {
                HttpMappingInfo.create(
                    url = url,
                    httpMethod = HttpMethod.GET,
                    method = method,
                    kind = EndpointKind.FEIGN,
                    pathTemplate = PathTemplate.parse(url),
                )
            },
        )
    }
}