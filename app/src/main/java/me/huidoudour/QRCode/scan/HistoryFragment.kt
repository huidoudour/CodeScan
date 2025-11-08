package me.huidoudour.QRCode.scan

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.LinearLayout
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import kotlinx.coroutines.launch
import me.huidoudour.QRCode.scan.databinding.FragmentHistoryBinding

class HistoryFragment : Fragment() {

    private var _binding: FragmentHistoryBinding? = null
    private val binding get() = _binding!!

    private lateinit var db: AppDatabase
    private lateinit var adapter: HistoryAdapter

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentHistoryBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        db = AppDatabase.getDatabase(requireContext())

        adapter = HistoryAdapter(emptyList()) { scanResult, action ->
            when (action) {
                "edit" -> showEditDialog(scanResult)
                "delete" -> deleteScanResult(scanResult)
                "export" -> navigateToExport(scanResult)
            }
        }

        binding.historyRecyclerView.layoutManager = LinearLayoutManager(requireContext())
        binding.historyRecyclerView.adapter = adapter

        loadHistory()
    }

    private fun loadHistory() {
        lifecycleScope.launch {
            val scanResults = db.scanResultDao().getAll()
            adapter.updateData(scanResults)
        }
    }

    private fun showEditDialog(scanResult: ScanResult) {
        val dialogView = layoutInflater.inflate(R.layout.dialog_edit_result, null)
        val contentInputLayout = dialogView.findViewById<TextInputLayout>(R.id.contentInputLayout)
        val contentEditText = dialogView.findViewById<TextInputEditText>(R.id.contentEditText)
        val remarkInputLayout = dialogView.findViewById<TextInputLayout>(R.id.remarkInputLayout)
        val remarkEditText = dialogView.findViewById<TextInputEditText>(R.id.remarkEditText)
        
        contentEditText.setText(scanResult.content)
        remarkEditText.setText(scanResult.remark)
        remarkInputLayout.hint = getString(R.string.hint_remark)

        MaterialAlertDialogBuilder(requireContext(), R.style.Theme_CodeScan_Dialog)
            .setTitle(getString(R.string.dialog_title_edit_result))
            .setView(dialogView)
            .setPositiveButton(getString(R.string.button_save)) { dialog, _ ->
                val newContent = contentEditText.text.toString()
                val newRemark = remarkEditText.text.toString()
                lifecycleScope.launch {
                    db.scanResultDao().update(scanResult.copy(content = newContent, remark = newRemark))
                    loadHistory()
                }
                dialog.dismiss()
            }
            .setNegativeButton(getString(R.string.button_cancel)) { dialog, _ ->
                dialog.dismiss()
            }
            .setBackgroundInsetStart(32)
            .setBackgroundInsetEnd(32)
            .show()
    }

    private fun deleteScanResult(scanResult: ScanResult) {
        lifecycleScope.launch {
            db.scanResultDao().delete(scanResult)
            loadHistory()
        }
    }

    private fun navigateToExport(scanResult: ScanResult) {
        val exportFragment = ExportFragment().apply {
            arguments = Bundle().apply {
                putString("content_to_export", scanResult.content)
            }
        }

        (activity as? MainActivity)?.navigateToTab(R.id.navigation_export)

        parentFragmentManager.beginTransaction()
            .replace(R.id.fragment_container, exportFragment)
            .commit()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}