package com.lizhuolun.apihelper.listener

import com.intellij.openapi.project.DumbService
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiInvalidElementAccessException
import com.intellij.psi.PsiTreeChangeAdapter
import com.intellij.psi.PsiTreeChangeEvent
import com.lizhuolun.apihelper.cache.BilateralMappingCacheService
import com.lizhuolun.apihelper.cache.CacheChangeListener
import com.lizhuolun.apihelper.cache.PsiClassCacheService
import com.lizhuolun.apihelper.config.ApplicationConfigReader
import com.lizhuolun.apihelper.core.EndpointKind
import com.lizhuolun.apihelper.core.annotation.AnnotationParser
import com.lizhuolun.apihelper.scanner.EndpointScanner
import com.lizhuolun.apihelper.settings.ApiHelperSettings

/**
 * PSI 树变更监听器，维护 PsiClassCacheService 与 BilateralMappingCacheService 的实时性。
 *
 * 设计要点：
 * 1. 完整覆盖 childAdded / childReplaced / childRemoved / childrenChanged / childMoved / propertyChanged 六类事件
 * 2. 继承 PsiTreeChangeAdapter，只 override 实际关心的事件
 * 3. 在 Dumb 模式下直接 return，避免索引未就绪时触发异常
 * 4. 所有 PSI 访问前都先做 isValid / try-catch 防御，避免触碰已失效的节点（典型场景是 childRemoved 事件里 event.child 已经脱离 PSI 树，调用 getProject / getManager 会抛 PsiInvalidElementAccessException）
 *
 * @author lizhuolun
 * @date 2026/6/9
 */
class ApiHelperPsiListener : PsiTreeChangeAdapter() {

    override fun childAdded(event: PsiTreeChangeEvent) {
        handleUpsert(event.child)
    }

    override fun childReplaced(event: PsiTreeChangeEvent) {
        handleUpsert(event.newChild)
        handleUpsert(event.child)
    }

    override fun childrenChanged(event: PsiTreeChangeEvent) {
        handleUpsert(event.parent)
    }

    override fun childMoved(event: PsiTreeChangeEvent) {
        handleUpsert(event.child)
    }

    override fun propertyChanged(event: PsiTreeChangeEvent) {
        handleUpsert(event.element)
    }

    /**
     * 处理 childRemoved 事件，删除类或文件时同步从缓存中清掉对应的条目。
     *
     * 注意：event.child 在 childRemoved 触发时通常已经脱离 PSI 树（parent 为 null），
     * 直接调用其 project / manager 等方法会抛 PsiInvalidElementAccessException，因此优先使用 event.parent 解析 Project，
     * 并在访问任何节点前做 isValid 检查。
     *
     * @param event PSI 删除事件
     */
    override fun childRemoved(event: PsiTreeChangeEvent) {
        val parent = event.parent
        val removed = event.child
        val project = resolveProject(parent, removed) ?: return
        if (project.isDisposed) return
        if (DumbService.isDumb(project)) return

        if (parent != null && parent.isValid && scheduleConfigRefresh(parent)) return
        if (removed != null && removed.isValid && scheduleConfigRefresh(removed)) return

        if (parent != null && parent.isValid) {
            findPsiClass(parent)?.let { parentClass ->
                handleUpsert(parentClass)
                return
            }
        }

        val target = removed?.takeIf { it.isValid } ?: return
        val psiClass = target as? PsiClass ?: findPsiClass(target) ?: return
        val qualifiedName = psiClass.qualifiedName ?: return
        PsiClassCacheService.of(project).removeByQualifiedName(qualifiedName)
        BilateralMappingCacheService.of(project).removeByClassQualifiedName(qualifiedName)
        project.messageBus.syncPublisher(CacheChangeListener.TOPIC).onCacheChanged()
    }

    /**
     * 通用 upsert 入口，仅当变更触及 Feign / HttpExchange / Controller 类时才刷新缓存。
     *
     * @param element 变更涉及的 PSI 元素
     */
    private fun handleUpsert(element: PsiElement?) {
        if (element == null) return
        if (!element.isValid) return
        val project = safeProjectOf(element) ?: return
        if (project.isDisposed) return
        if (DumbService.isDumb(project)) return
        if (scheduleConfigRefresh(element)) return

        val psiClass = findPsiClass(element) ?: return
        val qualifiedName = psiClass.qualifiedName ?: return
        val classCache = PsiClassCacheService.of(project)
        val mappingCache = BilateralMappingCacheService.of(project)
        classCache.removeByQualifiedName(qualifiedName)
        mappingCache.removeByClassQualifiedName(qualifiedName)

        if (!AnnotationParser.isClientInterface(psiClass) &&
            !AnnotationParser.isControllerClass(psiClass)
        ) {
            return
        }

        classCache.upsert(psiClass)
        refreshMappings(project, psiClass)
    }

    /**
     * 类变更后重新解析其所有方法级映射，覆盖到缓存。
     *
     * @param project 当前工程
     * @param psiClass 发生变更的类
     */
    private fun refreshMappings(project: Project, psiClass: PsiClass) {
        val cache = BilateralMappingCacheService.of(project)
        val qualifiedName = psiClass.qualifiedName ?: return
        cache.removeByClassQualifiedName(qualifiedName)
        val kind = EndpointScanner.resolveKind(psiClass) ?: return
        val mappings = if (kind == EndpointKind.CONTROLLER) {
            val manualProfile = ApiHelperSettings.getInstance().state.manualActiveProfile
            EndpointScanner.extractControllerMappings(psiClass, manualProfile)
        } else {
            EndpointScanner.extractClientMappings(psiClass, kind)
        }
        for (info in mappings) cache.upsert(info)
        project.messageBus.syncPublisher(CacheChangeListener.TOPIC).onCacheChanged()
    }

    /**
     * Spring 配置变化时异步刷新全部 Controller 映射。
     *
     * @param element 触发事件的 PSI 元素，必须已通过 isValid 校验
     * @return 命中 Spring 配置并已调度刷新返回 true，否则 false
     */
    private fun scheduleConfigRefresh(element: PsiElement): Boolean {
        val fileName = try {
            element.containingFile?.virtualFile?.name
        } catch (_: PsiInvalidElementAccessException) {
            return false
        } ?: return false
        if (!ApplicationConfigReader.isSpringConfigFile(fileName)) return false
        val project = safeProjectOf(element) ?: return false
        BilateralMappingCacheService.of(project).scheduleControllerRefresh()
        return true
    }

    /**
     * 向上查找最近的 PsiClass 容器，自身就是 PsiClass 时直接返回。
     * 遍历父链时若遇到已失效节点立即停止，防止触发 PsiInvalidElementAccessException。
     *
     * @param element 起点元素
     * @return 最近的 PsiClass 祖先，找不到时返回 null
     */
    private fun findPsiClass(element: PsiElement): PsiClass? {
        var current: PsiElement? = element
        while (current != null) {
            if (!current.isValid) return null
            if (current is PsiClass) return current
            current = try {
                current.parent
            } catch (_: PsiInvalidElementAccessException) {
                return null
            }
        }
        return null
    }

    /**
     * 在多个候选 PSI 元素中挑出首个仍然有效的元素，并解析其所在 Project。
     *
     * @param elements 候选 PSI 元素，按优先级排序，已失效或抛异常的元素会被跳过
     * @return 解析到的 Project，全部失效时返回 null
     */
    private fun resolveProject(vararg elements: PsiElement?): Project? {
        for (element in elements) {
            if (element == null || !element.isValid) continue
            val project = safeProjectOf(element) ?: continue
            return project
        }
        return null
    }

    /**
     * 安全获取 PSI 元素所在 Project，吞掉元素失效引起的异常。
     *
     * @param element 已确认 isValid 的 PSI 元素
     * @return 元素所在 Project，元素在调用过程中失效时返回 null
     */
    private fun safeProjectOf(element: PsiElement): Project? {
        return try {
            element.project
        } catch (_: PsiInvalidElementAccessException) {
            null
        }
    }
}
