package com.lizhuolun.apihelper.provider.linemarker

import com.intellij.openapi.project.Project
import com.intellij.psi.PsiMethod
import com.lizhuolun.apihelper.cache.BilateralMappingCacheService
import com.lizhuolun.apihelper.core.HttpMappingInfo
import com.lizhuolun.apihelper.core.annotation.AnnotationParser
import com.lizhuolun.apihelper.provider.RestIcons

/**
 * Controller 方法旁的导航 gutter。
 *
 * 左键：跳转到对端 FeignClient / HttpExchange 方法（多目标弹选择 popup）
 * 右键：调试当前接口或复制当前 Controller URL 到剪贴板
 */
class ControllerToFeignLineMarker : EndpointNavigationLineMarker() {

    override val markerIcon = RestIcons.JUMP_CONTROLLER_TO_FEIGN
    override val titleKey = "linemarker.controller.to.feign.title"
    override val accessibleKey = "linemarker.controller.accessible"
    override val debugPopupActionEnabled = true

    override fun isApplicable(method: PsiMethod): Boolean {
        val cls = method.containingClass ?: return false
        if (!AnnotationParser.isControllerClass(cls)) return false
        return AnnotationParser.findRestfulAnnotation(method) != null
    }

    override fun hasCounterpart(project: Project, method: PsiMethod): Boolean =
        BilateralMappingCacheService.of(project).hasClientCounterpart(method)

    override fun findTargets(project: Project, method: PsiMethod): List<HttpMappingInfo> =
        BilateralMappingCacheService.of(project)
            .findClientTargets(method)

    override fun resolveSelfMapping(project: Project, method: PsiMethod): HttpMappingInfo? =
        BilateralMappingCacheService.of(project).resolveMapping(method)
}
