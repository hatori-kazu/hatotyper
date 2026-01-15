package com.hatori.hatotyper

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class RegisteredKeyAdapter(
    private var keys: List<Pair<String, KeyCoord>>,
    private val onLongClick: (String) -> Unit // 削除用のコールバックを追加
) : RecyclerView.Adapter<RegisteredKeyAdapter.ViewHolder>() {

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvInfo: TextView = view.findViewById(android.R.id.text1)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(android.R.id.simple_list_item_1, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val (char, coord) = keys[position]
        holder.tvInfo.text = "キー: $char  |  座標: (${coord.x.toInt()}, ${coord.y.toInt()})"
        
        // 長押しで削除イベントを飛ばす
        holder.itemView.setOnLongClickListener {
            onLongClick(char)
            true
        }
    }

    override fun getItemCount() = keys.size

    fun updateData(newData: List<Pair<String, KeyCoord>>) {
        keys = newData
        notifyDataSetChanged()
    }
}
