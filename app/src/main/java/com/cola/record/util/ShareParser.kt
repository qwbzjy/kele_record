package com.cola.record.util

object ShareParser {

    private val urlRegex = Regex("""https?://[^\s，。！？、；：""''（）【】<>]+""")

    data class Parsed(val url: String, val title: String, val platform: String)

    fun parse(text: String): Parsed {
        val url = urlRegex.find(text)?.value
            ?.trimEnd('，', '。', ',', '.', '、', '；', ';', '）', '】')
            ?: ""
        var title = text.replace(urlRegex, " ")
            .replace(Regex("""\s+"""), " ")
            .trim()
            .trim('，', '。', ',', '.', ':', '：', '-', '|', ' ', '、')
        if (title.isBlank() && url.isNotBlank()) {
            title = runCatching { java.net.URI(url).host ?: "未命名链接" }.getOrDefault("未命名链接")
        }
        if (title.length > 120) title = title.take(120)
        return Parsed(url = url, title = title, platform = detectPlatform(url))
    }

    fun detectPlatform(url: String): String {
        val u = url.lowercase()
        return when {
            u.contains("xhslink") || u.contains("xiaohongshu") -> "小红书"
            u.contains("douyin") || u.contains("iesdouyin") -> "抖音"
            u.contains("weixin.qq.com") || u.contains("channels.weixin") ||
                u.contains("weishi") || u.contains("wxapp") -> "微信视频"
            u.isBlank() -> "其他"
            else -> "其他"
        }
    }
}
