package com.qile.ossbrowser.data

import android.util.Log
import com.alibaba.sdk.android.oss.OSSClient
import com.alibaba.sdk.android.oss.model.GetObjectRequest
import com.alibaba.sdk.android.oss.model.ListObjectsRequest
import com.alibaba.sdk.android.oss.model.ListObjectsResult
import com.alibaba.sdk.android.oss.model.PutObjectRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStreamReader
import java.util.Date

/**
 * OSS 文件仓库 - 负责所有 OSS 文件操作
 */
class OSSFileRepository {

    companion object {
        private const val TAG = "OSSFileRepository"
        private const val MAX_KEYS = 200
    }

    /**
     * OSS 文件/文件夹信息
     */
    data class OSSFileItem(
        val key: String,           // 完整的 object key
        val name: String,          // 显示名称（最后一段路径）
        val isFolder: Boolean,     // 是否为文件夹
        val size: Long = 0,        // 文件大小（字节）
        val lastModified: Long = 0 // 最后修改时间
    )

    /**
     * 列出指定前缀下的文件和文件夹
     * @param ossClient OSS 客户端
     * @param bucket Bucket 名称
     * @param prefix 当前路径前缀（如 "folder/" 或 ""）
     */
    suspend fun listFiles(
        ossClient: OSSClient,
        bucket: String,
        prefix: String
    ): List<OSSFileItem> = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "Listing files: bucket=$bucket, prefix='$prefix'")

            val request = ListObjectsRequest(bucket, prefix, "", "/", MAX_KEYS)

            val result: ListObjectsResult = ossClient.listObjects(request)
            val items = mutableListOf<OSSFileItem>()

            // 添加子文件夹（CommonPrefixes）
            result.commonPrefixes?.forEach { folderPrefix ->
                val folderName = folderPrefix.removeSuffix("/").substringAfterLast("/")
                items.add(
                    OSSFileItem(
                        key = folderPrefix,
                        name = folderName,
                        isFolder = true
                    )
                )
            }

            // 添加文件（排除当前前缀本身）
            result.objectSummaries?.forEach { summary ->
                // 排除当前目录前缀本身
                if (summary.key != prefix && summary.key.isNotBlank()) {
                    val fileName = summary.key.substringAfterLast("/")
                    if (fileName.isNotBlank()) {
                        items.add(
                            OSSFileItem(
                                key = summary.key,
                                name = fileName,
                                isFolder = false,
                                size = summary.size,
                                lastModified = summary.lastModified?.time ?: 0L
                            )
                        )
                    }
                }
            }

            // 排序：文件夹在前，文件在后，各自按名称排序
            items.sortWith(compareBy({ !it.isFolder }, { it.name.lowercase() }))

            Log.d(TAG, "Found ${items.size} items (${items.count { it.isFolder }} folders, ${items.count { !it.isFolder }} files)")
            items
        } catch (e: Exception) {
            Log.e(TAG, "Failed to list files", e)
            throw e
        }
    }

    /**
     * 下载文件内容为文本
     */
    suspend fun downloadTextFile(
        ossClient: OSSClient,
        bucket: String,
        objectKey: String
    ): String = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "Downloading text file: $objectKey")
            val request = GetObjectRequest(bucket, objectKey)
            val getResult = ossClient.getObject(request)
            val inputStream = getResult.objectContent
            val reader = BufferedReader(InputStreamReader(inputStream))
            val content = reader.use { it.readText() }
            Log.d(TAG, "Downloaded ${content.length} chars from $objectKey")
            content
        } catch (e: Exception) {
            Log.e(TAG, "Failed to download file: $objectKey", e)
            throw e
        }
    }

    /**
     * 上传文本内容到 OSS
     */
    suspend fun uploadTextFile(
        ossClient: OSSClient,
        bucket: String,
        objectKey: String,
        content: String
    ) = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "Uploading text file: $objectKey (${content.length} chars)")
            // PutObjectRequest 第三个参数只支持：文件路径(String)、字节数组(ByteArray)、Uri
            // 用 ByteArray 方式上传文本内容
            val request = PutObjectRequest(bucket, objectKey, content.toByteArray(Charsets.UTF_8))
            ossClient.putObject(request)
            Log.d(TAG, "Successfully uploaded: $objectKey")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to upload file: $objectKey", e)
            throw e
        }
    }

    /**
     * 上传文件（InputStream 方式）
     */
    suspend fun uploadFile(
        ossClient: OSSClient,
        bucket: String,
        objectKey: String,
        inputStream: java.io.InputStream
    ) = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "Uploading file: $objectKey")
            // 读取 InputStream 到 ByteArray
            val bytes = inputStream.readBytes()
            val request = PutObjectRequest(bucket, objectKey, bytes)
            ossClient.putObject(request)
            Log.d(TAG, "Successfully uploaded: $objectKey (${bytes.size} bytes)")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to upload file: $objectKey", e)
            throw e
        }
    }

    /**
     * 下载文件到本地路径（用于下载功能）
     */
    suspend fun downloadFileToLocal(
        ossClient: OSSClient,
        bucket: String,
        objectKey: String,
        localPath: String
    ) = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "Downloading file to local: $objectKey -> $localPath")
            val request = GetObjectRequest(bucket, objectKey)
            request.setProgressListener { _, currentSize, _ ->
                Log.d(TAG, "Download progress: $currentSize bytes")
            }
            val result = ossClient.getObject(request)
            // 手动保存到本地文件
            val outputFile = java.io.File(localPath)
            outputFile.parentFile?.mkdirs()
            outputFile.outputStream().use { output ->
                result.objectContent.copyTo(output)
            }
            Log.d(TAG, "Download complete: $localPath")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to download file: $objectKey", e)
            throw e
        }
    }

    /**
     * 删除文件
     */
    suspend fun deleteFile(
        ossClient: OSSClient,
        bucket: String,
        objectKey: String
    ) = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "Deleting file: $objectKey")
            val request = com.alibaba.sdk.android.oss.model.DeleteObjectRequest(bucket, objectKey)
            ossClient.deleteObject(request)
            Log.d(TAG, "Successfully deleted: $objectKey")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to delete file: $objectKey", e)
            throw e
        }
    }
}
