package com.lizhuolun.apihelper.core.contract

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.util.Computable
import com.intellij.psi.JavaPsiFacade
import com.intellij.psi.PsiMethod
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.testFramework.fixtures.BasePlatformTestCase

/**
 * 契约签名提取器测试。
 *
 * 覆盖两类容易出错的地方：
 * 1. 注解显式名与源码参数名的区分（决定误报率）；
 * 2. 无注解参数在客户端侧与服务端侧的不同语义。
 */
class ContractSignatureExtractorTest : BasePlatformTestCase() {

    fun testExtractsAnnotatedParameters() {
        addSpringAnnotations()
        myFixture.configureByText(
            "DemoController.java",
            """
                package example;

                import org.springframework.web.bind.annotation.RestController;
                import org.springframework.web.bind.annotation.GetMapping;
                import org.springframework.web.bind.annotation.PathVariable;
                import org.springframework.web.bind.annotation.RequestParam;
                import org.springframework.web.bind.annotation.RequestHeader;
                import org.springframework.web.bind.annotation.CookieValue;

                @RestController
                class DemoController {
                    @GetMapping("/users/{id}")
                    String detail(@PathVariable("id") String id,
                                  @RequestParam(value = "page", required = false) Integer page,
                                  @RequestHeader("X-Tenant") String tenant,
                                  @CookieValue("sid") String sid) {
                        return "";
                    }
                }
            """.trimIndent(),
        )

        val signature = extractSignature("DemoController", "detail")

        assertEquals(4, signature.params.size)

        val path = signature.params[0]
        assertEquals(ContractParamKind.PATH, path.kind)
        assertEquals("id", path.explicitName)
        assertTrue(path.required)

        val query = signature.params[1]
        assertEquals(ContractParamKind.QUERY, query.kind)
        assertEquals("page", query.explicitName)
        assertEquals(false, query.required)

        val header = signature.params[2]
        assertEquals(ContractParamKind.HEADER, header.kind)
        assertEquals("X-Tenant", header.explicitName)

        val cookie = signature.params[3]
        assertEquals(ContractParamKind.COOKIE, cookie.kind)
        assertEquals("sid", cookie.explicitName)
    }

    fun testFallsBackToSourceNameWhenAnnotationHasNoExplicitName() {
        addSpringAnnotations()
        myFixture.configureByText(
            "NameController.java",
            """
                package example;

                import org.springframework.web.bind.annotation.RestController;
                import org.springframework.web.bind.annotation.GetMapping;
                import org.springframework.web.bind.annotation.RequestParam;

                @RestController
                class NameController {
                    @GetMapping("/list")
                    String list(@RequestParam String keyword) {
                        return "";
                    }
                }
            """.trimIndent(),
        )

        val signature = extractSignature("NameController", "list")
        val param = signature.params.single()

        assertEquals(ContractParamKind.QUERY, param.kind)
        assertNull(param.explicitName)
        assertEquals("keyword", param.effectiveName)
    }

    fun testUnannotatedDtoOnClientSideBecomesBody() {
        addSpringAnnotations()
        myFixture.addFileToProject(
            "example/UserDto.java",
            """
                package example;

                public class UserDto {
                    private String name;
                }
            """.trimIndent(),
        )
        myFixture.configureByText(
            "UserClient.java",
            """
                package example;

                import org.springframework.cloud.openfeign.FeignClient;
                import org.springframework.web.bind.annotation.PostMapping;

                @FeignClient(name = "user")
                interface UserClient {
                    @PostMapping("/users")
                    String create(UserDto user);
                }
            """.trimIndent(),
        )

        val signature = extractSignature("UserClient", "create")
        val body = signature.params.single()

        assertEquals(ContractParamKind.BODY, body.kind)
        assertEquals("example.UserDto", signature.bodyTypeText)
    }

    fun testUnannotatedSimpleTypeOnControllerSideBecomesQuery() {
        addSpringAnnotations()
        myFixture.configureByText(
            "SimpleController.java",
            """
                package example;

                import org.springframework.web.bind.annotation.RestController;
                import org.springframework.web.bind.annotation.GetMapping;

                @RestController
                class SimpleController {
                    @GetMapping("/list")
                    String list(String keyword) {
                        return "";
                    }
                }
            """.trimIndent(),
        )

        val signature = extractSignature("SimpleController", "list")

        assertEquals(ContractParamKind.QUERY, signature.params.single().kind)
    }

    fun testSpringQueryMapIsExpandedIntoQueryParameters() {
        addSpringAnnotations()
        myFixture.addFileToProject(
            "example/QueryDto.java",
            """
                package example;

                public class QueryDto {
                    private String keyword;
                    private Integer page;
                }
            """.trimIndent(),
        )
        myFixture.configureByText(
            "SearchClient.java",
            """
                package example;

                import org.springframework.cloud.openfeign.FeignClient;
                import org.springframework.cloud.openfeign.SpringQueryMap;
                import org.springframework.web.bind.annotation.GetMapping;

                @FeignClient(name = "search")
                interface SearchClient {
                    @GetMapping("/search")
                    String search(@SpringQueryMap QueryDto query);
                }
            """.trimIndent(),
        )

        val signature = extractSignature("SearchClient", "search")

        assertEquals(2, signature.params.size)
        assertTrue(signature.params.all { it.kind == ContractParamKind.QUERY })
        assertEquals(
            setOf("keyword", "page"),
            signature.params.map { it.explicitName }.toSet(),
        )
    }

    /**
     * 取指定类与方法的签名。
     */
    private fun extractSignature(className: String, methodName: String): ContractSignature =
        readAction {
            val psiClass = JavaPsiFacade.getInstance(project)
                .findClass("example.$className", GlobalSearchScope.allScope(project))
                ?: error("Class not found: example.$className")
            val method: PsiMethod = psiClass.methods.first { it.name == methodName }
            ContractSignatureExtractor.extract(method)
        }

    private fun <T> readAction(block: () -> T): T =
        ApplicationManager.getApplication().runReadAction(Computable { block() })

    /**
     * 注册测试所需的 Spring 注解桩。
     */
    private fun addSpringAnnotations() {
        myFixture.addFileToProject(
            "org/springframework/web/bind/annotation/RestController.java",
            "package org.springframework.web.bind.annotation;\npublic @interface RestController {}",
        )
        myFixture.addFileToProject(
            "org/springframework/web/bind/annotation/GetMapping.java",
            """
                package org.springframework.web.bind.annotation;
                public @interface GetMapping {
                    String[] value() default {};
                    String[] path() default {};
                }
            """.trimIndent(),
        )
        myFixture.addFileToProject(
            "org/springframework/web/bind/annotation/PostMapping.java",
            """
                package org.springframework.web.bind.annotation;
                public @interface PostMapping {
                    String[] value() default {};
                    String[] path() default {};
                }
            """.trimIndent(),
        )
        myFixture.addFileToProject(
            "org/springframework/web/bind/annotation/PathVariable.java",
            """
                package org.springframework.web.bind.annotation;
                public @interface PathVariable {
                    String value() default "";
                    String name() default "";
                }
            """.trimIndent(),
        )
        myFixture.addFileToProject(
            "org/springframework/web/bind/annotation/RequestParam.java",
            """
                package org.springframework.web.bind.annotation;
                public @interface RequestParam {
                    String value() default "";
                    String name() default "";
                    boolean required() default true;
                }
            """.trimIndent(),
        )
        myFixture.addFileToProject(
            "org/springframework/web/bind/annotation/RequestHeader.java",
            """
                package org.springframework.web.bind.annotation;
                public @interface RequestHeader {
                    String value() default "";
                    String name() default "";
                    boolean required() default true;
                }
            """.trimIndent(),
        )
        myFixture.addFileToProject(
            "org/springframework/web/bind/annotation/CookieValue.java",
            """
                package org.springframework.web.bind.annotation;
                public @interface CookieValue {
                    String value() default "";
                    String name() default "";
                }
            """.trimIndent(),
        )
        myFixture.addFileToProject(
            "org/springframework/web/bind/annotation/RequestBody.java",
            "package org.springframework.web.bind.annotation;\npublic @interface RequestBody {}",
        )
        myFixture.addFileToProject(
            "org/springframework/cloud/openfeign/FeignClient.java",
            """
                package org.springframework.cloud.openfeign;
                public @interface FeignClient {
                    String name() default "";
                    String path() default "";
                }
            """.trimIndent(),
        )
        myFixture.addFileToProject(
            "org/springframework/cloud/openfeign/SpringQueryMap.java",
            "package org.springframework.cloud.openfeign;\npublic @interface SpringQueryMap {}",
        )
    }
}