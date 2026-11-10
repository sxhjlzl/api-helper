package com.lizhuolun.apihelper.ui

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.util.Computable
import com.intellij.psi.PsiJavaFile
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.lizhuolun.apihelper.core.EndpointKind
import com.lizhuolun.apihelper.core.HttpMethod

class EndpointTreeHttpRequestTest : BasePlatformTestCase() {

    fun testEndpointParametersAreIncludedInHttpClientRequest() {
        val file = myFixture.configureByText(
            "CouponApi.java",
            """
                package example;

                interface CouponApi {
                    Object getCouponListByUserId(Long userId, Integer status);
                }
            """.trimIndent(),
        ) as PsiJavaFile
        val method = file.classes.single().methods.single()
        val item = EndpointTreeItem(
            url = "/rpc-api/promotion/coupon/list-by-user-id",
            httpMethod = HttpMethod.GET,
            methodName = method.name,
            className = "example.CouponApi",
            moduleName = "",
            kind = EndpointKind.FEIGN,
            pointer = null,
        )

        val requestText = ApplicationManager.getApplication().runReadAction(Computable {
            EndpointTree(project, EndpointKind.FEIGN).buildHttpRequestForEndpoint(item, method)
        })

        assertEquals(
            "GET http://localhost:8080/rpc-api/promotion/coupon/list-by-user-id?userId=0&status=0\n",
            requestText,
        )
    }
}
