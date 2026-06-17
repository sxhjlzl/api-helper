package com.lizhuolun.apihelper.config

import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiDirectory
import org.yaml.snakeyaml.Yaml
import java.util.Properties

/**
 * Spring 应用配置读取器。
 *
 * 负责定位 Controller 所在模块的 resources 目录，
 * 解析 application/bootstrap 系列文件（含 profile 变体），
 * 把 yml 的嵌套结构扁平化成 dotted-key Map。
 *
 * 最终返回的 Map 兼容 PlaceholderResolver 的检索约定。
 */
object ApplicationConfigReader {

    private val LOG = thisLogger()

    /**
     * 6 个候选文件名，扫描顺序决定优先级（后面读到的覆盖前面的）。
     * 与 Spring Boot 的优先级保持一致：bootstrap > application，profile > 默认。
     */
    private val DEFAULT_FILES = listOf(
        "application.properties",
        "application.yml",
        "application.yaml",
        "bootstrap.properties",
        "bootstrap.yml",
        "bootstrap.yaml",
    )

    private val CONFIG_EXTENSIONS = setOf("properties", "yml", "yaml")

    /**
     * 判断文件名是否属于 Spring Boot application/bootstrap 配置文件。
     */
    fun isSpringConfigFile(fileName: String): Boolean {
        val extension = fileName.substringAfterLast('.', missingDelimiterValue = "")
        if (extension !in CONFIG_EXTENSIONS) return false
        val baseName = fileName.substringBeforeLast('.')
        return baseName == "application" ||
                baseName == "bootstrap" ||
                baseName.startsWith("application-") ||
                baseName.startsWith("bootstrap-")
    }

    /**
     * 读取给定 Controller 类所在模块的合并后配置。
     */
    fun readConfigForClass(psiClass: PsiClass, manualProfile: String?): Map<String, Any?> {
        val resourcesDir = findResourcesDir(psiClass) ?: return emptyMap()
        return readConfigFromResources(resourcesDir, manualProfile)
    }

    /**
     * 在指定 resources 目录下读取配置。
     */
    fun readConfigFromResources(resourcesDir: VirtualFile, manualProfile: String?): Map<String, Any?> {
        val defaults = loadFiles(resourcesDir, DEFAULT_FILES)
        val activeProfiles = ProfileResolver.resolveActiveProfiles(defaults, manualProfile)
        if (activeProfiles.isEmpty()) return defaults

        val merged = LinkedHashMap<String, Any?>(defaults)
        for (profile in activeProfiles) {
            val profileFiles = listOf(
                "application-$profile.properties",
                "application-$profile.yml",
                "application-$profile.yaml",
                "bootstrap-$profile.properties",
                "bootstrap-$profile.yml",
                "bootstrap-$profile.yaml",
            )
            merged.putAll(loadFiles(resourcesDir, profileFiles))
        }
        return merged
    }

    /**
     * 按给定文件名顺序读取并合并配置；后读到的会覆盖前读到的。
     */
    private fun loadFiles(resourcesDir: VirtualFile, fileNames: List<String>): Map<String, Any?> {
        val merged = LinkedHashMap<String, Any?>()
        for (name in fileNames) {
            val file = resourcesDir.findChild(name) ?: continue
            try {
                if (name.endsWith(".properties")) {
                    merged.putAll(parseProperties(file))
                } else {
                    merged.putAll(parseYaml(file))
                }
            } catch (e: ProcessCanceledException) {
                throw e
            } catch (e: Exception) {
                LOG.warn("ApiHelper: 解析配置文件失败, file=${file.path}", e)
            }
        }
        return merged
    }

    /**
     * 解析 .properties 文件。
     */
    private fun parseProperties(file: VirtualFile): Map<String, Any?> {
        val props = Properties()
        file.inputStream.use { props.load(it) }
        return props.entries.associate { it.key.toString() to it.value }
    }

    /**
     * 解析 .yml/.yaml 文件并扁平化为 dotted-key Map。
     */
    private fun parseYaml(file: VirtualFile): Map<String, Any?> {
        val yaml = Yaml()
        val rawText = file.inputStream.use { it.readBytes().toString(Charsets.UTF_8) }
        val root: Any? = yaml.load(rawText)
        val flat = LinkedHashMap<String, Any?>()
        if (root is Map<*, *>) flatten("", root, flat)
        return flat
    }

    /**
     * 递归扁平化嵌套 Map，按 dotted key 写入扁平 Map。
     * 数组按 [index] 形式写入，普通对象用 . 拼接。
     */
    private fun flatten(prefix: String, node: Any?, out: MutableMap<String, Any?>) {
        when (node) {
            is Map<*, *> -> {
                for ((rawKey, value) in node) {
                    val key = rawKey?.toString() ?: continue
                    val nextPrefix = if (prefix.isEmpty()) key else "$prefix.$key"
                    flatten(nextPrefix, value, out)
                }
            }
            is List<*> -> {
                node.forEachIndexed { idx, item ->
                    flatten("$prefix[$idx]", item, out)
                }
                out[prefix] = node
            }
            else -> {
                if (prefix.isNotEmpty()) out[prefix] = node
            }
        }
    }

    // ============ 模块 resources 目录定位 ============

    /**
     * 从 PsiClass 出发，向上找到包含 src 子目录的模块根，
     * 然后在 src/main/resources 或更靠下的 resources 中查找配置文件目录。
     *
     * @param psiClass 定位起点
     * @return 找到的 resources 目录，找不到时返回 null
     */
    fun findResourcesDir(psiClass: PsiClass): VirtualFile? {
        var dir: PsiDirectory? = psiClass.containingFile?.containingDirectory ?: return null
        while (dir != null) {
            val vDir = dir.virtualFile
            val resources = findResourcesRecursive(vDir, depth = 0)
            if (resources != null) return resources
            dir = dir.parentDirectory
        }
        return null
    }

    /**
     * 在指定目录下最多向下递归 4 层找名为 resources 的目录。
     */
    private fun findResourcesRecursive(dir: VirtualFile, depth: Int): VirtualFile? {
        if (!dir.isDirectory) return null
        if (depth > 4) return null
        if (dir.name == "resources") return dir
        for (child in dir.children) {
            if (!child.isDirectory) continue
            if (child.name.startsWith(".")) continue
            findResourcesRecursive(child, depth + 1)?.let { return it }
        }
        return null
    }

    // ============ 关键配置项快捷读取 ============

    /**
     * 读取 server.servlet.context-path，并解析占位符。
     */
    fun readContextPath(properties: Map<String, Any?>): String {
        val raw = properties["server.servlet.context-path"]?.toString().orEmpty()
        return PlaceholderResolver.resolve(raw, properties)
    }

    /**
     * 读取 spring.mvc.servlet.path，并解析占位符。
     */
    fun readMvcServletPath(properties: Map<String, Any?>): String {
        val raw = properties["spring.mvc.servlet.path"]?.toString().orEmpty()
        return PlaceholderResolver.resolve(raw, properties)
    }

    /**
     * 读取 server.port，并解析占位符。
     *
     * @param properties 配置项
     * @return 合法端口号，未配置或非法时返回 null
     */
    fun readServerPort(properties: Map<String, Any?>): Int? {
        val raw = properties["server.port"]?.toString().orEmpty()
        val resolved = PlaceholderResolver.resolve(raw, properties).trim()
        return resolved.toIntOrNull()?.takeIf { it in 1..65535 }
    }
}
