package me.huidoudour.QRCode.scan

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class StartupRecordAdapter(
    private var records: List<AppStartupRecord>
) : RecyclerView.Adapter<StartupRecordAdapter.ViewHolder>() {

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val idText: TextView = view.findViewById(R.id.idText)
        val timeText: TextView = view.findViewById(R.id.timeText)
        val pageText: TextView = view.findViewById(R.id.pageText)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_startup_record, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val record = records[position]
        val context = holder.itemView.context
        
        // ID列
        holder.idText.text = "${record.id}"
        
        // 时间列
        val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
        holder.timeText.text = dateFormat.format(Date(record.timestamp))
        
        // 启动页面列
        val pageName = when (record.startupPage) {
            "main" -> context.getString(R.string.startup_page_main)
            "quick" -> context.getString(R.string.startup_page_quick)
            else -> record.startupPage
        }
        holder.pageText.text = pageName
    }

    override fun getItemCount(): Int = records.size

    fun updateRecords(newRecords: List<AppStartupRecord>) {
        records = newRecords
        notifyDataSetChanged()
    }
}
