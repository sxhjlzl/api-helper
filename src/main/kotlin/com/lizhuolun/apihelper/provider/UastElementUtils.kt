package com.lizhuolun.apihelper.provider

import com.intellij.psi.PsiElement
import com.intellij.psi.PsiMethod
import org.jetbrains.uast.UMethod
import org.jetbrains.uast.toUElementOfType

/**
 * UAST 辅助工具，给 LineMarker / IconProvider 共用。
 *
 * 同时兼容 Java 与 Kotlin 源码：
 * - Java：PsiMethod 自身实现了 UMethod，nameIdentifier 就是 leaf
 * - Kotlin：KtNamedFunction 由 UAST 转成 KotlinUMethod，javaPsi 是 LightMethod，
 *   uastAnchor.sourcePsi 指向 Kotlin 源码中的方法名 leaf
 */
internal object UastElementUtils {

    /**
     * 如果 leaf 是某个方法名标识符，返回对应的 PsiMethod；否则返回 null。
     *
     * 只对方法名 leaf 返回，避免方法体内的每个 token 都触发 LineMarker。
     */
    fun extractMethodForNameAnchor(leaf: PsiElement): PsiMethod? {
        val parent = leaf.parent ?: return null
        val uMethod: UMethod = parent.toUElementOfType<UMethod>() ?: return null
        val anchor = uMethod.uastAnchor?.sourcePsi ?: return null
        if (anchor != leaf) return null
        return uMethod.javaPsi
    }
}
