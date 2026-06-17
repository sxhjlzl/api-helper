package com.lizhuolun.apihelper

import com.intellij.DynamicBundle
import org.jetbrains.annotations.Nls
import org.jetbrains.annotations.PropertyKey

private const val BUNDLE = "messages.ApiHelperBundle"

/**
 * 国际化资源访问入口。
 *
 * messages/ApiHelperBundle.properties 为英文默认
 * messages/ApiHelperBundle_zh_CN.properties 为中文
 */
object ApiHelperBundle : DynamicBundle(BUNDLE) {

    @Nls
    fun message(@PropertyKey(resourceBundle = BUNDLE) key: String, vararg params: Any): String =
        getMessage(key, *params)
}
