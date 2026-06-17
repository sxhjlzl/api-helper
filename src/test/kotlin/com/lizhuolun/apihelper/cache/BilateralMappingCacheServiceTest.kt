package com.lizhuolun.apihelper.cache

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.util.Computable
import com.intellij.psi.PsiJavaFile
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.lizhuolun.apihelper.scanner.EndpointScanner

class BilateralMappingCacheServiceTest : BasePlatformTestCase() {

    fun testHasControllerCounterpartReturnsTrueWhenControllerExists() {
        addSpringAnnotations()

        myFixture.configureByText(
            "DemoController.java",
            """
                package example;

                import org.springframework.web.bind.annotation.GetMapping;
                import org.springframework.web.bind.annotation.RestController;

                @RestController
                class DemoController {
                    @GetMapping("/api/hello")
                    String hello() {
                        return "ok";
                    }
                }
            """.trimIndent(),
        )
        val feignFile = myFixture.configureByText(
            "DemoClient.java",
            """
                package example;

                import org.springframework.cloud.openfeign.FeignClient;
                import org.springframework.web.bind.annotation.GetMapping;

                @FeignClient(name = "demo")
                interface DemoClient {
                    @GetMapping("/api/hello")
                    String hello();
                }
            """.trimIndent(),
        ) as PsiJavaFile

        val cache = BilateralMappingCacheService.of(project)
        cache.clear()
        cache.replaceController(EndpointScanner.scanControllerEndpoints(project, null))

        val feignMethod = feignFile.classes.single().methods.single()
        assertTrue(cache.hasControllerCounterpart(feignMethod))
    }

    fun testHasControllerCounterpartReturnsFalseWhenNoMatchingController() {
        addSpringAnnotations()

        val feignFile = myFixture.configureByText(
            "DemoClient.java",
            """
                package example;

                import org.springframework.cloud.openfeign.FeignClient;
                import org.springframework.web.bind.annotation.GetMapping;

                @FeignClient(name = "demo")
                interface DemoClient {
                    @GetMapping("/api/no-match")
                    String hello();
                }
            """.trimIndent(),
        ) as PsiJavaFile

        val cache = BilateralMappingCacheService.of(project)
        cache.clear()
        // Controller 缓存为空，等同于工程里没有匹配 Controller
        cache.replaceController(emptyList())

        val feignMethod = feignFile.classes.single().methods.single()
        assertFalse(cache.hasControllerCounterpart(feignMethod))
    }

    fun testHasClientCounterpartReturnsTrueWhenClientExists() {
        addSpringAnnotations()

        val controllerFile = myFixture.configureByText(
            "DemoController.java",
            """
                package example;

                import org.springframework.web.bind.annotation.GetMapping;
                import org.springframework.web.bind.annotation.RestController;

                @RestController
                class DemoController {
                    @GetMapping("/api/hello")
                    String hello() {
                        return "ok";
                    }
                }
            """.trimIndent(),
        ) as PsiJavaFile

        val feignFile = myFixture.configureByText(
            "DemoClient.java",
            """
                package example;

                import org.springframework.cloud.openfeign.FeignClient;
                import org.springframework.web.bind.annotation.GetMapping;

                @FeignClient(name = "demo")
                interface DemoClient {
                    @GetMapping("/api/hello")
                    String hello();
                }
            """.trimIndent(),
        ) as PsiJavaFile

        val cache = BilateralMappingCacheService.of(project)
        cache.clear()
        cache.replaceClient(EndpointScanner.scanClientEndpoints(project))

        val controllerMethod = controllerFile.classes.single().methods.single()
        assertTrue(cache.hasClientCounterpart(controllerMethod))
    }

    fun testHasClientCounterpartReturnsFalseWhenNoMatchingClient() {
        addSpringAnnotations()

        val controllerFile = myFixture.configureByText(
            "DemoController.java",
            """
                package example;

                import org.springframework.web.bind.annotation.GetMapping;
                import org.springframework.web.bind.annotation.RestController;

                @RestController
                class DemoController {
                    @GetMapping("/api/no-match")
                    String hello() {
                        return "ok";
                    }
                }
            """.trimIndent(),
        ) as PsiJavaFile

        val cache = BilateralMappingCacheService.of(project)
        cache.clear()
        // 客户端缓存为空，等同于工程里没有匹配 Feign/HttpExchange
        cache.replaceClient(emptyList())

        val controllerMethod = controllerFile.classes.single().methods.single()
        assertFalse(cache.hasClientCounterpart(controllerMethod))
    }

    fun testFindControllerTargetsFallsBackToProjectScanWhenControllerCacheIsEmpty() {
        myFixture.addFileToProject(
            "org/springframework/web/bind/annotation/RestController.java",
            """
                package org.springframework.web.bind.annotation;

                public @interface RestController {}
            """.trimIndent(),
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
            "org/springframework/cloud/openfeign/FeignClient.java",
            """
                package org.springframework.cloud.openfeign;

                public @interface FeignClient {
                    String name() default "";
                    String path() default "";
                }
            """.trimIndent(),
        )

        myFixture.configureByText(
            "DemoController.java",
            """
                package example;

                import org.springframework.web.bind.annotation.GetMapping;
                import org.springframework.web.bind.annotation.RestController;

                @RestController
                class DemoController {
                    @GetMapping("/api/hello")
                    String hello() {
                        return "ok";
                    }
                }
            """.trimIndent(),
        )
        val feignFile = myFixture.configureByText(
            "DemoClient.java",
            """
                package example;

                import org.springframework.cloud.openfeign.FeignClient;
                import org.springframework.web.bind.annotation.GetMapping;

                @FeignClient(name = "demo")
                interface DemoClient {
                    @GetMapping("/api/hello")
                    String hello();
                }
            """.trimIndent(),
        ) as PsiJavaFile

        val cache = BilateralMappingCacheService.of(project)
        cache.clear()

        val feignMethod = feignFile.classes.single().methods.single()
        val targets = cache.findControllerTargets(feignMethod)

        assertSize(1, targets)
        val targetMethod = ApplicationManager.getApplication().runReadAction(Computable {
            targets.single().resolveMethod()
        })
        assertNotNull(targetMethod)
        assertEquals("hello", targetMethod?.name)
    }

    private fun addSpringAnnotations() {
        myFixture.addFileToProject(
            "org/springframework/web/bind/annotation/RestController.java",
            """
                package org.springframework.web.bind.annotation;

                public @interface RestController {}
            """.trimIndent(),
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
            "org/springframework/cloud/openfeign/FeignClient.java",
            """
                package org.springframework.cloud.openfeign;

                public @interface FeignClient {
                    String name() default "";
                    String path() default "";
                }
            """.trimIndent(),
        )
    }
}
