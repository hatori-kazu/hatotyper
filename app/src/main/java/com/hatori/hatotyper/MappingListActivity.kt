package com.hatori.hatotyper

import android.os.Bundle
import android.text.InputType
import android.widget.EditText
import android.widget.LinearLayout
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.floatingactionbutton.FloatingActionButton

class MappingListActivity : AppCompatActivity(), MappingAdapter.Listener {

    private lateinit var rv: RecyclerView
    private lateinit var adapter: MappingAdapter
    private lateinit var fab: FloatingActionButton
    private lateinit var itemTouchHelper: ItemTouchHelper

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_mapping_list)

        rv = findViewById(R.id.rvMappings)
        fab = findViewById(R.id.fabAdd)

        adapter = MappingAdapter(this) { viewHolder ->
            if (::itemTouchHelper.isInitialized) itemTouchHelper.startDrag(viewHolder)
        }
        rv.layoutManager = LinearLayoutManager(this)
        rv.adapter = adapter

        val callback = object : ItemTouchHelper.SimpleCallback(
            ItemTouchHelper.UP or ItemTouchHelper.DOWN,
            0
        ) {
            override fun onMove(
                recyclerView: RecyclerView,
                viewHolder: RecyclerView.ViewHolder,
                target: RecyclerView.ViewHolder
            ): Boolean {
                val from = viewHolder.adapterPosition
                val to = target.adapterPosition
                adapter.onItemMove(from, to)
                return true
            }

            override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {}

            override fun clearView(recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder) {
                super.clearView(recyclerView, viewHolder)
                persistOrder()
            }
        }
        itemTouchHelper = ItemTouchHelper(callback)
        itemTouchHelper.attachToRecyclerView(rv)

        fab.setOnClickListener { showAddDialog() }

        refreshList()
    }

    private fun persistOrder() {
        val current = adapter.getCurrentListCopy()
        val n = current.size
        val reordered = current.mapIndexed { index, mapping ->
            mapping.copy(priority = n - index)
        }
        MappingStorage.saveAll(this, reordered)
        adapter.submitList(reordered)
    }

    private fun refreshList() {
        val list = MappingStorage.loadAll(this)
        adapter.submitList(list)
    }

    private fun showAddDialog() {
        val layout = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(24,12,24,0) }
        val etTrigger = EditText(this).apply { hint = getString(R.string.hint_mapping_trigger); inputType = InputType.TYPE_CLASS_TEXT }
        val etOutput = EditText(this).apply { hint = getString(R.string.hint_mapping_output); inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE }
        val etPriority = EditText(this).apply { hint = getString(R.string.hint_mapping_priority); inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_SIGNED }

        layout.addView(etTrigger)
        layout.addView(etOutput)
        layout.addView(etPriority)

        AlertDialog.Builder(this)
            .setTitle(getString(R.string.dialog_add_mapping_title))
            .setView(layout)
            .setPositiveButton("保存") { _, _ ->
                val t = etTrigger.text.toString().trim()
                val o = etOutput.text.toString()
                val p = etPriority.text.toString().toIntOrNull() ?: 0
                if (t.isNotEmpty()) {
                    MappingStorage.add(this, t, o, true, p)
                    refreshList()
                }
            }
            .setNegativeButton(getString(R.string.dialog_cancel), null)
            .show()
    }

    private fun showEditDialog(m: Mapping) {
        val layout = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(24,12,24,0) }
        val etTrigger = EditText(this).apply { setText(m.trigger); inputType = InputType.TYPE_CLASS_TEXT }
        val etOutput = EditText(this).apply { setText(m.output); inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE }
        val etPriority = EditText(this).apply { setText(m.priority.toString()); inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_SIGNED }

        layout.addView(etTrigger)
        layout.addView(etOutput)
        layout.addView(etPriority)

        AlertDialog.Builder(this)
            .setTitle(getString(R.string.dialog_edit_mapping_title))
            .setView(layout)
            .setPositiveButton("更新") { _, _ ->
                val t = etTrigger.text.toString().trim()
                val o = etOutput.text.toString()
                val p = etPriority.text.toString().toIntOrNull() ?: m.priority
                if (t.isNotEmpty()) {
                    MappingStorage.update(this, m.id, t, o, m.enabled, p)
                    refreshList()
                }
            }
            .setNegativeButton(getString(R.string.dialog_cancel), null)
            .show()
    }

    override fun onEdit(mapping: Mapping) = showEditDialog(mapping)

    override fun onDelete(mapping: Mapping) {
        AlertDialog.Builder(this)
            .setTitle(getString(R.string.delete_confirm_title))
            .setMessage(getString(R.string.delete_confirm_message, mapping.trigger, mapping.output))
            .setPositiveButton("削除") { _, _ ->
                MappingStorage.remove(this, mapping.id)
                refreshList()
            }
            .setNegativeButton(getString(R.string.dialog_cancel), null)
            .show()
    }

    override fun onToggleEnabled(mapping: Mapping, enabled: Boolean) {
        MappingStorage.setEnabled(this, mapping.id, enabled)
        refreshList()
    }

    override fun onChangePriority(mapping: Mapping, newPriority: Int) {
        MappingStorage.setPriority(this, mapping.id, newPriority)
        refreshList()
    }
}
