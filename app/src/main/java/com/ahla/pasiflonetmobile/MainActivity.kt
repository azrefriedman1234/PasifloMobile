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
import android.graphics.RectF
import java.io.File

class MainActivity : AppCompatActivity() {

    private var selectedUri: Uri? = null
    private var selectedPath: String? = null
    
    // משתנים לממשק
    private lateinit var tvStatus: TextView
    private lateinit var etCaption: EditText
    private lateinit var etChannel: EditText
    private lateinit var cbWithMedia: CheckBox
    private lateinit var imgPreview: ImageView

    // בחירת קובץ
    private val pickMedia = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            selectedUri = uri
            selectedPath = FileUtils.getPathFromUri(this, uri)
            findViewById<TextView>(R.id.tvFilePath).text = selectedPath ?: "Error loading file"
            
            // הצגת תמונה ממוזערת אם זה תמונה או וידאו
            imgPreview.setImageURI(uri) 
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        
        // אתחול TDLib
        TdRepository.init(this)

        // קישור משתנים לממשק
        tvStatus = findViewById(R.id.tvStatus)
        etCaption = findViewById(R.id.etCaption)
        etChannel = findViewById(R.id.etChannel)
        cbWithMedia = findViewById(R.id.cbWithMedia) // הצ'קבוקס החדש
        imgPreview = findViewById(R.id.imgPreview)

        // כפתור בחירת קובץ
        findViewById<Button>(R.id.btnPickFile).setOnClickListener {
            pickMedia.launch("*/*") // נותן לבחור הכל, נסנן אחר כך
        }

        // כפתור הגדרות
        findViewById<Button>(R.id.btnSettings).setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }
        
        // עדכון סטטוס חיבור
        TdRepository.onStatusChanged = { isConnected ->
            runOnUiThread {
                tvStatus.text = if (isConnected) "מחובר ✅" else "ממתין..."
                tvStatus.setTextColor(if (isConnected) 0xFF00FF00.toInt() else 0xFFFF0000.toInt())
            }
        }

        // כפתור שליחה - כאן הלוגיקה החדשה
        findViewById<Button>(R.id.btnSend).setOnClickListener {
            val caption = etCaption.text.toString()
            val channel = etChannel.text.toString()
            
            // בדיקה 1: האם רוצים לשלוח עם מדיה?
            if (cbWithMedia.isChecked) {
                // המשתמש רוצה מדיה
                if (selectedPath == null) {
                    Toast.makeText(this, "נא לבחור קובץ או להסיר את ה-V ממדיה", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }

                Toast.makeText(this, "מעבד מדיה...", Toast.LENGTH_SHORT).show()
                
                // קבלת נתיב הלוגו מההגדרות
                val prefs = getSharedPreferences("PasifloPrefs", MODE_PRIVATE)
                val logoPath = prefs.getString("logo_path", null)
                
                // קובץ יעד זמני
                val outFile = File(cacheDir, "processed_" + System.currentTimeMillis() + ".mp4").absolutePath
                
                // עיבוד (טשטוש/לוגו)
                VideoProcessor.processVideo(selectedPath!!, outFile, null, PointF(0.1f, 0.1f), logoPath) { success ->
                    if (success) {
                        runOnUiThread { Toast.makeText(this, "שולח לטלגרם...", Toast.LENGTH_SHORT).show() }
                        TdRepository.sendVideoByUsername(channel, outFile, caption) { sent, err ->
                            runOnUiThread {
                                if (sent) Toast.makeText(this, "נשלח בהצלחה!", Toast.LENGTH_LONG).show()
                                else Toast.makeText(this, "שגיאה: $err", Toast.LENGTH_LONG).show()
                            }
                        }
                    } else {
                        runOnUiThread { Toast.makeText(this, "שגיאה בעיבוד הוידאו", Toast.LENGTH_SHORT).show() }
                    }
                }
            } else {
                // המשתמש לא רוצה מדיה - שולחים רק טקסט
                if (caption.isEmpty()) {
                    Toast.makeText(this, "נא לכתוב טקסט", Toast.LENGTH_SHORT).show()
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
