package com.qile.ossbrowser.ui.editor

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.qile.ossbrowser.OSSBrowserApp
import com.qile.ossbrowser.R
import com.qile.ossbrowser.data.OSSFileRepository
import com.qile.ossbrowser.databinding.ActivityTextEditorBinding
import kotlinx.coroutines.launch

/**
 * 文本编辑器页
 * - 下载并显示文本文件内容
 * - 编辑后可保存回 OSS
 * - 支持检测未保存的修改
 */
class TextEditorActivity : AppCompatActivity() {

    companion object {
        private const val TAG = "TextEditorActivity"
        const val EXTRA_BUCKET = "bucket"
        const val EXTRA_OBJECT_KEY = "object_key"
        const val EXTRA_FILE_NAME = "file_name"
    }

    private lateinit var binding: ActivityTextEditorBinding
    private lateinit var repository: OSSFileRepository

    private var bucket: String = ""
    private var objectKey: String = ""
    private var fileName: String = ""
    private var originalContent: String = ""
    private var isModified: Boolean = false
    private var isSaving: Boolean = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityTextEditorBinding.inflate(layoutInflater)
        setContentView(binding.root)

        repository = OSSFileRepository()

        bucket = intent.getStringExtra(EXTRA_BUCKET) ?: ""
        objectKey = intent.getStringExtra(EXTRA_OBJECT_KEY) ?: ""
        fileName = intent.getStringExtra(EXTRA_FILE_NAME) ?: ""

        setupToolbar()
        setupEditor()
        loadFileContent()
    }

    private fun setupToolbar() {
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = fileName
        supportActionBar?.subtitle = objectKey
    }

    override fun onSupportNavigateUp(): Boolean {
        handleBackPress()
        return true
    }

    override fun onBackPressed() {
        handleBackPress()
    }

    private fun setupEditor() {
        binding.etEditor.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}

            override fun afterTextChanged(s: Editable?) {
                val currentContent = s.toString()
                val wasModified = isModified
                isModified = currentContent != originalContent

                // 只在修改状态变化时更新 UI
                if (wasModified != isModified) {
                    updateModifiedState()
                }
            }
        })

        binding.fabSave.setOnClickListener {
            saveFile()
        }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_editor, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_save -> {
                saveFile()
                true
            }
            android.R.id.home -> {
                handleBackPress()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    /**
     * 加载文件内容
     */
    private fun loadFileContent() {
        showLoading(true)
        binding.etEditor.isEnabled = false

        lifecycleScope.launch {
            try {
                val app = application as OSSBrowserApp
                val ossClient = app.getOSSClient()
                val content = repository.downloadTextFile(ossClient, bucket, objectKey)

                originalContent = content
                isModified = false
                binding.etEditor.setText(content)
                binding.etEditor.isEnabled = true
                showLoading(false)
                updateModifiedState()
            } catch (e: Exception) {
                Log.e(TAG, "Failed to load file", e)
                showLoading(false)
                Toast.makeText(
                    this@TextEditorActivity,
                    getString(R.string.file_open_failed) + ": " + (e.message ?: ""),
                    Toast.LENGTH_LONG
                ).show()
                finish()
            }
        }
    }

    /**
     * 保存文件到 OSS
     */
    private fun saveFile() {
        if (isSaving) return

        val content = binding.etEditor.text.toString()
        if (content == originalContent) {
            Toast.makeText(this, "文件未修改", Toast.LENGTH_SHORT).show()
            return
        }

        isSaving = true
        binding.fabSave.isEnabled = false

        lifecycleScope.launch {
            try {
                val app = application as OSSBrowserApp
                val ossClient = app.getOSSClient()
                repository.uploadTextFile(ossClient, bucket, objectKey, content)

                originalContent = content
                isModified = false
                updateModifiedState()

                Toast.makeText(this@TextEditorActivity, R.string.file_save_success, Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                Log.e(TAG, "Failed to save file", e)
                Toast.makeText(
                    this@TextEditorActivity,
                    getString(R.string.file_save_failed, e.message ?: "未知错误"),
                    Toast.LENGTH_LONG
                ).show()
            } finally {
                isSaving = false
                binding.fabSave.isEnabled = true
            }
        }
    }

    /**
     * 处理返回按键 - 检查未保存的修改
     */
    private fun handleBackPress() {
        if (isModified) {
            AlertDialog.Builder(this)
                .setTitle(R.string.editor_unsaved_changes)
                .setMessage(R.string.editor_unsaved_changes)
                .setPositiveButton(R.string.editor_discard) { _, _ ->
                    finish()
                }
                .setNegativeButton(R.string.editor_keep_editing, null)
                .show()
        } else {
            finish()
        }
    }

    /**
     * 更新修改状态的 UI 显示
     */
    private fun updateModifiedState() {
        if (isModified) {
            supportActionBar?.subtitle = "● 已修改 · $objectKey"
            binding.fabSave.visibility = View.VISIBLE
        } else {
            supportActionBar?.subtitle = objectKey
            binding.fabSave.visibility = View.GONE
        }
    }

    private fun showLoading(show: Boolean) {
        binding.progressBar.visibility = if (show) View.VISIBLE else View.GONE
    }
}
