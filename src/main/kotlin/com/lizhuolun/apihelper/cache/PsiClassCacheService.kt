package com.lizhuolun.apihelper.cache

import com.intellij.openapi.components.Service
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiClass
import com.intellij.psi.SmartPointerManager
import com.intellij.psi.SmartPsiElementPointer
import com.lizhuolun.apihelper.core.EndpointKind
import com.lizhuolun.apihelper.scanner.EndpointScanner
import java.util.concurrent.ConcurrentHashMap

/**
 * 项目级 PSI 类缓存：分别保存 Feign 客户端、HttpExchange 客户端、Controller 类。
 *
 * 使用 SmartPsiElementPointer 持有引用，避免 PSI 失效后访问崩溃。
 *
 * Service 由 IDEA 管理生命周期，Project 关闭时自动释放，无需手写 Clear。
 */
@Service(Service.Level.PROJECT)
class PsiClassCacheService(private val project: Project) {

    private val pointers: MutableMap<EndpointKind, MutableMap<String, SmartPsiElementPointer<PsiClass>>> =
        ConcurrentHashMap<EndpointKind, MutableMap<String, SmartPsiElementPointer<PsiClass>>>().apply {
            EndpointKind.entries.forEach { put(it, ConcurrentHashMap()) }
        }

    /**
     * 整体替换某一类的全部缓存项（一般在 StartupActivity 预热时调用）。
     */
    fun replaceAll(kind: EndpointKind, classes: Collection<PsiClass>) {
        val map = pointers.getValue(kind)
        map.clear()
        val smartManager = SmartPointerManager.getInstance(project)
        for (cls in classes) {
            val key = cls.qualifiedName ?: continue
            map[key] = smartManager.createSmartPsiElementPointer(cls)
        }
    }

    /**
     * 增量更新：插入或覆盖单个类的缓存。
     */
    fun upsert(psiClass: PsiClass) {
        val kind = EndpointScanner.resolveKind(psiClass) ?: return
        val key = psiClass.qualifiedName ?: return
        val map = pointers.getValue(kind)
        map[key] = SmartPointerManager.getInstance(project).createSmartPsiElementPointer(psiClass)
    }

    /**
     * 按 PsiClass 移除单个缓存项（用于 childRemoved 事件）。
     */
    fun remove(psiClass: PsiClass) {
        val key = psiClass.qualifiedName ?: return
        for (map in pointers.values) {
            map.remove(key)
        }
    }

    /**
     * 按全限定名移除（PSI 可能已经失效，无法再走 EndpointScanner.resolveKind）。
     */
    fun removeByQualifiedName(qualifiedName: String) {
        for (map in pointers.values) {
            map.remove(qualifiedName)
        }
    }

    /**
     * 获取指定类型的所有有效 PsiClass，自动过滤已失效的 SmartPointer。
     */
    fun getAll(kind: EndpointKind): List<PsiClass> {
        val map = pointers.getValue(kind)
        if (map.isEmpty()) return emptyList()
        val result = ArrayList<PsiClass>(map.size)
        val invalidKeys = ArrayList<String>()
        for ((key, pointer) in map) {
            val cls = pointer.element
            if (cls != null && cls.isValid) {
                result += cls
            } else {
                invalidKeys += key
            }
        }
        invalidKeys.forEach(map::remove)
        return result
    }

    /**
     * 缓存是否已被预热过（即至少有一种 kind 非空）。
     */
    fun isWarmedUp(): Boolean = pointers.values.any { it.isNotEmpty() }

    /**
     * 清空全部缓存。
     */
    fun clear() {
        pointers.values.forEach { it.clear() }
    }

    companion object {
        /**
         * 便捷获取入口，避免业务代码到处写 project.service<...>()。
         */
        fun of(project: Project): PsiClassCacheService = project.getService(PsiClassCacheService::class.java)
    }
}
