package com.qile.ossbrowser.ui.file

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.PopupMenu
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.qile.ossbrowser.OSSBrowserApp
import com.qile.ossbrowser.R
import com.qile.ossbrowser.data.OSSConfigManager
import com.qile.ossbrowser.data.OSSFileRepository
import com.qile.ossbrowser.databinding.ActivityFileBrowseBinding
import com.qile.ossbrowser.ui.editor.TextEditorActivity
import com.qile.ossbrowser.ui.login.LoginActivity
import com.qile.ossbrowser.util.FileUtils
import kotlinx.coroutines.launch

/**
 * 文件浏览页
 * - 显示当前目录下的文件和文件夹列表
 * - 支持进入子文件夹、返回上级目录
 * - 支持编辑文本文件和下载文件
 */
class FileBrowseActivity : AppCompatActivity() {

    companion object {
        private const val TAG = "FileBrowseActivity"
        const val EXTRA_BUCKET = "bucket"
        const val EXTRA_PREFIX = "prefix"
    }

    // 文件选择器
    private val filePickerLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            result.data?.data?.let { uri ->
                uploadFile(uri)
            }
        }
    }

    private lateinit var binding: ActivityFileBrowseBinding
    private lateinit var repository: OSSFileRepository
    private lateinit var adapter: FileAdapter
    private lateinit var configManager: OSSConfigManager

    private var bucket: String = ""
    private var currentPrefix: String = ""
    private val pathStack = mutableListOf<String>() // 路径导航栈

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityFileBrowseBinding.inflate(layoutInflater)
        setContentView(binding.root)

        repository = OSSFileRepository()
        configManager = OSSConfigManager(this)

        bucket = intent.getStringExtra(EXTRA_BUCKET) ?: ""
        val startPrefix = intent.getStringExtra(EXTRA_PREFIX) ?: ""

        setupToolbar()
        setupRecyclerView()
        setupSwipeRefresh()
        setupFAB()
        setupBackPressed()

        // 加载文件列表
        loadFiles(startPrefix)
    }

    private fun setupToolbar() {
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(false)
    }

    private fun setupRecyclerView() {
        adapter = FileAdapter(
            onItemClick = { item ->
                if (item.isFolder) {
                    // 进入子文件夹
                    pathStack.add(currentPrefix)
                    loadFiles(item.key)
                } else if (FileUtils.isTextFile(item.name)) {
                    // 打开文本编辑器
                    openTextEditor(item)
                }
            },
            onMoreClick = { item, view ->
                showFileMenu(item, view)
            }
        )
        binding.recyclerView.layoutManager = LinearLayoutManager(this)
        binding.recyclerView.adapter = adapter
    }

    private fun setupSwipeRefresh() {
        binding.swipeRefresh.setColorSchemeResources(
            R.color.md_theme_primary,
            R.color.md_theme_secondary
        )
        binding.swipeRefresh.setOnRefreshListener {
            loadFiles(currentPrefix)
        }
    }

    private fun setupFAB() {
        binding.fabHome.setOnClickListener {
            pathStack.clear()
            loadFiles("")
        }
    }

    private fun setupBackPressed() {
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (pathStack.isNotEmpty()) {
                    val parentPrefix = pathStack.removeAt(pathStack.size - 1)
                    loadFiles(parentPrefix)
                } else {
                    isEnabled = false
                    onBackPressedDispatcher.onBackPressed()
                }
            }
        })
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_file_browse, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_upload -> {
                openFilePicker()
                true
            }
            R.id.action_logout -> {
                showLogoutConfirm()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    /**
     * 打开文件选择器
     */
    private fun openFilePicker() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "*/*"
        }
        filePickerLauncher.launch(intent)
    }

    /**
     * 上传文件到 OSS
     */
    private fun uploadFile(uri: Uri) {
        lifecycleScope.launch {
            try {
                // 获取文件名
                val fileName = getFileNameFromUri(uri)
                val objectKey = if (currentPrefix.isBlank()) {
                    fileName
                } else {
                    "$currentPrefix$fileName"
                }

                Toast.makeText(this@FileBrowseActivity, "正在上传: $fileName", Toast.LENGTH_SHORT).show()

                val app = application as OSSBrowserApp
                val ossClient = app.getOSSClient()

                // 读取文件内容并上传
                contentResolver.openInputStream(uri)?.use { inputStream ->
                    repository.uploadFile(ossClient, bucket, objectKey, inputStream)
                }

                Toast.makeText(this@FileBrowseActivity, "上传成功: $fileName", Toast.LENGTH_SHORT).show()

                // 刷新列表
                loadFiles(currentPrefix)
            } catch (e: Exception) {
                Log.e(TAG, "Upload failed", e)
                Toast.makeText(
                    this@FileBrowseActivity,
                    "上传失败: ${e.message}",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    /**
     * 从 URI 获取文件名
     */
    private fun getFileNameFromUri(uri: Uri): String {
        var fileName = "unknown"
        contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (nameIndex >= 0) {
                    fileName = cursor.getString(nameIndex)
                }
            }
        }
        return fileName
    }

    /**
     * 加载文件列表
     */
    private fun loadFiles(prefix: String) {
        currentPrefix = prefix
        updatePathDisplay()
        showLoading()

        lifecycleScope.launch {
            try {
                val app = application as OSSBrowserApp
                val ossClient = app.getOSSClient()
                val files = repository.listFiles(ossClient, bucket, prefix)
                showContent(files)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to load files", e)
                showError(e.message ?: "加载失败")
            }
        }
    }

    /**
     * 更新路径显示
     */
    private fun updatePathDisplay() {
        val displayPath = if (currentPrefix.isBlank()) {
            getString(R.string.root_path)
        } else {
            "/${currentPrefix.removeSuffix("/")}"
        }
        binding.tvCurrentPath.text = getString(R.string.current_path, displayPath)

        // 显示/隐藏返回根目录 FAB
        binding.fabHome.visibility = if (pathStack.isNotEmpty()) View.VISIBLE else View.GONE
    }

    /**
     * 显示文件操作菜单
     */
    private fun showFileMenu(item: OSSFileRepository.OSSFileItem, view: View) {
        if (item.isFolder) return // 文件夹暂不支持更多操作

        val popup = PopupMenu(this, view)
        popup.menuInflater.inflate(R.menu.menu_file_item, popup.menu)

        // 根据文件类型显示/隐藏编辑选项
        val editItem = popup.menu.findItem(R.id.action_edit)
        editItem.isVisible = FileUtils.isTextFile(item.name)

        popup.setOnMenuItemClickListener { menuItem ->
            when (menuItem.itemId) {
                R.id.action_edit -> {
                    openTextEditor(item)
                    true
                }
                R.id.action_download -> {
                    downloadFile(item)
                    true
                }
                R.id.action_delete -> {
                    showDeleteConfirm(item)
                    true
                }
                else -> false
            }
        }
        popup.show()
    }

    /**
     * 打开文本编辑器
     */
    private fun openTextEditor(item: OSSFileRepository.OSSFileItem) {
        val intent = Intent(this, TextEditorActivity::class.java).apply {
            putExtra(TextEditorActivity.EXTRA_BUCKET, bucket)
            putExtra(TextEditorActivity.EXTRA_OBJECT_KEY, item.key)
            putExtra(TextEditorActivity.EXTRA_FILE_NAME, item.name)
        }
        startActivity(intent)
    }

    /**
     * 下载文件
     */
    private fun downloadFile(item: OSSFileRepository.OSSFileItem) {
        lifecycleScope.launch {
            try {
                Toast.makeText(this@FileBrowseActivity, getString(R.string.downloading), Toast.LENGTH_SHORT).show()

                val app = application as OSSBrowserApp
                val ossClient = app.getOSSClient()
                val downloadDir = FileUtils.getDownloadDir(this@FileBrowseActivity)
                val localPath = "${downloadDir.absolutePath}/${FileUtils.getFileNameFromKey(item.key)}"

                repository.downloadFileToLocal(ossClient, bucket, item.key, localPath)

                Toast.makeText(
                    this@FileBrowseActivity,
                    getString(R.string.file_download_success, localPath),
                    Toast.LENGTH_LONG
                ).show()
            } catch (e: Exception) {
                Log.e(TAG, "Download failed", e)
                Toast.makeText(
                    this@FileBrowseActivity,
                    getString(R.string.file_download_failed, e.message),
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    /**
     * 显示删除确认对话框
     */
    private fun showDeleteConfirm(item: OSSFileRepository.OSSFileItem) {
        AlertDialog.Builder(this)
            .setTitle(R.string.menu_delete)
            .setMessage("确定要删除 \"${item.name}\" 吗？")
            .setPositiveButton(R.string.confirm) { _, _ ->
                deleteFile(item)
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    /**
     * 删除文件
     */
    private fun deleteFile(item: OSSFileRepository.OSSFileItem) {
        lifecycleScope.launch {
            try {
                val app = application as OSSBrowserApp
                val ossClient = app.getOSSClient()
                repository.deleteFile(ossClient, bucket, item.key)

                Toast.makeText(this@FileBrowseActivity, "删除成功: ${item.name}", Toast.LENGTH_SHORT).show()

                // 刷新列表
                loadFiles(currentPrefix)
            } catch (e: Exception) {
                Log.e(TAG, "Delete failed", e)
                Toast.makeText(
                    this@FileBrowseActivity,
                    "删除失败: ${e.message}",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    /**
     * 显示退出登录确认
     */
    private fun showLogoutConfirm() {
        AlertDialog.Builder(this)
            .setTitle(R.string.logout)
            .setMessage(R.string.logout_confirm)
            .setPositiveButton(R.string.confirm) { _, _ ->
                lifecycleScope.launch {
                    configManager.clearConfig()
                    (application as OSSBrowserApp).disconnect()
                    val intent = Intent(this@FileBrowseActivity, LoginActivity::class.java)
                    intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                    startActivity(intent)
                    finish()
                }
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    // ========== UI 状态管理 ==========

    private fun showLoading() {
        binding.progressBar.visibility = View.VISIBLE
        binding.layoutEmpty.visibility = View.GONE
        binding.layoutError.visibility = View.GONE
        binding.recyclerView.visibility = View.GONE
        binding.swipeRefresh.isRefreshing = false
    }

    private fun showContent(files: List<OSSFileRepository.OSSFileItem>) {
        binding.progressBar.visibility = View.GONE
        binding.layoutError.visibility = View.GONE
        binding.swipeRefresh.isRefreshing = false

        if (files.isEmpty()) {
            binding.layoutEmpty.visibility = View.VISIBLE
            binding.recyclerView.visibility = View.GONE
        } else {
            binding.layoutEmpty.visibility = View.GONE
            binding.recyclerView.visibility = View.VISIBLE
            adapter.submitList(files)
        }
    }

    private fun showError(message: String) {
        binding.progressBar.visibility = View.GONE
        binding.layoutEmpty.visibility = View.GONE
        binding.recyclerView.visibility = View.GONE
        binding.swipeRefresh.isRefreshing = false
        binding.layoutError.visibility = View.VISIBLE
        binding.tvError.text = getString(R.string.file_error, message)
    }
}
