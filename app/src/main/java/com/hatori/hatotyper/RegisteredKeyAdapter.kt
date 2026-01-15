package com.hatori.hatotyper

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class RegisteredKeyAdapter(private var keys: List<Pair<String, KeyCoord>>) :
    RecyclerView.Adapter<RegisteredKeyAdapter.ViewHolder>() {

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvInfo: TextView = view.findViewById(android.R.id.text1)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        // Android標準のシンプルなレイアウトを使用
        val view = LayoutInflater.from(parent.context)
            .inflate(android.R.id.simple_list_item_1, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val (char, coord) = keys[position]
        holder.tvInfo.text = "キー: $char  |  座標: (${coord.x.toInt()}, ${coord.y.toInt()})"
    }

    override fun getItemCount() = keys.size

    fun updateData(newData: List<Pair<String, KeyCoord>>) {
        keys = newData
        notifyDataSetChanged()
    }
}
