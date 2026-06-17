package com.lizhuolun.apihelper.cache

import com.intellij.util.messages.Topic

/**
 * 缓存变更监听器，用于解耦缓存层与 UI 层。
 *
 * @author lizhuolun
 * @date 2026/6/12
 */
interface CacheChangeListener {

    /**
     * 双边映射缓存发生变更时回调。
     */
    fun onCacheChanged()

    companion object {

        /**
         * 消息总线 topic。
         */
        val TOPIC: Topic<CacheChangeListener> = Topic.create(
            "ApiHelper Cache Changed",
            CacheChangeListener::class.java,
        )
    }
}
