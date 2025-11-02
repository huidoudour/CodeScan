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

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentExportBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        arguments?.getString("content_to_export")?.let {
            binding.inputText.setText(it)
        }

        binding.generateEan13Button.setOnClickListener {
            generateCode(BarcodeFormat.EAN_13, 12)
        }

        binding.generateQrButton.setOnClickListener {
            binding.inputText.hint = getString(R.string.hint_export_input_qr)
            generateCode(BarcodeFormat.QR_CODE, -1) // No length limit for QR
        }
    }

    private fun generateCode(format: BarcodeFormat, requiredLength: Int) {
        val text = binding.inputText.text.toString()
        if (text.isEmpty()) {
            return
        }

        if (requiredLength > 0 && text.length != requiredLength) {
            Toast.makeText(requireContext(), getString(R.string.toast_ean13_length_error), Toast.LENGTH_SHORT).show()
            return
        }

        try {
            val multiFormatWriter = MultiFormatWriter()
            // For EAN_13, ZXing automatically calculates the checksum for 12-digit input
            val bitMatrix = multiFormatWriter.encode(text, format, 400, 200)
            val barcodeEncoder = BarcodeEncoder()
            val bitmap: Bitmap = barcodeEncoder.createBitmap(bitMatrix)
            binding.generatedCode.setImageBitmap(bitmap)
        } catch (e: WriterException) {
            e.printStackTrace()
            Toast.makeText(requireContext(), getString(R.string.toast_generation_error, e.message), Toast.LENGTH_LONG).show()
        } catch (e: IllegalArgumentException) {
            // ZXing throws this for invalid EAN-13 content
            e.printStackTrace()
            Toast.makeText(requireContext(), getString(R.string.toast_generic_error, e.message), Toast.LENGTH_LONG).show()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}