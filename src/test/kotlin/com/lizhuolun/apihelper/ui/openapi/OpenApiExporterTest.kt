package com.lizhuolun.apihelper.ui.openapi

import com.google.gson.JsonParser
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.lizhuolun.apihelper.cache.BilateralMappingCacheService
import com.lizhuolun.apihelper.scanner.EndpointScanner

class OpenApiExporterTest : BasePlatformTestCase() {

    fun testExportBuildsPathsParametersAndRequestBody() {
        addSpringAnnotations()
        myFixture.addFileToProject(
            "example/CreateOrderRequest.java",
            """
                package example;

                public class CreateOrderRequest {
                    public String productName;
                    public int count;
                }
            """.trimIndent(),
        )
        myFixture.configureByText(
            "OrderController.java",
            """
                package example;

                import org.springframework.web.bind.annotation.*;

                @RestController
                class OrderController {
                    @GetMapping("/api/order/{orderId}")
                    String detail(@PathVariable("orderId") Long orderId, @RequestParam("verbose") boolean verbose) {
                        return "ok";
                    }

                    @PostMapping("/api/order")
                    String create(@RequestBody CreateOrderRequest request) {
                        return "ok";
                    }
                }
            """.trimIndent(),
        )

        val cache = BilateralMappingCacheService.of(project)
        cache.clear()
        cache.replaceController(EndpointScanner.scanControllerEndpoints(project, null))
        cache.replaceClient(emptyList())

        val result = OpenApiExporter.export(project)!!
        val root = JsonParser.parseString(result.json).asJsonObject

        assertEquals("3.0.3", root.get("openapi").asString)
        assertEquals(2, result.operationCount)

        val paths = root.getAsJsonObject("paths")
        val detail = paths.getAsJsonObject("/api/order/{orderId}").getAsJsonObject("get")
        val parameters = detail.getAsJsonArray("parameters")
        val pathParam = parameters.single { it.asJsonObject.get("name").asString == "orderId" }.asJsonObject
        assertEquals("path", pathParam.get("in").asString)
        assertTrue(pathParam.get("required").asBoolean)
        assertEquals("integer", pathParam.getAsJsonObject("schema").get("type").asString)
        val queryParam = parameters.single { it.asJsonObject.get("name").asString == "verbose" }.asJsonObject
        assertEquals("query", queryParam.get("in").asString)
        assertEquals("boolean", queryParam.getAsJsonObject("schema").get("type").asString)

        val create = paths.getAsJsonObject("/api/order").getAsJsonObject("post")
        val properties = create
            .getAsJsonObject("requestBody")
            .getAsJsonObject("content")
            .getAsJsonObject("application/json")
            .getAsJsonObject("schema")
            .getAsJsonObject("properties")
        assertEquals("string", properties.getAsJsonObject("productName").get("type").asString)
        assertEquals("integer", properties.getAsJsonObject("count").get("type").asString)
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
            "org/springframework/web/bind/annotation/RequestBody.java",
            """
                package org.springframework.web.bind.annotation;

                public @interface RequestBody {
                    boolean required() default true;
                }
            """.trimIndent(),
        )
    }
}
