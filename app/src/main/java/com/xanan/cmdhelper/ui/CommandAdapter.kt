package com.xanan.cmdhelper.ui

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.xanan.cmdhelper.data.CommandEntry
import com.xanan.cmdhelper.databinding.ItemCommandBinding

/**
 * 命令列表适配器。
 *
 * 用 [ListAdapter] + [DiffUtil] 而不是 notifyDataSetChanged：
 * 命令文本可能很长，全量重绘会让滚动位置跳动；差分刷新能保持滚动稳定，
 * 也不会让正在长按的条目突然"失焦"。
 */
class CommandAdapter(
    private val onCopy: (CommandEntry) -> Unit,
    private val onPick: (CommandEntry) -> Unit,
    private val onLongPress: (CommandEntry, android.view.View) -> Unit
) : ListAdapter<CommandEntry, CommandAdapter.ViewHolder>(DIFF) {

    /**
     * 字号倍率，由设置页控制。
     *
     * 在 ViewHolder 里实时按倍率设置字号（而不是改 XML），这样切换字号时
     * 不用重建 ViewHolder，滚动位置也不会丢。
     */
    var fontScale: Float = 1.0f

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemCommandBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class ViewHolder(
        private val binding: ItemCommandBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(entry: CommandEntry) {
            // 原版历史里的命令有的带 `/` 有的不带，这里原样显示，不做"美化"，
            // 因为玩家往往就是靠这个区别来区分"聊天"和"指令"。
            binding.commandText.text = entry.text
            binding.commandText.textSize = BASE_TEXT_SIZE_SP * fontScale

            // 收藏状态用文字颜色的细微差别暗示，外加长按菜单里的明确开关。
            binding.copyIcon.setOnClickListener { onCopy(entry) }
            binding.root.setOnClickListener { onPick(entry) }
            binding.root.setOnLongClickListener { view ->
                onLongPress(entry, view)
                true
            }
        }
    }

    private companion object {
        /** 列表项基准字号（sp），与 dimens.xml 里的 list_item_text_size 保持一致。 */
        const val BASE_TEXT_SIZE_SP = 17f

        val DIFF = object : DiffUtil.ItemCallback<CommandEntry>() {
            override fun areItemsTheSame(oldItem: CommandEntry, newItem: CommandEntry): Boolean =
                oldItem.text == newItem.text

            override fun areContentsTheSame(oldItem: CommandEntry, newItem: CommandEntry): Boolean =
                oldItem == newItem
        }
    }
}
