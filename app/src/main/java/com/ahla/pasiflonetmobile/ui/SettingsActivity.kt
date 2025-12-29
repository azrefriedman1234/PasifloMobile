package com.ahla.pasiflonetmobile.ui
import android.content.Context
import android.os.Bundle
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.ahla.pasiflonetmobile.databinding.ActivitySettingsBinding
class SettingsActivity : AppCompatActivity() {
    private lateinit var binding: ActivitySettingsBinding
    private val pickImage = registerForActivityResult(ActivityResultContracts.GetContent()) { uri -> uri?.let { binding.tvLogoPath.text = uri.path } }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)
        val prefs = getSharedPreferences("PasifloPrefs", Context.MODE_PRIVATE)
        binding.etApiId.setText(prefs.getString("api_id", ""))
        binding.etApiHash.setText(prefs.getString("api_hash", ""))
        binding.etChannel.setText(prefs.getString("target_channel", ""))
        binding.tvLogoPath.text = prefs.getString("logo_path", "אין לוגו")
        binding.btnPickLogo.setOnClickListener { Toast.makeText(this, "יש להעתיק נתיב ידנית בינתיים", Toast.LENGTH_SHORT).show() }
        binding.btnSave.setOnClickListener {
            prefs.edit().apply { putString("api_id", binding.etApiId.text.toString()); putString("api_hash", binding.etApiHash.text.toString()); putString("target_channel", binding.etChannel.text.toString()); apply() }
            Toast.makeText(this, "הגדרות נשמרו!", Toast.LENGTH_LONG).show()
            finish()
        }
    }
}
