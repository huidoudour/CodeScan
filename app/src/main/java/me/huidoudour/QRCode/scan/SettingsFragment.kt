package me.huidoudour.QRCode.scan

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
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
        
        binding.aboutCard.setOnClickListener {
            val intent = Intent(requireContext(), MeActivity::class.java)
            startActivity(intent)
        }
    }

    private fun updateLanguageDisplay() {
        val currentLanguage = getCurrentLanguage()
        binding.currentLanguage.text = currentLanguage
    }

    private fun getCurrentLanguage(): String {
        val languageCode = LanguageManager.getCurrentLanguage(requireActivity())
        
        return when (languageCode) {
            LanguageManager.LANGUAGE_ENGLISH -> getString(R.string.language_en)
            LanguageManager.LANGUAGE_CHINESE -> getString(R.string.language_zh)
            else -> getString(R.string.language_default)
        }
    }

    private fun showLanguageSelectionDialog() {
        val languages = arrayOf(
            getString(R.string.language_default),
            getString(R.string.language_en),
            getString(R.string.language_zh)
        )
        
        val currentLanguageCode = LanguageManager.getCurrentLanguage(requireActivity())
        
        val selectedIndex = when (currentLanguageCode) {
            LanguageManager.LANGUAGE_ENGLISH -> 1
            LanguageManager.LANGUAGE_CHINESE -> 2
            else -> 0
        }
        
        MaterialAlertDialogBuilder(requireContext(), R.style.Theme_CodeScan_Dialog)
            .setTitle(R.string.settings_language)
            .setSingleChoiceItems(languages, selectedIndex) { dialog, which ->
                when (which) {
                    0 -> setLanguage(LanguageManager.LANGUAGE_SYSTEM)
                    1 -> setLanguage(LanguageManager.LANGUAGE_ENGLISH)
                    2 -> setLanguage(LanguageManager.LANGUAGE_CHINESE)
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
        LanguageManager.saveLanguage(requireActivity(), languageCode)
        
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