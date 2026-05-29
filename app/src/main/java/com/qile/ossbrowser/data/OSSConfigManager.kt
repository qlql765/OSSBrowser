package com.qile.ossbrowser.data

import android.content.Context
import android.util.Log
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.runBlocking

/**
 * OSS 配置数据存储管理
 * 使用 DataStore Preferences 安全地保存用户的 OSS 凭证信息
 */
class OSSConfigManager(private val context: Context) {

    companion object {
        private const val TAG = "OSSConfigManager"

        private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(
            name = "oss_config"
        )

        private val KEY_ENDPOINT = stringPreferencesKey("endpoint")
        private val KEY_BUCKET = stringPreferencesKey("bucket")
        private val KEY_ACCESS_KEY_ID = stringPreferencesKey("access_key_id")
        private val KEY_ACCESS_KEY_SECRET = stringPreferencesKey("access_key_secret")
    }

    /**
     * OSS 配置数据类
     */
    data class OSSConfig(
        val endpoint: String = "",
        val bucket: String = "",
        val accessKeyId: String = "",
        val accessKeySecret: String = ""
    ) {
        /** 是否所有字段都已填写 */
        val isComplete: Boolean
            get() = endpoint.isNotBlank() && bucket.isNotBlank() &&
                    accessKeyId.isNotBlank() && accessKeySecret.isNotBlank()
    }

    /**
     * 保存 OSS 配置
     */
    suspend fun saveConfig(config: OSSConfig) {
        Log.d(TAG, "Saving OSS config (endpoint=${config.endpoint}, bucket=${config.bucket})")
        context.dataStore.edit { preferences ->
            preferences[KEY_ENDPOINT] = config.endpoint
            preferences[KEY_BUCKET] = config.bucket
            preferences[KEY_ACCESS_KEY_ID] = config.accessKeyId
            preferences[KEY_ACCESS_KEY_SECRET] = config.accessKeySecret
        }
    }

    /**
     * 获取 OSS 配置 Flow（用于观察变化）
     */
    fun getConfigFlow(): Flow<OSSConfig> {
        return context.dataStore.data.map { preferences ->
            OSSConfig(
                endpoint = preferences[KEY_ENDPOINT] ?: "",
                bucket = preferences[KEY_BUCKET] ?: "",
                accessKeyId = preferences[KEY_ACCESS_KEY_ID] ?: "",
                accessKeySecret = preferences[KEY_ACCESS_KEY_SECRET] ?: ""
            )
        }
    }

    /**
     * 获取当前保存的配置（一次性读取）
     */
    suspend fun getConfig(): OSSConfig {
        return getConfigFlow().first()
    }

    /**
     * 同步获取配置（阻塞式，用于启动时检查）
     */
    fun getConfigSync(): OSSConfig {
        return runBlocking {
            getConfigFlow().first()
        }
    }

    /**
     * 清除所有保存的配置
     */
    suspend fun clearConfig() {
        Log.d(TAG, "Clearing OSS config")
        context.dataStore.edit { it.clear() }
    }
}
