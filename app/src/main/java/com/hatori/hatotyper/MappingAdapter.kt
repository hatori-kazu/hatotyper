package com.hatori.hatotyper

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.TextView
import androidx.appcompat.widget.SwitchCompat
import androidx.recyclerview.widget.RecyclerView

class MappingAdapter(
    private var mappings: MutableList<Mapping>,
    private val onEdit: (Mapping) -> Unit,
    private val onDelete: (Mapping) -> Unit,
    private val onToggle: (Mapping, Boolean) -> Unit
) : RecyclerView.Adapter<MappingAdapter.ViewHolder>() {

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvTrigger: TextView = view.findViewById(R.id.tvTrigger)
        val tvOutput: TextView = view.findViewById(R.id.tvOutput)
        val swEnabled: SwitchCompat = view.findViewById(R.id.switchEnabled) // SwitchCompatに修正
        val btnDelete: ImageButton = view.findViewById(R.id.btnDelete)
        val btnEdit: ImageButton = view.findViewById(R.id.btnEdit)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_mapping, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = mappings[position]
        holder.tvTrigger.text = "トリガー: ${item.trigger}"
        holder.tvOutput.text = "出力: ${item.output}"
        
        // リスナーの重複登録を避けるため一度nullにする
        holder.swEnabled.setOnCheckedChangeListener(null)
        holder.swEnabled.isChecked = item.isEnabled
        
        holder.swEnabled.setOnCheckedChangeListener { _, isChecked ->
            onToggle(item, isChecked)
        }

        holder.btnEdit.setOnClickListener { onEdit(item) }
        holder.btnDelete.setOnClickListener { onDelete(item) }
    }

    override fun getItemCount() = mappings.size

    fun updateData(newList: List<Mapping>) {
        mappings.clear()
        mappings.addAll(newList)
        notifyDataSetChanged()
    }
}
