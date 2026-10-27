package com.lizhuolun.apihelper.core.contract

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Computable
import com.intellij.psi.util.PsiModificationTracker
import com.lizhuolun.apihelper.core.HttpMappingInfo

/**
 * 契约签名的项目级缓存。
 *
 * 缓存策略：
 * 1. 以 `qualifier` 为键，用访问顺序 LRU 限制条目数，避免大工程内存膨胀；
 * 2. 用 [PsiModificationTracker] 的修改计数做整体失效，
 *    源码一旦变化就清空缓存，保证不会用到过期签名；
 * 3. 对外暴露 [ContractSignatureProvider]，规则层单测可以注入假实现。
 *
 * @param project 当前工程
 * @author lizhuolun
 * @date 2026/10/27
 */
@Service(Service.Level.PROJECT)
class ContractSignatureCache(private val project: Project) : ContractSignatureProvider {

    /**
     * 访问顺序的 LRU 缓存，读写都在 [lock] 内完成。
     */
    private val cache = object : LinkedHashMap<String, ContractSignature>(INITIAL_CAPACITY, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, ContractSignature>): Boolean =
            size > MAX_ENTRIES
    }

    private val lock = Any()

    /**
     * 缓存建立时所对应的 PSI 修改计数。
     */
    private var cachedModificationCount: Long = -1L

    /**
     * 解析签名，优先命中缓存。
     *
     * @param mapping 端点映射
     * @return 契约签名；方法已失效或无法解析时返回 null
     */
    override fun signatureOf(mapping: HttpMappingInfo): ContractSignature? =
        ApplicationManager.getApplication().runReadAction(Computable {
            val method = mapping.resolveMethod() ?: return@Computable null
            val modificationCount = PsiModificationTracker.getInstance(project).modificationCount
            synchronized(lock) {
                if (modificationCount != cachedModificationCount) {
                    cache.clear()
                    cachedModificationCount = modificationCount
                }
                cache.getOrPut(mapping.qualifier) {
                    ContractSignatureExtractor.extract(method)
                }
            }
        })

    /**
     * 主动清空缓存，供测试或索引重建后调用。
     */
    fun invalidate() {
        synchronized(lock) {
            cache.clear()
            cachedModificationCount = -1L
        }
    }

    companion object {

        private const val INITIAL_CAPACITY = 256

        /**
         * 单工程最多缓存的签名数量。
         */
        private const val MAX_ENTRIES = 2000

        /**
         * 获取工程级缓存实例。
         *
         * @param project 当前工程
         * @return 契约签名缓存
         */
        fun of(project: Project): ContractSignatureCache =
            project.getService(ContractSignatureCache::class.java)
    }
}