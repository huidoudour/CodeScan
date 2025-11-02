package me.huidoudour.QRCode.scan

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
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
        val editText = EditText(requireContext()).apply {
            setText(scanResult.content)
        }

        AlertDialog.Builder(requireContext())
            .setTitle(getString(R.string.dialog_title_edit_result))
            .setView(editText)
            .setPositiveButton(getString(R.string.button_save)) { _, _ ->
                val newContent = editText.text.toString()
                lifecycleScope.launch {
                    db.scanResultDao().update(scanResult.copy(content = newContent))
                    loadHistory()
                }
            }
            .setNegativeButton(getString(R.string.button_cancel), null)
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