package me.huidoudour.QRCode.scan

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import java.text.SimpleDateFormat
import java.util.*

class HistoryAdapter(
    private var scanResults: List<ScanResult>,
    private val onActionSelected: (ScanResult, String) -> Unit
) : RecyclerView.Adapter<HistoryAdapter.ViewHolder>() {

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val contentTextView: TextView = view.findViewById(R.id.contentTextView)
        val remarkTextView: TextView = view.findViewById(R.id.remarkTextView)
        val timestampTextView: TextView = view.findViewById(R.id.timestampTextView)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_history, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val scanResult = scanResults[position]
        holder.contentTextView.text = scanResult.content
        
        // 设置备注（如果有）
        if (!scanResult.remark.isNullOrEmpty()) {
            holder.remarkTextView.text = scanResult.remark
            holder.remarkTextView.visibility = View.VISIBLE
        } else {
            holder.remarkTextView.visibility = View.GONE
        }
        
        // 格式化时间戳
        val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
        val dateString = dateFormat.format(Date(scanResult.timestamp))
        holder.timestampTextView.text = dateString

        holder.itemView.setOnLongClickListener {
            val context = it.context
            MaterialAlertDialogBuilder(context, R.style.Theme_CodeScan_Dialog)
                .setTitle(context.getString(R.string.dialog_title_select_action))
                .setItems(arrayOf(
                    context.getString(R.string.action_edit), 
                    context.getString(R.string.action_delete), 
                    context.getString(R.string.action_export)
                )) { dialog, which ->
                    when (which) {
                        0 -> onActionSelected(scanResult, "edit")
                        1 -> onActionSelected(scanResult, "delete")
                        2 -> onActionSelected(scanResult, "export")
                    }
                    dialog.dismiss()
                }
                .setBackgroundInsetStart(32)
                .setBackgroundInsetEnd(32)
                .show()
            true
        }
    }

    override fun getItemCount() = scanResults.size

    fun updateData(newScanResults: List<ScanResult>) {
        this.scanResults = newScanResults
        notifyDataSetChanged()
    }
}