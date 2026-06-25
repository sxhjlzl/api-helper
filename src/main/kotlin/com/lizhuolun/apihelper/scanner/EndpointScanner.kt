package com.lizhuolun.apihelper.scanner

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Computable
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.JavaPsiFacade
import com.intellij.psi.PsiClass
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.search.searches.AnnotatedElementsSearch
import com.lizhuolun.apihelper.config.ApplicationConfigReader
import com.lizhuolun.apihelper.core.EndpointKind
import com.lizhuolun.apihelper.core.HttpMappingInfo
import com.lizhuolun.apihelper.core.annotation.AnnotationParser
import com.lizhuolun.apihelper.core.annotation.PathBuilder
import com.lizhuolun.apihelper.core.annotation.SpringAnnotations

/**
 * 端点扫描器：在工程中查找所有 FeignClient / HttpExchange / RestController，
 * 并把每个方法解析为 HttpMappingInfo。
 *
 * 实现原则：
 * 1. 所有 PSI 访问都包在 ReadAction.compute 内，即便调用方已在 read action 中也是幂等的
 * 2. DumbMode 期间直接返回空集，避免触发索引未就绪异常
 * 3. 使用 AnnotatedElementsSearch，依赖 Java Index 命中
 * 4. 同一次 scanControllerEndpoints 调用内对相同 resources 目录的配置只解析一次，
 *    避免大型项目里数百个 Controller 反复解析 yml
 *
 * @author lizhuolun
 * @date 2026/6/9
 */
object EndpointScanner {

    private val LOG = thisLogger()

    /**
     * 扫描所有客户端接口，含 Feign 与 HttpExchange。
     *
     * @param project 当前工程
     * @return 全部客户端方法对应的 HttpMappingInfo 列表
     */
    fun scanClientEndpoints(project: Project): List<HttpMappingInfo> {
        if (DumbService.isDumb(project)) return emptyList()
        val classes = findAnnotatedClasses(
            project,
            listOf(SpringAnnotations.FEIGN_CLIENT, SpringAnnotations.HTTP_EXCHANGE),
        )
        val result = ArrayList<HttpMappingInfo>(classes.size * 8)
        for (cls in classes) {
            val mappings = recoverableScan(
                message = { "ApiHelper: 客户端端点解析失败, class=${classNameForLog(cls)}" },
            ) {
                val kind = resolveKind(cls)
                    ?.takeIf { it == EndpointKind.FEIGN || it == EndpointKind.HTTP_EXCHANGE }
                    ?: return@recoverableScan emptyList()
                extractClientMappings(cls, kind)
            } ?: continue
            result.addAll(mappings)
        }
        return result
    }

    /**
     * 扫描所有 Controller 端点。
     *
     * @param project 当前工程
     * @param manualProfile 用户手动指定的 Spring profile，传 null 或空走自动推断
     * @return 全部 Controller 方法对应的 HttpMappingInfo 列表
     */
    fun scanControllerEndpoints(project: Project, manualProfile: String?): List<HttpMappingInfo> {
        if (DumbService.isDumb(project)) return emptyList()
        val classes = findAnnotatedClasses(
            project,
            listOf(SpringAnnotations.REST_CONTROLLER, SpringAnnotations.CONTROLLER),
        )
        val configCache = HashMap<String, Map<String, Any?>>()
        val result = ArrayList<HttpMappingInfo>(classes.size * 8)
        for (cls in classes) {
            val mappings = recoverableScan(
                message = { "ApiHelper: Controller 端点解析失败, class=${classNameForLog(cls)}" },
            ) {
                if (resolveKind(cls) != EndpointKind.CONTROLLER) {
                    return@recoverableScan emptyList()
                }
                extractControllerMappings(cls, manualProfile, configCache)
            } ?: continue
            result.addAll(mappings)
        }
        return result
    }

    /**
     * 从单个客户端接口中解析所有方法级映射。
     * Feign / HttpExchange 客户端的 URL 不依赖 application.yml，因此无需 profile 参数。
     *
     * @param psiClass 客户端接口
     * @param kind 端点类别
     * @return 该接口下所有方法的映射
     */
    fun extractClientMappings(
        psiClass: PsiClass,
        kind: EndpointKind,
    ): List<HttpMappingInfo> = computeInReadAction {
        val classPath = AnnotationParser.extractClassLevelPath(psiClass)
        val mappings = ArrayList<HttpMappingInfo>(psiClass.methods.size)
        for (method in psiClass.methods) {
            val annotation = AnnotationParser.findRestfulAnnotation(method) ?: continue
            val methodPath = AnnotationParser.extractMethodPath(annotation)
            val httpMethod = AnnotationParser.extractHttpMethod(annotation)
            mappings += HttpMappingInfo.create(
                url = PathBuilder.buildClientUrl(classPath, methodPath),
                httpMethod = httpMethod,
                method = method,
                kind = kind,
            )
        }
        mappings
    }

    /**
     * 从单个 Controller 类中解析所有方法级映射，需要拼接 server 与 mvc 前缀。
     *
     * @param psiClass Controller 类
     * @param manualProfile 用户手动指定的 Spring profile
     * @return 该类下所有方法的映射
     */
    fun extractControllerMappings(
        psiClass: PsiClass,
        manualProfile: String?,
    ): List<HttpMappingInfo> = extractControllerMappings(psiClass, manualProfile, configCache = null)

    /**
     * 同上，提供配置缓存入参以便批量扫描时减少重复 IO。
     *
     * @param psiClass Controller 类
     * @param manualProfile 用户手动指定的 Spring profile
     * @param configCache 以 resources 目录路径为 key 的 properties 缓存，可为 null
     * @return 该类下所有方法的映射
     */
    private fun extractControllerMappings(
        psiClass: PsiClass,
        manualProfile: String?,
        configCache: MutableMap<String, Map<String, Any?>>?,
    ): List<HttpMappingInfo> = computeInReadAction {
        val classPath = AnnotationParser.extractClassLevelPath(psiClass)
        val properties = readPropertiesWithCache(psiClass, manualProfile, configCache)
        val contextPath = ApplicationConfigReader.readContextPath(properties)
        val mvcPath = ApplicationConfigReader.readMvcServletPath(properties)
        val mappings = ArrayList<HttpMappingInfo>(psiClass.methods.size)
        for (method in psiClass.methods) {
            val annotation = AnnotationParser.findRestfulAnnotation(method) ?: continue
            val methodPath = AnnotationParser.extractMethodPath(annotation)
            val httpMethod = AnnotationParser.extractHttpMethod(annotation)
            mappings += HttpMappingInfo.create(
                url = PathBuilder.buildControllerUrl(contextPath, mvcPath, classPath, methodPath),
                httpMethod = httpMethod,
                method = method,
                kind = EndpointKind.CONTROLLER,
            )
        }
        mappings
    }

    /**
     * 给定一个 PsiClass，计算其端点类别；不可识别时返回 null。
     *
     * @param psiClass 待识别的类
     * @return 对应的 EndpointKind，无法识别时返回 null
     */
    fun resolveKind(psiClass: PsiClass): EndpointKind? = computeInReadAction {
        when {
            AnnotationParser.isFeignInterface(psiClass) -> EndpointKind.FEIGN
            AnnotationParser.isHttpExchangeInterface(psiClass) -> EndpointKind.HTTP_EXCHANGE
            AnnotationParser.isControllerClass(psiClass) -> EndpointKind.CONTROLLER
            else -> null
        }
    }

    /**
     * 找出工程中所有被指定注解标注的 PsiClass，使用 AnnotatedElementsSearch 走索引高速命中。
     *
     * @param project 当前工程
     * @param annotationFqns 要查找的注解全限定名列表
     * @return 命中的 PsiClass 列表，按注解顺序去重
     */
    private fun findAnnotatedClasses(
        project: Project,
        annotationFqns: List<String>,
    ): List<PsiClass> = computeInReadAction {
        val scope = GlobalSearchScope.projectScope(project)
        val facade = JavaPsiFacade.getInstance(project)
        val allScope = GlobalSearchScope.allScope(project)
        val collected = LinkedHashSet<PsiClass>()
        var anySearched = false

        for (fqn in annotationFqns) {
            recoverableScan(
                message = { "ApiHelper: AnnotatedElementsSearch 查询失败, fqn=$fqn" },
            ) {
                val annotationClass = facade.findClass(fqn, allScope)
                if (annotationClass == null) {
                    LOG.debug("ApiHelper: 未找到注解类, fqn=$fqn, 跳过")
                    return@recoverableScan
                }
                anySearched = true
                val query = AnnotatedElementsSearch.searchPsiClasses(annotationClass, scope)
                collected.addAll(query.findAll())
            }
        }

        if (!anySearched) {
            LOG.info("ApiHelper: 所有目标注解类均未在 classpath 中找到, 跳过扫描")
        }
        collected.toList()
    }

    /**
     * 读取 Controller 所在模块的 properties，优先命中缓存。
     *
     * @param psiClass Controller 类，用于定位模块 resources 目录
     * @param manualProfile 手动指定的 profile
     * @param configCache 调用方维护的缓存；为 null 时直接读取，不写入缓存
     * @return 合并后的 properties Map
     */
    private fun readPropertiesWithCache(
        psiClass: PsiClass,
        manualProfile: String?,
        configCache: MutableMap<String, Map<String, Any?>>?,
    ): Map<String, Any?> {
        if (configCache == null) {
            return ApplicationConfigReader.readConfigForClass(psiClass, manualProfile)
        }
        val resourcesDir: VirtualFile? = ApplicationConfigReader.findResourcesDir(psiClass)
        val cacheKey = resourcesDir?.path ?: return ApplicationConfigReader.readConfigForClass(psiClass, manualProfile)
        return configCache.getOrPut(cacheKey) {
            ApplicationConfigReader.readConfigFromResources(resourcesDir, manualProfile)
        }
    }

    /**
     * Kotlin 包装的 Application.runReadAction(Computable)，传入 lambda 即可。
     * 在 read action 内调用时是幂等的，不会重复持有读锁。
     *
     * @param block 在 read action 内执行的逻辑
     * @return block 的返回值
     */
    private inline fun <T> computeInReadAction(crossinline block: () -> T): T =
        ApplicationManager.getApplication().runReadAction(Computable { block() })

    /**
     * 执行一次可恢复扫描步骤。索引或 PSI 的瞬态不一致只影响当前步骤，
     * 取消操作仍继续向上抛出，避免吞掉用户取消与 IDE 取消信号。
     *
     * @param message 延迟构造的日志信息
     * @param block 扫描逻辑
     * @return 扫描结果；失败时返回 null
     */
    private inline fun <T> recoverableScan(
        message: () -> String,
        block: () -> T,
    ): T? = try {
        block()
    } catch (e: ProcessCanceledException) {
        throw e
    } catch (e: Exception) {
        LOG.warn(message(), e)
        null
    }

    /**
     * 获取用于日志的类名。这里不能让日志辅助逻辑影响主流程。
     *
     * @param psiClass PSI 类
     * @return 类名；无法读取时返回 unknown
     */
    private fun classNameForLog(psiClass: PsiClass): String = try {
        computeInReadAction {
            psiClass.qualifiedName ?: psiClass.name ?: "anonymous"
        }
    } catch (_: Exception) {
        "unknown"
    }
}
