package com.hatori.hatotyper

import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.Switch
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView

class MappingAdapter(
    private val listener: Listener,
    private val startDrag: ((RecyclerView.ViewHolder) -> Unit)? = null
) : ListAdapter<Mapping, MappingAdapter.VH>(DIFF) {

    interface Listener {
        fun onEdit(mapping: Mapping)
        fun onDelete(mapping: Mapping)
        fun onToggleEnabled(mapping: Mapping, enabled: Boolean)
        fun onChangePriority(mapping: Mapping, newPriority: Int)
    }

    fun onItemMove(fromPosition: Int, toPosition: Int) {
        if (fromPosition == toPosition) return
        val tmp = currentList.toMutableList()
        val item = tmp.removeAt(fromPosition)
        tmp.add(toPosition, item)
        submitList(tmp)
    }

    fun getCurrentListCopy(): List<Mapping> = currentList.toList()

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val v = LayoutInflater.from(parent.context).inflate(R.layout.item_mapping, parent, false)
        return VH(v as android.view.ViewGroup)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val m = getItem(position)
        holder.bind(m)
    }

    inner class VH(itemView: ViewGroup) : RecyclerView.ViewHolder(itemView) {
        private val ivHandle: ImageView = itemView.findViewById(R.id.ivHandle)
        private val tvTrigger: TextView = itemView.findViewById(R.id.tvTrigger)
        private val tvOutput: TextView = itemView.findViewById(R.id.tvOutput)
        private val tvPriority: TextView = itemView.findViewById(R.id.tvPriority)
        private val btnUp: ImageButton = itemView.findViewById(R.id.btnUp)
        private val btnDown: ImageButton = itemView.findViewById(R.id.btnDown)
        private val swEnabled: Switch = itemView.findViewById(R.id.switchEnabled)
        private val btnEdit: ImageButton = itemView.findViewById(R.id.btnEdit)
        private val btnDelete: ImageButton = itemView.findViewById(R.id.btnDelete)

        fun bind(m: Mapping) {
            tvTrigger.text = m.trigger
            tvOutput.text = m.output
            tvPriority.text = m.priority.toString()

            swEnabled.setOnCheckedChangeListener(null)
            swEnabled.isChecked = m.enabled
            swEnabled.setOnCheckedChangeListener { _, checked ->
                listener.onToggleEnabled(m, checked)
            }

            btnEdit.setOnClickListener { listener.onEdit(m) }
            btnDelete.setOnClickListener { listener.onDelete(m) }

            btnUp.setOnClickListener { listener.onChangePriority(m, m.priority + 1) }
            btnDown.setOnClickListener { listener.onChangePriority(m, m.priority - 1) }

            ivHandle.setOnTouchListener { _, ev ->
                if (ev.action == MotionEvent.ACTION_DOWN) {
                    startDrag?.invoke(this)
                }
                false
            }
        }
    }

    companion object {
        private val DIFF = object : DiffUtil.ItemCallback<Mapping>() {
            override fun areItemsTheSame(oldItem: Mapping, newItem: Mapping): Boolean = oldItem.id == newItem.id
            override fun areContentsTheSame(oldItem: Mapping, newItem: Mapping): Boolean = oldItem == newItem
        }
    }
}
