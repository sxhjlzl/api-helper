package com.lizhuolun.apihelper.provider.linemarker

import com.intellij.openapi.project.Project
import com.intellij.psi.PsiMethod
import com.lizhuolun.apihelper.cache.BilateralMappingCacheService
import com.lizhuolun.apihelper.core.HttpMappingInfo
import com.lizhuolun.apihelper.core.annotation.AnnotationParser
import com.lizhuolun.apihelper.provider.RestIcons

/**
 * FeignClient / HttpExchange 客户端方法旁的导航 gutter。
 *
 * 左键：跳转到对端 Controller 方法（多目标弹选择 popup）
 * 右键：不提供操作，避免客户端侧误进入调试或复制不完整地址
 */
class FeignToControllerLineMarker : EndpointNavigationLineMarker() {

    override val markerIcon = RestIcons.JUMP_FEIGN_TO_CONTROLLER
    override val titleKey = "linemarker.feign.to.controller.title"
    override val accessibleKey = "linemarker.feign.accessible"
    override val tooltipHintKey = "linemarker.tooltip.left.click.only"
    override val popupActionsEnabled = false

    override fun isApplicable(method: PsiMethod): Boolean {
        val cls = method.containingClass ?: return false
        if (!AnnotationParser.isClientInterface(cls)) return false
        return AnnotationParser.findRestfulAnnotation(method) != null
    }

    override fun hasCounterpart(project: Project, method: PsiMethod): Boolean =
        BilateralMappingCacheService.of(project).hasControllerCounterpart(method)

    override fun findTargets(project: Project, method: PsiMethod): List<HttpMappingInfo> =
        BilateralMappingCacheService.of(project)
            .findControllerTargets(method)

    override fun resolveSelfMapping(project: Project, method: PsiMethod): HttpMappingInfo? =
        BilateralMappingCacheService.of(project).resolveMapping(method)
}
