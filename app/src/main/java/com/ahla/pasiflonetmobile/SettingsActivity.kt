package com.ahla.pasiflonetmobile

import android.content.Context
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity

class SettingsActivity : AppCompatActivity() {

    private lateinit var etApiId: EditText
    private lateinit var etApiHash: EditText
    private lateinit var tvLogoPath: TextView

    // משגר בחירת קבצים
    private val pickLogo = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            // כאן הקסם: המרה מ-Uri לקובץ אמיתי
            val realPath = FileUtils.getPathFromUri(this, uri)
            if (realPath != null) {
                getSharedPreferences("PasifloPrefs", Context.MODE_PRIVATE)
                    .edit()
                    .putString("logo_path", realPath)
                    .apply()
                tvLogoPath.text = "לוגו נבחר: " + realPath.substringAfterLast("/")
            } else {
                Toast.makeText(this, "שגיאה בשמירת הלוגו", Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        etApiId = findViewById(R.id.etApiId)
        etApiHash = findViewById(R.id.etApiHash)
        tvLogoPath = findViewById(R.id.tvLogoPath)
        val btnSave = findViewById<Button>(R.id.btnSave)
        val btnPickLogo = findViewById<Button>(R.id.btnPickLogo)

        val prefs = getSharedPreferences("PasifloPrefs", Context.MODE_PRIVATE)
        etApiId.setText(prefs.getString("api_id", ""))
        etApiHash.setText(prefs.getString("api_hash", ""))
        val currentLogo = prefs.getString("logo_path", "")
        if (!currentLogo.isNullOrEmpty()) {
            tvLogoPath.text = "לוגו קיים: " + currentLogo.substringAfterLast("/")
        }

        btnPickLogo.setOnClickListener {
            pickLogo.launch("image/*")
        }

        btnSave.setOnClickListener {
            prefs.edit()
                .putString("api_id", etApiId.text.toString())
                .putString("api_hash", etApiHash.text.toString())
                .apply()
            Toast.makeText(this, "הגדרות נשמרו", Toast.LENGTH_SHORT).show()
            finish()
        }
    }
}
