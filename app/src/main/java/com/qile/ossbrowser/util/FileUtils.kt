package com.qile.ossbrowser.util

import android.content.Context
import android.os.Environment
import android.widget.ImageView
import android.widget.TextView
import com.qile.ossbrowser.R
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 文件工具类
 */
object FileUtils {

    /**
     * 支持编辑的文本文件扩展名
     */
    private val TEXT_EXTENSIONS = setOf(
        "txt", "log", "json", "xml", "html", "htm", "css", "js", "ts",
        "jsx", "tsx", "vue", "svelte", "md", "markdown", "csv", "tsv",
        "yaml", "yml", "properties", "conf", "cfg", "ini", "sh", "bash",
        "bat", "cmd", "ps1", "py", "java", "kt", "kts", "c", "cpp", "h",
        "hpp", "cs", "go", "rs", "rb", "php", "sql", "r", "swift", "dart",
        "lua", "pl", "scala", "groovy", "gradle", "toml", "env", "gitignore",
        "dockerfile", "makefile", "cmake", "proto", "graphql", "tf", "hcl",
        "less", "scss", "sass", "styl", "svg", "xsl", "xslt", "xhtml"
    )

    /**
     * 判断文件是否为可编辑的文本文件
     */
    fun isTextFile(fileName: String): Boolean {
        val extension = fileName.substringAfterLast('.', "").lowercase()
        return extension in TEXT_EXTENSIONS || fileName.lowercase() in setOf(
            "makefile", "dockerfile", "gitignore", "env", "license", "readme"
        )
    }

    /**
     * 格式化文件大小
     */
    fun formatFileSize(size: Long): String {
        if (size <= 0) return "0 B"
        val units = arrayOf("B", "KB", "MB", "GB", "TB")
        val digitGroups = (Math.log10(size.toDouble()) / Math.log10(1024.0)).toInt()
        val index = digitGroups.coerceAtMost(units.size - 1)
        return String.format(Locale.getDefault(), "%.1f %s", size / Math.pow(1024.0, index.toDouble()), units[index])
    }

    /**
     * 格式化日期
     */
    fun formatDate(timestamp: Long): String {
        if (timestamp <= 0) return ""
        val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
        return sdf.format(Date(timestamp))
    }

    /**
     * 设置文件图标和颜色
     */
    fun setFileIcon(fileName: String, isFolder: Boolean, imageView: ImageView) {
        if (isFolder) {
            imageView.setImageResource(R.drawable.ic_folder)
            imageView.setColorFilter(R.color.folder_yellow)
        } else {
            val extension = fileName.substringAfterLast('.', "").lowercase()
            val iconRes = when {
                extension in listOf("jpg", "jpeg", "png", "gif", "bmp", "webp", "svg") ->
                    R.drawable.ic_image
                extension in listOf("mp4", "avi", "mkv", "mov", "wmv", "flv") ->
                    R.drawable.ic_video
                extension in listOf("mp3", "wav", "flac", "aac", "ogg") ->
                    R.drawable.ic_audio
                extension in listOf("pdf") ->
                    R.drawable.ic_pdf
                extension in listOf("doc", "docx") ->
                    R.drawable.ic_doc
                extension in listOf("xls", "xlsx") ->
                    R.drawable.ic_excel
                extension in listOf("ppt", "pptx") ->
                    R.drawable.ic_ppt
                extension in listOf("zip", "rar", "7z", "tar", "gz") ->
                    R.drawable.ic_archive
                isTextFile(fileName) ->
                    R.drawable.ic_text
                else ->
                    R.drawable.ic_file
            }
            imageView.setImageResource(iconRes)
        }
    }

    /**
     * 获取文件信息文本
     */
    fun getFileInfoText(item: com.qile.ossbrowser.data.OSSFileRepository.OSSFileItem): String {
        return if (item.isFolder) {
            "文件夹"
        } else {
            buildString {
                if (item.size > 0) append(formatFileSize(item.size))
                if (item.lastModified > 0) {
                    if (isNotEmpty()) append(" · ")
                    append(formatDate(item.lastModified))
                }
            }
        }
    }

    /**
     * 获取下载目录
     */
    fun getDownloadDir(context: Context): File {
        return File(
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
            "OSSBrowser"
        ).also {
            if (!it.exists()) it.mkdirs()
        }
    }

    /**
     * 从完整 key 中提取文件名（用于下载时）
     */
    fun getFileNameFromKey(key: String): String {
        return key.substringAfterLast("/")
    }
}
