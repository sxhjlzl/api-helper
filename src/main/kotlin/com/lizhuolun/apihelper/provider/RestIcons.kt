package com.lizhuolun.apihelper.provider

import com.intellij.openapi.util.IconLoader

/**
 * 插件统一图标资源入口。
 *
 * 同名的 _dark.svg 会被 IDEA 在 Darcula 主题下自动选用，无需在此显式区分。
 */
object RestIcons {

    /** Feign / HttpExchange 客户端方法 -> Controller 的导航图标（右向三角） */
    val JUMP_FEIGN_TO_CONTROLLER = IconLoader.getIcon("/icons/jumpAction_feign.svg", RestIcons::class.java)

    /** Controller 方法 -> Feign / HttpExchange 客户端的导航图标（左向三角） */
    val JUMP_CONTROLLER_TO_FEIGN = IconLoader.getIcon("/icons/jumpAction_controller.svg", RestIcons::class.java)
}
