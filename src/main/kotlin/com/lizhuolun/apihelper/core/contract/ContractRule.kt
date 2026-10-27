package com.lizhuolun.apihelper.core.contract

import com.intellij.psi.PsiMethod
import com.lizhuolun.apihelper.core.HttpMappingInfo

/**
 * 契约问题严重级。
 *
 * ERROR 表示两端协议不一致会直接导致运行时失败（400 / 404 / 启动失败）；
 * WARNING 表示大概率出问题或语义风险，允许被用户忽略。
 *
 * @author lizhuolun
 * @date 2026/10/27
 */
enum class ContractSeverity {
    ERROR,
    WARNING,
    INFO,
}

/**
 * 可自动修复的建议动作。
 *
 * P0 阶段只支持把客户端侧的参数名同步为服务端侧的名字，
 * 其余修复动作在后续版本扩展。
 *
 * @property kind 修复类型
 * @property targetName 期望改写成的名字
 * @property sourceName 当前注解上的名字，用于定位要修改的参数
 * @author lizhuolun
 * @date 2026/10/27
 */
data class ContractFix(
    val kind: ContractFixKind,
    val targetName: String,
    val sourceName: String = "",
)

/**
 * 修复类型枚举。
 */
enum class ContractFixKind {
    /** 把客户端侧参数注解上的名字同步为服务端侧的名字 */
    SYNC_PARAM_NAME,
}

/**
 * 一条契约问题。
 *
 * 直接持有 [HttpMappingInfo] 而不是单独的智能指针，
 * 因为渲染与跳转都需要 URL / HTTP 方法 / 类名等信息，且它们已经是不可变快照的一部分。
 *
 * @property ruleId 规则标识，与 [ContractRule.id] 一致
 * @property severity 严重级
 * @property message 已本地化的用户文案
 * @property client 客户端侧的映射，可能为 null（例如仅服务端内部的问题）
 * @property controllers 相关的服务端映射，可能为空列表
 * @property fixes 可用的自动修复建议
 * @author lizhuolun
 * @date 2026/10/27
 */
data class ContractIssue(
    val ruleId: String,
    val severity: ContractSeverity,
    val message: String,
    val client: HttpMappingInfo? = null,
    val controllers: List<HttpMappingInfo> = emptyList(),
    val fixes: List<ContractFix> = emptyList(),
) {

    /**
     * 问题去重键：同一条规则命中同一对方法只保留一条。
     */
    val dedupKey: String
        get() = buildString {
            append(ruleId).append('|')
            append(client?.qualifier.orEmpty()).append('|')
            controllers.map { it.qualifier }.sorted().forEach { append(it).append(',') }
        }

    /**
     * 全部相关方法，按客户端在前的顺序返回，供跳转弹窗使用。
     */
    fun allMappings(): List<HttpMappingInfo> =
        (listOfNotNull(client) + controllers)
}

/**
 * 一条契约规则的契约。
 *
 * 规则实现必须是无状态的：同一条规则会被反复调用，
 * 任何跨项目状态都应放在 [ContractCheckContext] 或签名缓存中。
 *
 * @author lizhuolun
 * @date 2026/10/27
 */
interface ContractRule {

    /**
     * 规则唯一标识，用于 i18n key `contract.rule.<id>` 与报告分组。
     */
    val id: String

    /**
     * 在给定上下文上产出问题列表。
     *
     * @param context 本次体检的上下文，包含全部配对与签名提供者
     * @return 本规则命中的问题列表；无命中时返回空列表
     */
    fun check(context: ContractCheckContext): List<ContractIssue>
}

/**
 * 一对客户端与服务端的配对结果。
 *
 * @property client 客户端侧映射
 * @property controllers 全部匹配的服务端映射；多实现场景下可能有多条
 * @author lizhuolun
 * @date 2026/10/27
 */
data class ContractPair(
    val client: HttpMappingInfo,
    val controllers: List<HttpMappingInfo>,
) {

    /**
     * 是否存在服务端对端。
     */
    val isPaired: Boolean
        get() = controllers.isNotEmpty()
}

/**
 * 从映射解析契约签名的抽象，便于规则层单测注入假数据而不依赖 PSI。
 *
 * @author lizhuolun
 * @date 2026/10/27
 */
fun interface ContractSignatureProvider {

    /**
     * 解析指定映射对应方法的契约签名。
     *
     * 调用方必须自行持有 read action；实现方可以在内部复用缓存。
     *
     * @param mapping 端点映射
     * @return 契约签名；方法已失效时返回 null
     */
    fun signatureOf(mapping: HttpMappingInfo): ContractSignature?
}

/**
 * 一次体检的上下文。
 *
 * @property pairs 全部配对结果，客户端数量与快照一致
 * @property snapshot 本次使用的双边快照
 * @property signatures 签名提供者
 * @property message 本地化消息函数，签名与 ApiHelperBundle.message 一致
 * @author lizhuolun
 * @date 2026/10/27
 */
class ContractCheckContext(
    val pairs: List<ContractPair>,
    val snapshot: BilateralSnapshot,
    val signatures: ContractSignatureProvider,
    val message: (key: String, params: List<Any>) -> String,
) {

    /**
     * 简化取签名的辅助方法。
     */
    fun signature(mapping: HttpMappingInfo): ContractSignature? =
        signatures.signatureOf(mapping)

    /**
     * 获取方法展示名，形如 `ClassName#methodName`。
     */
    fun displayName(method: PsiMethod?): String = method?.name.orEmpty()
}