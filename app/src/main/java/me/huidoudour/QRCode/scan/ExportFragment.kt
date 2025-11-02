package me.huidoudour.QRCode.scan

import android.graphics.Bitmap
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
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

        binding.generateQrButton.setOnClickListener {
            generateCode(BarcodeFormat.QR_CODE)
        }

        binding.generateBarcodeButton.setOnClickListener {
            generateCode(BarcodeFormat.CODE_128)
        }
    }

    private fun generateCode(format: BarcodeFormat) {
        val text = binding.inputText.text.toString()
        if (text.isEmpty()) {
            return
        }

        try {
            val multiFormatWriter = MultiFormatWriter()
            val bitMatrix = multiFormatWriter.encode(text, format, 300, 300)
            val barcodeEncoder = BarcodeEncoder()
            val bitmap: Bitmap = barcodeEncoder.createBitmap(bitMatrix)
            binding.generatedCode.setImageBitmap(bitmap)
        } catch (e: WriterException) {
            e.printStackTrace()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}