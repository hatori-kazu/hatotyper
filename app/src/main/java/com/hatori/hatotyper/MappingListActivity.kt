package com.hatori.hatotyper

import android.os.Bundle
import android.widget.EditText
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.floatingactionbutton.FloatingActionButton

class MappingListActivity : AppCompatActivity() {

    private lateinit var adapter: MappingAdapter
    private lateinit var rvMappings: RecyclerView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_mapping_list)

        rvMappings = findViewById(R.id.rvMappings)
        rvMappings.layoutManager = LinearLayoutManager(this)

        adapter = MappingAdapter(
            mutableListOf(),
            onEdit = { showEditDialog(it) },
            onDelete = { 
                KeyMapStorage.deleteMapping(this, it.trigger)
                refreshList()
            },
            onToggle = { mapping, isEnabled ->
                mapping.isEnabled = isEnabled
                KeyMapStorage.saveMapping(this, mapping)
            }
        )
        rvMappings.adapter = adapter

        findViewById<FloatingActionButton>(R.id.fabAdd).setOnClickListener {
            showEditDialog(null)
        }

        refreshList()
    }

    private fun refreshList() {
        try {
            val list = KeyMapStorage.getAllMappings(this)
            adapter.updateData(list)
        } catch (e: Exception) {
            // 万が一データが壊れていても空表示で耐える
            adapter.updateData(emptyList())
        }
    }

    private fun showEditDialog(mapping: Mapping?) {
        val builder = AlertDialog.Builder(this)
        val view = layoutInflater.inflate(R.layout.activity_main, null) // 入力用レイアウトを流用
        val etTarget = view.findViewById<EditText>(R.id.etTarget)
        val etInput = view.findViewById<EditText>(R.id.etInput)

        mapping?.let {
            etTarget.setText(it.trigger)
            etInput.setText(it.output)
        }

        builder.setView(view)
            .setTitle(if (mapping == null) "新規登録" else "編集")
            .setPositiveButton("保存") { _, _ ->
                val newMapping = Mapping(
                    trigger = etTarget.text.toString(),
                    output = etInput.text.toString(),
                    isEnabled = mapping?.isEnabled ?: true
                )
                KeyMapStorage.saveMapping(this, newMapping)
                refreshList()
            }
            .setNegativeButton("キャンセル", null)
            .show()
    }
}
