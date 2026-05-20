package com.qile.ossbrowser

import android.app.Application
import android.util.Log
import com.alibaba.sdk.android.oss.ClientConfiguration
import com.alibaba.sdk.android.oss.OSSClient
import com.alibaba.sdk.android.oss.common.auth.OSSCredentialProvider
import com.alibaba.sdk.android.oss.common.auth.OSSPlainTextAKSKCredentialProvider
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class OSSBrowserApp : Application() {

    companion object {
        private const val TAG = "OSSBrowserApp"

        @Volatile
        private var instance: OSSBrowserApp? = null

        fun getInstance(): OSSBrowserApp = instance ?: synchronized(this) {
            instance ?: OSSBrowserApp().also { instance = it }
        }
    }

    private var ossClient: OSSClient? = null

    private val _isConnected = MutableStateFlow(false)
    val isConnected: StateFlow<Boolean> = _isConnected

    override fun onCreate() {
        super.onCreate()
        instance = this
        Log.d(TAG, "OSSBrowserApp initialized")
    }

    /**
     * 初始化 OSS 客户端
     */
    fun initOSSClient(
        endpoint: String,
        accessKeyId: String,
        accessKeySecret: String
    ): OSSClient {
        // 确保 endpoint 格式正确
        val formattedEndpoint = if (!endpoint.startsWith("http://") && !endpoint.startsWith("https://")) {
            "https://$endpoint"
        } else {
            endpoint
        }

        val credentialProvider: OSSCredentialProvider =
            OSSPlainTextAKSKCredentialProvider(accessKeyId, accessKeySecret)

        val conf = ClientConfiguration().apply {
            // 连接超时时间（毫秒）
            connectionTimeout = 30000
            // Socket 超时时间（毫秒）
            socketTimeout = 60000
            // 最大并发请求数
            maxConcurrentRequest = 5
            // 失败后重试次数
            maxErrorRetry = 3
        }

        ossClient = OSSClient(applicationContext, formattedEndpoint, credentialProvider, conf)
        _isConnected.value = true
        Log.d(TAG, "OSS client initialized with endpoint: $formattedEndpoint")
        return ossClient!!
    }

    /**
     * 获取 OSS 客户端
     */
    fun getOSSClient(): OSSClient {
        return ossClient ?: throw IllegalStateException("OSS 客户端未初始化，请先登录")
    }

    /**
     * 断开连接
     */
    fun disconnect() {
        ossClient = null
        _isConnected.value = false
        Log.d(TAG, "OSS client disconnected")
    }
}
