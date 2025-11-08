package me.huidoudour.QRCode.scan

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import me.huidoudour.QRCode.scan.databinding.FragmentSettingsBinding

class SettingsFragment : Fragment() {

    private var _binding: FragmentSettingsBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentSettingsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        updateLanguageDisplay()
        
        binding.languageSettingItem.setOnClickListener {
            showLanguageSelectionDialog()
        }
    }

    private fun updateLanguageDisplay() {
        val currentLanguage = getCurrentLanguage()
        binding.currentLanguage.text = currentLanguage
    }

    private fun getCurrentLanguage(): String {
        val sharedPref = requireActivity().getSharedPreferences("app_preferences", Context.MODE_PRIVATE)
        val languageCode = sharedPref.getString("language_preference", "") ?: ""
        
        return when (languageCode) {
            "en" -> getString(R.string.language_en)
            "zh" -> getString(R.string.language_zh)
            else -> getString(R.string.language_default)
        }
    }

    private fun showLanguageSelectionDialog() {
        val languages = arrayOf(
            getString(R.string.language_default),
            getString(R.string.language_en),
            getString(R.string.language_zh)
        )
        
        val sharedPref = requireActivity().getSharedPreferences("app_preferences", Context.MODE_PRIVATE)
        val currentLanguageCode = sharedPref.getString("language_preference", "") ?: ""
        
        val selectedIndex = when (currentLanguageCode) {
            "en" -> 1
            "zh" -> 2
            else -> 0
        }
        
        MaterialAlertDialogBuilder(requireContext(), R.style.Theme_CodeScan_Dialog)
            .setTitle(R.string.settings_language)
            .setSingleChoiceItems(languages, selectedIndex) { dialog, which ->
                when (which) {
                    0 -> setLanguage("")
                    1 -> setLanguage("en")
                    2 -> setLanguage("zh")
                }
                dialog.dismiss()
            }
            .setNegativeButton(R.string.button_cancel) { dialog, _ ->
                dialog.dismiss()
            }
            .setBackgroundInsetStart(32)
            .setBackgroundInsetEnd(32)
            .show()
    }

    private fun setLanguage(languageCode: String) {
        val sharedPref = requireActivity().getSharedPreferences("app_preferences", Context.MODE_PRIVATE)
        with(sharedPref.edit()) {
            putString("language_preference", languageCode)
            apply()
        }
        
        // 更新显示
        updateLanguageDisplay()
        
        // 重启应用以应用语言更改
        restartApp()
    }

    private fun restartApp() {
        val intent = activity?.packageManager?.getLaunchIntentForPackage(activity?.packageName ?: "")
        intent?.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
        intent?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        startActivity(intent)
        activity?.finish()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}