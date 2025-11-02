package me.huidoudour.QRCode.scan

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class HistoryAdapter(
    private var scanResults: List<ScanResult>,
    private val onActionSelected: (ScanResult, String) -> Unit
) : RecyclerView.Adapter<HistoryAdapter.ViewHolder>() {

    interface OnActionSelectedListener {
        fun onActionSelected(scanResult: ScanResult, action: String)
    }

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val contentTextView: TextView = view.findViewById(android.R.id.text1)
        val timestampTextView: TextView = view.findViewById(android.R.id.text2)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(android.R.layout.simple_list_item_2, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val scanResult = scanResults[position]
        holder.contentTextView.text = scanResult.content
        holder.timestampTextView.text = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.getDefault()).format(java.util.Date(scanResult.timestamp))

        holder.itemView.setOnLongClickListener {
            android.app.AlertDialog.Builder(it.context)
                .setTitle("Select Action")
                .setItems(arrayOf("Edit", "Delete", "Export")) { _, which ->
                    when (which) {
                        0 -> onActionSelected(scanResult, "edit")
                        1 -> onActionSelected(scanResult, "delete")
                        2 -> onActionSelected(scanResult, "export")
                    }
                }
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