package com.lizhuolun.apihelper.ui

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.util.Computable
import com.intellij.psi.PsiJavaFile
import com.intellij.testFramework.fixtures.BasePlatformTestCase

class EndpointParameterResolverTest : BasePlatformTestCase() {

    fun testExpandsUnannotatedDtoParameterIntoQueryFields() {
        val file = myFixture.configureByText(
            "DemoController.java",
            """
                package example;

                class ScreenVersionQueryPage {
                    private String keyword;
                    private Integer pageNo;
                    private Boolean enabled;
                    private static String ignoredStatic;
                    private transient String ignoredTransient;
                }

                class DemoController {
                    Object listScreenVersion(ScreenVersionQueryPage screenVersionQueryPage) {
                        return null;
                    }
                }
            """.trimIndent(),
        ) as PsiJavaFile

        val parameter = file.classes
            .single { it.name == "DemoController" }
            .methods
            .single()
            .parameterList
            .parameters
            .single()
        val queryParams = readAction {
            EndpointParameterResolver.queryParameters(parameter)
        }

        assertEquals(listOf("keyword", "pageNo", "enabled"), queryParams.map { it.name })
        assertEquals(listOf("", "0", "true"), queryParams.map { it.sampleValue })
    }

    fun testExpandsSpringQueryMapParameterIntoQueryFields() {
        myFixture.addFileToProject(
            "org/springframework/cloud/openfeign/SpringQueryMap.java",
            """
                package org.springframework.cloud.openfeign;

                public @interface SpringQueryMap {}
            """.trimIndent(),
        )
        val file = myFixture.configureByText(
            "DemoClient.java",
            """
                package example;

                import org.springframework.cloud.openfeign.SpringQueryMap;

                class AdvertisersPageReq {
                    private String advertiserName;
                    private Long current;
                }

                interface DemoClient {
                    Object page(@SpringQueryMap AdvertisersPageReq req);
                }
            """.trimIndent(),
        ) as PsiJavaFile

        val parameter = file.classes
            .single { it.name == "DemoClient" }
            .methods
            .single()
            .parameterList
            .parameters
            .single()
        val queryParams = readAction {
            assertTrue(EndpointParameterResolver.hasQueryMapAnnotation(parameter))
            EndpointParameterResolver.queryParameters(parameter)
        }

        assertEquals(listOf("advertiserName", "current"), queryParams.map { it.name })
        assertEquals(listOf("", "0"), queryParams.map { it.sampleValue })
    }

    fun testExpandsDtoParameterIntoBodyFields() {
        val file = myFixture.configureByText(
            "DemoController.java",
            """
                package example;

                class AdvertiserSaveReq {
                    /** Advertiser name. */
                    private String name;

                    /** Advertiser age. */
                    private Integer age;

                    /** Whether enabled. */
                    private Boolean enabled;
                }

                class DemoController {
                    /**
                     * Saves advertiser.
                     *
                     * @param req save request
                     */
                    Object save(AdvertiserSaveReq req) {
                        return null;
                    }
                }
            """.trimIndent(),
        ) as PsiJavaFile

        val method = file.classes
            .single { it.name == "DemoController" }
            .methods
            .single()
        val parameter = method
            .parameterList
            .parameters
            .single()
        val bodyParams = readAction {
            EndpointParameterResolver.bodyParameters(parameter)
        }

        assertEquals("Saves advertiser.", readAction { EndpointParameterResolver.methodDescription(method) })
        assertEquals(listOf("name", "age", "enabled"), bodyParams.map { it.name })
        assertEquals(listOf("", "0", "true"), bodyParams.map { it.sampleValue })
        assertEquals(
            listOf("Advertiser name.", "Advertiser age.", "Whether enabled."),
            bodyParams.map { it.description },
        )
    }

    fun testReadsMethodParamCommentAsParameterDescription() {
        val file = myFixture.configureByText(
            "DemoController.java",
            """
                package example;

                class DemoController {
                    /**
                     * Finds advertiser detail.
                     *
                     * @param id advertiser id
                     */
                    Object detail(String id) {
                        return null;
                    }
                }
            """.trimIndent(),
        ) as PsiJavaFile

        val method = file.classes
            .single { it.name == "DemoController" }
            .methods
            .single()
        val parameter = method.parameterList.parameters.single()
        val descriptor = readAction {
            EndpointParameterResolver.parameterDescriptor(parameter)
        }

        assertEquals("Finds advertiser detail.", readAction { EndpointParameterResolver.methodDescription(method) })
        assertEquals("advertiser id", descriptor.description)
    }

    fun testReadsLineCommentAsFieldDescription() {
        val file = myFixture.configureByText(
            "DemoController.java",
            """
                package example;

                class AdvertiserQuery {
                    // Advertiser keyword.
                    private String keyword;

                    /* Current page number. */
                    private Integer current;
                }

                class DemoController {
                    Object list(AdvertiserQuery query) {
                        return null;
                    }
                }
            """.trimIndent(),
        ) as PsiJavaFile

        val parameter = file.classes
            .single { it.name == "DemoController" }
            .methods
            .single()
            .parameterList
            .parameters
            .single()
        val queryParams = readAction {
            EndpointParameterResolver.queryParameters(parameter)
        }

        assertEquals(listOf("Advertiser keyword.", "Current page number."), queryParams.map { it.description })
    }

    fun testReadsMultilineFieldJavadocAsDescription() {
        val file = myFixture.configureByText(
            "DemoController.java",
            """
                package example;

                class ScreenQuery {
                    /**
                     * 屏幕编码
                     */
                    private String screenCode;
                }

                class DemoController {
                    Object list(ScreenQuery query) {
                        return null;
                    }
                }
            """.trimIndent(),
        ) as PsiJavaFile

        val parameter = file.classes
            .single { it.name == "DemoController" }
            .methods
            .single()
            .parameterList
            .parameters
            .single()
        val queryParams = readAction {
            EndpointParameterResolver.queryParameters(parameter)
        }

        assertEquals(listOf("screenCode"), queryParams.map { it.name })
        assertEquals(listOf("屏幕编码"), queryParams.map { it.description })
    }

    fun testReadsGetterJavadocAsFieldDescriptionFallback() {
        val file = myFixture.configureByText(
            "DemoController.java",
            """
                package example;

                class ScreenQuery {
                    private String screenCode;

                    /**
                     * 屏幕编码
                     */
                    public String getScreenCode() {
                        return screenCode;
                    }
                }

                class DemoController {
                    Object list(ScreenQuery query) {
                        return null;
                    }
                }
            """.trimIndent(),
        ) as PsiJavaFile

        val parameter = file.classes
            .single { it.name == "DemoController" }
            .methods
            .single()
            .parameterList
            .parameters
            .single()
        val queryParams = readAction {
            EndpointParameterResolver.queryParameters(parameter)
        }

        assertEquals(listOf("screenCode"), queryParams.map { it.name })
        assertEquals(listOf("屏幕编码"), queryParams.map { it.description })
    }

    fun testReadsSwaggerAnnotationsAsDescriptions() {
        myFixture.addFileToProject(
            "io/swagger/v3/oas/annotations/Operation.java",
            """
                package io.swagger.v3.oas.annotations;

                public @interface Operation {
                    String summary() default "";
                    String description() default "";
                }
            """.trimIndent(),
        )
        myFixture.addFileToProject(
            "io/swagger/v3/oas/annotations/Parameter.java",
            """
                package io.swagger.v3.oas.annotations;

                public @interface Parameter {
                    String description() default "";
                }
            """.trimIndent(),
        )
        myFixture.addFileToProject(
            "io/swagger/v3/oas/annotations/media/Schema.java",
            """
                package io.swagger.v3.oas.annotations.media;

                public @interface Schema {
                    String description() default "";
                }
            """.trimIndent(),
        )
        myFixture.addFileToProject(
            "io/swagger/annotations/ApiModelProperty.java",
            """
                package io.swagger.annotations;

                public @interface ApiModelProperty {
                    String value() default "";
                }
            """.trimIndent(),
        )
        val file = myFixture.configureByText(
            "DemoController.java",
            """
                package example;

                import io.swagger.annotations.ApiModelProperty;
                import io.swagger.v3.oas.annotations.Operation;
                import io.swagger.v3.oas.annotations.Parameter;
                import io.swagger.v3.oas.annotations.media.Schema;

                class AdvertiserQuery {
                    @Schema(description = "Advertiser keyword.")
                    private String keyword;

                    @ApiModelProperty("Current page number.")
                    private Integer current;
                }

                class DemoController {
                    @Operation(summary = "Lists advertisers.")
                    Object list(@Parameter(description = "query object") AdvertiserQuery query) {
                        return null;
                    }
                }
            """.trimIndent(),
        ) as PsiJavaFile

        val method = file.classes
            .single { it.name == "DemoController" }
            .methods
            .single()
        val parameter = method.parameterList.parameters.single()
        val queryParams = readAction {
            EndpointParameterResolver.queryParameters(parameter)
        }

        assertEquals("Lists advertisers.", readAction { EndpointParameterResolver.methodDescription(method) })
        assertEquals("query object", readAction { EndpointParameterResolver.parameterDescriptor(parameter).description })
        assertEquals(listOf("Advertiser keyword.", "Current page number."), queryParams.map { it.description })
    }

    private fun <T> readAction(action: () -> T): T =
        ApplicationManager.getApplication().runReadAction(Computable { action() })
}
