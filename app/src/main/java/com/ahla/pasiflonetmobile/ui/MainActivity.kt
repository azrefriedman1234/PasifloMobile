package com.ahla.pasiflonetmobile.ui
import android.Manifest
import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.text.InputType
import android.view.View
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import com.ahla.pasiflonetmobile.R
import com.ahla.pasiflonetmobile.databinding.ActivityMainBinding
import com.ahla.pasiflonetmobile.td.TdRepository
import com.ahla.pasiflonetmobile.td.TelegramMsg
import kotlin.system.exitProcess
class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding
    private val messages = mutableListOf<TelegramMsg>()
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        checkPermissions()
        val adapter = MessageAdapter { msg ->
            val intent = Intent(this, DetailsActivity::class.java).apply {
                val localPath = TdRepository.downloadedFiles[msg.fileId.toIntOrNull() ?: -1]
                putExtra("VIDEO_LOCAL_PATH", localPath ?: msg.thumbPath)
                putExtra("MSG_TEXT", msg.text)
            }
            startActivity(intent)
        }
        binding.rvMessages.layoutManager = LinearLayoutManager(this)
        binding.rvMessages.adapter = adapter
        binding.btnExit.setOnClickListener { finishAffinity(); exitProcess(0) }
        binding.btnSettings.setOnClickListener { startActivity(Intent(this, SettingsActivity::class.java)) }
        binding.btnLogin.setOnClickListener { showLoginDialog() }
        TdRepository.onStatusChanged = { isConnected -> runOnUiThread { binding.tvStatusText.text = if (isConnected) getString(R.string.status_connected) else getString(R.string.status_waiting) } }
        TdRepository.onNewMessage = { msg -> runOnUiThread { messages.add(0, msg); adapter.submitList(messages.toList()) } }
        val prefs = getSharedPreferences("PasifloPrefs", Context.MODE_PRIVATE)
        if (prefs.getString("api_id", "").isNullOrEmpty()) { binding.tvStatusText.text = getString(R.string.status_missing_api); binding.tvStatusText.setTextColor(Color.RED) }
        else TdRepository.init(this)
    }
    private fun checkPermissions() {
        val p = if (Build.VERSION.SDK_INT >= 33) arrayOf(Manifest.permission.READ_MEDIA_IMAGES, Manifest.permission.READ_MEDIA_VIDEO) else arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
        if (p.any { ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED }) ActivityCompat.requestPermissions(this, p, 101)
    }
    private fun showLoginDialog() {
        val builder = AlertDialog.Builder(this)
        builder.setTitle(getString(R.string.login_title))
        val layout = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(50, 40, 50, 10) }
        val ip = EditText(this).apply { hint = getString(R.string.phone_hint); inputType = InputType.TYPE_CLASS_PHONE }
        val ic = EditText(this).apply { hint = getString(R.string.code_hint); visibility = View.GONE; inputType = InputType.TYPE_CLASS_NUMBER }
        val ips = EditText(this).apply { hint = getString(R.string.password_hint); visibility = View.GONE; inputType = 129 }
        layout.addView(ip); layout.addView(ic); layout.addView(ips)
        builder.setView(layout); builder.setPositiveButton(getString(R.string.send_btn), null)
        val d = builder.create(); d.show()
        d.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
            when {
                ip.visibility == View.VISIBLE -> TdRepository.sendCode(ip.text.toString()) { s, e -> runOnUiThread { if (s) { ip.visibility = View.GONE; ic.visibility = View.VISIBLE } else Toast.makeText(this, e, 1).show() } }
                ic.visibility == View.VISIBLE -> TdRepository.verifyCode(ic.text.toString()) { s, e -> runOnUiThread { if (s) d.dismiss() else if (e?.contains("PASSWORD")==true) { ic.visibility=View.GONE; ips.visibility=View.VISIBLE } } }
                ips.visibility == View.VISIBLE -> TdRepository.verifyPassword(ips.text.toString()) { s, e -> runOnUiThread { if (s) d.dismiss() else Toast.makeText(this, e, 1).show() } }
            }
        }
    }
}
