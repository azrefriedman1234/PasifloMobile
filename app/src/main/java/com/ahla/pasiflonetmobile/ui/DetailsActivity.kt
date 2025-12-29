package com.ahla.pasiflonetmobile.ui
import android.content.Context
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.ahla.pasiflonetmobile.databinding.ActivityDetailsBinding
import com.ahla.pasiflonetmobile.td.TdRepository
import com.ahla.pasiflonetmobile.video.VideoProcessor
import com.bumptech.glide.Glide
class DetailsActivity : AppCompatActivity() {
    private lateinit var binding: ActivityDetailsBinding
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityDetailsBinding.inflate(layoutInflater)
        setContentView(binding.root)
        val videoPath = intent.getStringExtra("VIDEO_LOCAL_PATH") ?: ""
        val caption = intent.getStringExtra("MSG_TEXT") ?: ""
        binding.etCaption.setText(caption)
        if (videoPath.isNotEmpty()) Glide.with(this).load(videoPath).into(binding.ivPreview)
        binding.btnBlur.setOnClickListener { binding.drawOverlay.mode = 1 }
        binding.btnWatermark.setOnClickListener { binding.drawOverlay.mode = 2 }
        binding.btnClearDrawings.setOnClickListener { binding.drawOverlay.blurRect = null; binding.drawOverlay.watermarkPoint = null; binding.drawOverlay.invalidate() }
        binding.btnClose.setOnClickListener { finish() }
        binding.btnSendChannel.setOnClickListener {
            val finalCaption = binding.etCaption.text.toString()
            val relBlur = binding.drawOverlay.getRelativeBlur()
            val relWm = binding.drawOverlay.getRelativeWatermark()
            val prefs = getSharedPreferences("PasifloPrefs", Context.MODE_PRIVATE)
            val savedLogoPath = prefs.getString("logo_path", null)
            Toast.makeText(this, "מעבד...", Toast.LENGTH_SHORT).show()
            Thread {
                val ext = if (videoPath.contains(".mp4")) ".mp4" else ".jpg"
                val outPath = "${externalCacheDir?.absolutePath}/proc_${System.currentTimeMillis()}$ext"
                val cb = { success: Boolean -> runOnUiThread { if (success) { sendToTelegram(outPath, finalCaption); finish() } else Toast.makeText(this, "שגיאה בעיבוד!", Toast.LENGTH_LONG).show() } }
                if (ext == ".mp4") VideoProcessor.processVideo(videoPath, outPath, relBlur, relWm, savedLogoPath, cb) else VideoProcessor.processImage(videoPath, outPath, relBlur, relWm, savedLogoPath, cb)
            }.start()
        }
    }
    private fun sendToTelegram(path: String, caption: String) {
        val prefs = getSharedPreferences("PasifloPrefs", Context.MODE_PRIVATE)
        val channel = prefs.getString("target_channel", "") ?: ""
        TdRepository.sendVideoByUsername(channel, path, caption) { _, _ -> }
    }
}
