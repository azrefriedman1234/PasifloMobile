package com.ahla.pasiflonetmobile

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.ahla.pasiflonetmobile.td.TdRepository
import com.ahla.pasiflonetmobile.video.VideoProcessor
import android.graphics.PointF
import java.io.File

class MainActivity : AppCompatActivity() {

    private var selectedUri: Uri? = null
    private var selectedPath: String? = null
    
    private lateinit var tvStatus: TextView
    private lateinit var etCaption: EditText
    private lateinit var etChannel: EditText
    private lateinit var cbWithMedia: CheckBox
    private lateinit var imgPreview: ImageView

    private val pickMedia = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            selectedUri = uri
            selectedPath = FileUtils.getPathFromUri(this, uri)
            findViewById<TextView>(R.id.tvFilePath).text = selectedPath ?: "Error"
            imgPreview.setImageURI(uri) 
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        
        TdRepository.init(this)

        tvStatus = findViewById(R.id.tvStatus)
        etCaption = findViewById(R.id.etCaption)
        etChannel = findViewById(R.id.etChannel)
        cbWithMedia = findViewById(R.id.cbWithMedia)
        imgPreview = findViewById(R.id.imgPreview)

        findViewById<Button>(R.id.btnPickFile).setOnClickListener {
            pickMedia.launch("*/*")
        }

        findViewById<Button>(R.id.btnSettings).setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }
        
        TdRepository.onStatusChanged = { isConnected ->
            runOnUiThread {
                tvStatus.text = if (isConnected) "מחובר ✅" else "ממתין..."
                tvStatus.setTextColor(if (isConnected) 0xFF00FF00.toInt() else 0xFFFF0000.toInt())
            }
        }

        findViewById<Button>(R.id.btnSend).setOnClickListener {
            val caption = etCaption.text.toString()
            val channel = etChannel.text.toString()
            
            if (cbWithMedia.isChecked) {
                if (selectedPath == null) {
                    Toast.makeText(this, "בחר קובץ או בטל את הסימון", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }

                Toast.makeText(this, "מעבד...", Toast.LENGTH_SHORT).show()
                val prefs = getSharedPreferences("PasifloPrefs", MODE_PRIVATE)
                val logoPath = prefs.getString("logo_path", null)
                val outFile = File(cacheDir, "out_${System.currentTimeMillis()}.mp4").absolutePath
                
                VideoProcessor.processVideo(selectedPath!!, outFile, null, PointF(0.1f, 0.1f), logoPath) { success ->
                    if (success) {
                        runOnUiThread { Toast.makeText(this, "שולח...", Toast.LENGTH_SHORT).show() }
                        TdRepository.sendVideoByUsername(channel, outFile, caption) { sent, err ->
                            runOnUiThread {
                                if (sent) Toast.makeText(this, "נשלח!", Toast.LENGTH_LONG).show()
                                else Toast.makeText(this, "שגיאה: $err", Toast.LENGTH_LONG).show()
                            }
                        }
                    } else {
                        runOnUiThread { Toast.makeText(this, "שגיאת עיבוד", Toast.LENGTH_SHORT).show() }
                    }
                }
            } else {
                if (caption.isEmpty()) {
                    Toast.makeText(this, "כתוב משהו", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }
                
                TdRepository.sendTextByUsername(channel, caption) { sent, err ->
                    runOnUiThread {
                        if (sent) Toast.makeText(this, "הודעה נשלחה!", Toast.LENGTH_LONG).show()
                        else Toast.makeText(this, "שגיאה: $err", Toast.LENGTH_LONG).show()
                    }
                }
            }
        }
    }
}
