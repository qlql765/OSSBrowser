package com.qile.ossbrowser.ui.file

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.qile.ossbrowser.data.OSSFileRepository
import com.qile.ossbrowser.databinding.ItemFileBinding
import com.qile.ossbrowser.util.FileUtils

/**
 * 文件列表适配器
 */
class FileAdapter(
    private val onItemClick: (OSSFileRepository.OSSFileItem) -> Unit,
    private val onMoreClick: (OSSFileRepository.OSSFileItem, android.view.View) -> Unit
) : ListAdapter<OSSFileRepository.OSSFileItem, FileAdapter.FileViewHolder>(DiffCallback) {

    inner class FileViewHolder(
        private val binding: ItemFileBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(item: OSSFileRepository.OSSFileItem) {
            binding.tvFileName.text = item.name
            binding.tvFileInfo.text = FileUtils.getFileInfoText(item)
            FileUtils.setFileIcon(item.name, item.isFolder, binding.ivIcon)

            binding.root.setOnClickListener { onItemClick(item) }
            binding.ivMore.setOnClickListener { onMoreClick(item, binding.ivMore) }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): FileViewHolder {
        val binding = ItemFileBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return FileViewHolder(binding)
    }

    override fun onBindViewHolder(holder: FileViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    companion object DiffCallback : DiffUtil.ItemCallback<OSSFileRepository.OSSFileItem>() {
        override fun areItemsTheSame(
            oldItem: OSSFileRepository.OSSFileItem,
            newItem: OSSFileRepository.OSSFileItem
        ): Boolean = oldItem.key == newItem.key

        override fun areContentsTheSame(
            oldItem: OSSFileRepository.OSSFileItem,
            newItem: OSSFileRepository.OSSFileItem
        ): Boolean = oldItem == newItem
    }
}
