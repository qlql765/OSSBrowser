package com.qile.ossbrowser.ui.login

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.qile.ossbrowser.OSSBrowserApp
import com.qile.ossbrowser.R
import com.qile.ossbrowser.data.OSSConfigManager
import com.qile.ossbrowser.databinding.ActivityLoginBinding
import com.qile.ossbrowser.ui.file.FileBrowseActivity
import com.alibaba.sdk.android.oss.model.ListObjectsRequest
import kotlinx.coroutines.launch

/**
 * OSS 配置登录页
 * - 用户选择 Endpoint 地区、输入 Bucket、AccessKey ID、AccessKey Secret
 * - 自动加载已保存的配置
 * - 连接成功后保存配置并跳转到文件浏览页
 */
class LoginActivity : AppCompatActivity() {

    companion object {
        private const val TAG = "LoginActivity"

        /**
         * 地区名称 → 完整 Endpoint 映射
         * key: 地区标识（如 hangzhou）
         * value: 完整 endpoint（如 oss-cn-hangzhou.aliyuncs.com）
         */
        private val REGION_ENDPOINT_MAP = mapOf(
            "hangzhou" to "oss-cn-hangzhou.aliyuncs.com",
            "shanghai" to "oss-cn-shanghai.aliyuncs.com",
            "nanjing" to "oss-cn-nanjing.aliyuncs.com",
            "qingdao" to "oss-cn-qingdao.aliyuncs.com",
            "beijing" to "oss-cn-beijing.aliyuncs.com",
            "zhangjiakou" to "oss-cn-zhangjiakou.aliyuncs.com",
            "huhehaote" to "oss-cn-huhehaote.aliyuncs.com",
            "wulanchabu" to "oss-cn-wulanchabu.aliyuncs.com",
            "shenzhen" to "oss-cn-shenzhen.aliyuncs.com",
            "guangzhou" to "oss-cn-guangzhou.aliyuncs.com",
            "chengdu" to "oss-cn-chengdu.aliyuncs.com",
            "hongkong" to "oss-cn-hongkong.aliyuncs.com",
            "singapore" to "oss-ap-southeast-1.aliyuncs.com",
            "kuala-lumpur" to "oss-ap-southeast-3.aliyuncs.com",
            "jakarta" to "oss-ap-southeast-5.aliyuncs.com",
            "bangkok" to "oss-ap-southeast-7.aliyuncs.com",
            "tokyo" to "oss-ap-northeast-1.aliyuncs.com",
            "seoul" to "oss-ap-northeast-2.aliyuncs.com",
            "mumbai" to "oss-ap-south-1.aliyuncs.com",
            "muscat" to "oss-me-east-1.aliyuncs.com",
            "dubai" to "oss-me-east-2.aliyuncs.com",
            "london" to "oss-eu-west-1.aliyuncs.com",
            "frankfurt" to "oss-eu-central-1.aliyuncs.com",
            "paris" to "oss-eu-west-2.aliyuncs.com",
            "virginia" to "oss-us-east-1.aliyuncs.com",
            "silicon-valley" to "oss-us-west-1.aliyuncs.com",
            "toronto" to "oss-ca-central-1.aliyuncs.com",
            "monterrey" to "oss-nac-central-1.aliyuncs.com",
            "saopaulo" to "oss-sa-east-1.aliyuncs.com",
            "sydney" to "oss-ap-southeast-2.aliyuncs.com",
            "melbourne" to "oss-ap-southeast-4.aliyuncs.com",
        )

        /**
         * 从下拉选项中提取地区标识
         * 例如 "hangzhou（华东1）" → "hangzhou"
         */
        fun extractRegion(displayText: String): String {
            return displayText.substringBefore("（").trim()
        }
    }

    private lateinit var binding: ActivityLoginBinding
    private lateinit var configManager: OSSConfigManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 先检查是否有保存的配置，有则直接尝试自动登录
        configManager = OSSConfigManager(this)
        val config = configManager.getConfigSync()

        if (config.isComplete) {
            Log.d(TAG, "Found saved config, auto-connecting immediately...")
            // 有配置：直接尝试连接，不设置登录布局
            autoConnectAndNavigate(config)
            return
        }

        // 无配置：显示登录页面
        binding = ActivityLoginBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupToolbar()
        setupEndpointDropdown()
        setupButtons()
    }

    /**
     * 自动连接并跳转（不显示登录页面）
     */
    private fun autoConnectAndNavigate(config: OSSConfigManager.OSSConfig) {
        lifecycleScope.launch {
            try {
                val app = application as OSSBrowserApp
                val ossClient = app.initOSSClient(config.endpoint, config.accessKeyId, config.accessKeySecret)

                // 验证连接
                Log.d(TAG, "Auto-connecting to bucket: ${config.bucket}")
                val listRequest = ListObjectsRequest(config.bucket, "", "", "/", 1)
                ossClient.listObjects(listRequest)

                // 连接成功，直接跳转
                Log.d(TAG, "Auto-login successful, navigating...")
                val intent = Intent(this@LoginActivity, FileBrowseActivity::class.java).apply {
                    putExtra(FileBrowseActivity.EXTRA_BUCKET, config.bucket)
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                }
                startActivity(intent)
                finish()

            } catch (e: Exception) {
                Log.e(TAG, "Auto-login failed, showing login form", e)

                // 连接失败，显示登录页面并回填信息
                binding = ActivityLoginBinding.inflate(layoutInflater)
                setContentView(binding.root)

                setupToolbar()
                setupEndpointDropdown()
                setupButtons()

                // 回填保存的信息
                val savedRegion = REGION_ENDPOINT_MAP.entries
                    .firstOrNull { it.value == config.endpoint }?.key ?: config.endpoint
                val regions = resources.getStringArray(R.array.endpoint_regions)
                val displayText = regions.firstOrNull { it.startsWith(savedRegion) } ?: savedRegion
                binding.etEndpoint.setText(displayText, false)
                binding.etBucket.setText(config.bucket)
                binding.etAccessKeyId.setText(config.accessKeyId)
                binding.etAccessKeySecret.setText(config.accessKeySecret)

                Toast.makeText(
                    this@LoginActivity,
                    getString(R.string.login_failed, e.message ?: "连接失败，请检查配置"),
                    Toast.LENGTH_LONG
                ).show()
                (application as OSSBrowserApp).disconnect()
            }
        }
    }

    private fun setupToolbar() {
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(false)
    }

    /**
     * 设置 Endpoint 地区下拉选择
     */
    private fun setupEndpointDropdown() {
        val regions = resources.getStringArray(R.array.endpoint_regions)
        val adapter = ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, regions)
        binding.etEndpoint.setAdapter(adapter)
    }

    private fun setupButtons() {
        binding.btnLogin.setOnClickListener {
            attemptLogin()
        }

        binding.btnClear.setOnClickListener {
            lifecycleScope.launch {
                configManager.clearConfig()
                binding.etEndpoint.text?.clear()
                binding.etBucket.text?.clear()
                binding.etAccessKeyId.text?.clear()
                binding.etAccessKeySecret.text?.clear()
                Toast.makeText(this@LoginActivity, R.string.btn_clear, Toast.LENGTH_SHORT).show()
            }
        }
    }

    /**
     * 尝试连接 OSS
     */
    private fun attemptLogin() {
        val regionDisplay = binding.etEndpoint.text.toString().trim()
        val bucket = binding.etBucket.text.toString().trim()
        val accessKeyId = binding.etAccessKeyId.text.toString().trim()
        val accessKeySecret = binding.etAccessKeySecret.text.toString().trim()

        // 提取地区标识并转换为完整 endpoint
        val region = extractRegion(regionDisplay)
        val endpoint = REGION_ENDPOINT_MAP[region]

        // 验证输入
        if (region.isBlank() || endpoint == null || bucket.isBlank() ||
            accessKeyId.isBlank() || accessKeySecret.isBlank()
        ) {
            Toast.makeText(this, R.string.login_empty_fields, Toast.LENGTH_SHORT).show()
            return
        }

        // 显示加载状态
        binding.btnLogin.isEnabled = false
        binding.btnLogin.text = getString(R.string.login_connecting)

        lifecycleScope.launch {
            try {
                // 初始化 OSS 客户端
                val app = application as OSSBrowserApp
                val ossClient = app.initOSSClient(endpoint, accessKeyId, accessKeySecret)

                // 验证连接：用 listObjects 来验证 bucket 是否可访问
                Log.d(TAG, "Testing connection to bucket: $bucket, endpoint: $endpoint")
                val listRequest = ListObjectsRequest(bucket, "", "", "/", 1)
                ossClient.listObjects(listRequest)

                // 连接成功，保存配置（保存完整 endpoint）
                val config = OSSConfigManager.OSSConfig(
                    endpoint = endpoint,
                    bucket = bucket,
                    accessKeyId = accessKeyId,
                    accessKeySecret = accessKeySecret
                )
                configManager.saveConfig(config)

                Log.d(TAG, "Login successful, navigating to FileBrowseActivity")
                Toast.makeText(this@LoginActivity, R.string.login_success, Toast.LENGTH_SHORT).show()

                // 跳转到文件浏览页
                val intent = Intent(this@LoginActivity, FileBrowseActivity::class.java).apply {
                    putExtra(FileBrowseActivity.EXTRA_BUCKET, bucket)
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                }
                startActivity(intent)
                finish()

            } catch (e: Exception) {
                Log.e(TAG, "Login failed", e)
                Toast.makeText(
                    this@LoginActivity,
                    getString(R.string.login_failed, e.message ?: "未知错误"),
                    Toast.LENGTH_LONG
                ).show()
                (application as OSSBrowserApp).disconnect()
            } finally {
                binding.btnLogin.isEnabled = true
                binding.btnLogin.text = getString(R.string.btn_login)
            }
        }
    }
}
