package me.huidoudour.QRCode.scan

import android.graphics.Bitmap
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import com.google.zxing.BarcodeFormat
import com.google.zxing.MultiFormatWriter
import com.google.zxing.WriterException
import com.journeyapps.barcodescanner.BarcodeEncoder
import me.huidoudour.QRCode.scan.databinding.FragmentExportBinding

class ExportFragment : Fragment() {

    private var _binding: FragmentExportBinding? = null
    private val binding get() = _binding!!
    private lateinit var db: AppDatabase

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentExportBinding.inflate(inflater, container, false)
        db = AppDatabase.getDatabase(requireContext())
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        arguments?.getString("content_to_export")?.let {
            binding.inputText.setText(it)
        }

        binding.generateEan13Button.setOnClickListener {
            binding.textInputLayout.hint = getString(R.string.hint_export_input_ean13)
            generateCode(BarcodeFormat.EAN_13)
        }
        
        binding.generateCode128Button.setOnClickListener {
            binding.textInputLayout.hint = getString(R.string.hint_export_input_code128)
            generateCode(BarcodeFormat.CODE_128) // No length limit for CODE_128
        }

        binding.generateQrButton.setOnClickListener {
            binding.textInputLayout.hint = getString(R.string.hint_export_input_qr)
            generateCode(BarcodeFormat.QR_CODE) // No length limit for QR
        }
    }

    private fun generateCode(format: BarcodeFormat) {
        val text = binding.inputText.text.toString()
        if (text.isEmpty()) {
            val hintText = when (format) {
                BarcodeFormat.EAN_13 -> getString(R.string.hint_export_input_ean13)
                BarcodeFormat.CODE_128 -> getString(R.string.hint_export_input_code128)
                BarcodeFormat.QR_CODE -> getString(R.string.hint_export_input_qr)
                else -> getString(R.string.export_input_required)
            }
            Toast.makeText(requireContext(), hintText, Toast.LENGTH_SHORT).show()
            binding.inputText.requestFocus()
            return
        }

        // EAN-13 的事前验证
        if (format == BarcodeFormat.EAN_13) {
            val (isValid, message) = validateEan13(text)
            if (!isValid) {
                Toast.makeText(requireContext(), message, Toast.LENGTH_LONG).show()
                return
            }
        }

        try {
            val multiFormatWriter = MultiFormatWriter()
            // 根据格式类型调整生成尺寸：QR码使用正方形，条形码使用宽矩形
            val (width, height) = when (format) {
                BarcodeFormat.QR_CODE -> Pair(400, 400)
                else -> Pair(600, 200) // 条形码使用更宽的尺寸
            }
            val bitMatrix = multiFormatWriter.encode(text, format, width, height)
            val barcodeEncoder = BarcodeEncoder()
            val bitmap: Bitmap = barcodeEncoder.createBitmap(bitMatrix)
            binding.generatedCode.setImageBitmap(bitmap)
        } catch (e: WriterException) {
            e.printStackTrace()
            // 如果是 EAN-13 失败且不符合规范，提示用户或自动降级到二维码
            if (format == BarcodeFormat.EAN_13) {
                val errorMsg = when {
                    text.length != 12 && text.length != 13 -> 
                        getString(R.string.export_ean13_digit_count_error, text.length)
                    text.any { !it.isDigit() } -> 
                        getString(R.string.export_ean13_digits_only_error)
                    else -> getString(R.string.export_ean13_generation_failed, e.message)
                }
                Toast.makeText(requireContext(), errorMsg, Toast.LENGTH_LONG).show()
            } else {
                val errorMsg = e.message ?: getString(R.string.barcode_generation_failed_generic)
                Toast.makeText(requireContext(), errorMsg, Toast.LENGTH_LONG).show()
            }
        } catch (e: IllegalArgumentException) {
            e.printStackTrace()
            val errorMsg = when {
                format == BarcodeFormat.EAN_13 -> getString(R.string.export_ean13_format_invalid)
                else -> e.message ?: getString(R.string.input_format_error)
            }
            Toast.makeText(requireContext(), errorMsg, Toast.LENGTH_LONG).show()
        }
    }

    private fun validateEan13(text: String): Pair<Boolean, String> {
        return when {
            text.length != 12 && text.length != 13 -> 
                Pair(false, getString(R.string.export_ean13_validation_error_length, text.length))
            text.any { !it.isDigit() } -> 
                Pair(false, getString(R.string.export_ean13_validation_error_chars))
            else -> Pair(true, "")
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}