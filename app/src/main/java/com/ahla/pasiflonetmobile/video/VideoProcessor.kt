package com.ahla.pasiflonetmobile.video

import android.graphics.*
import android.util.Log
import com.arthenica.mobileffmpeg.FFmpeg
import com.arthenica.mobileffmpeg.Config
import java.io.File
import java.io.FileOutputStream

object VideoProcessor {
    fun processVideo(input: String, output: String, relBlur: RectF?, relWm: PointF?, logoPath: String?, callback: (Boolean) -> Unit) {
        Thread {
            try {
                if (!File(input).exists()) {
                    Log.e("VideoProcessor", "Input file not found")
                    callback(false)
                    return@Thread
                }
                
                // חישוב מימדים פשוט (בלי FFprobe כדי למנוע קריסות תאימות)
                // נניח ברירת מחדל 720p אם לא מצליחים לקרוא
                var vW = 1280
                var vH = 720
                
                val filters = mutableListOf<String>()
                relBlur?.let {
                    val x = (it.left * vW).toInt()
                    val y = (it.top * vH).toInt()
                    val w = ((it.right - it.left) * vW).toInt()
                    val h = ((it.bottom - it.top) * vH).toInt()
                    filters.add("delogo=x=$x:y=$y:w=$w:h=$h")
                }

                var cmd = ""
                if (!logoPath.isNullOrEmpty() && relWm != null && File(logoPath).exists()) {
                    val lx = (relWm.x * vW).toInt()
                    val ly = (relWm.y * vH).toInt()
                    val fStr = if (filters.isNotEmpty()) filters.joinToString(",") + "," else ""
                    val lw = (vW * 0.15).toInt()
                    cmd = "-i \"$input\" -i \"$logoPath\" -filter_complex \"[1:v]scale=$lw:-1[img];[0:v]${fStr}[bg];[bg][img]overlay=$lx:$ly\" -c:v libx264 -preset ultrafast -c:a copy -y \"$output\""
                } else {
                    val fCmd = if (filters.isNotEmpty()) "-vf \"" + filters.joinToString(",") + "\"" else ""
                    cmd = "-i \"$input\" $fCmd -c:v libx264 -preset ultrafast -c:a copy -y \"$output\""
                }
                
                Log.d("VideoProcessor", "Running cmd: $cmd")
                // הרצה בטוחה
                val rc = FFmpeg.execute(cmd)
                
                if (rc == Config.RETURN_CODE_SUCCESS) {
                     callback(true)
                } else {
                     Log.e("VideoProcessor", "FFmpeg failed with rc=$rc")
                     callback(false)
                }

            } catch (e: Exception) {
                Log.e("VideoProcessor", "Exception", e)
                callback(false)
            }
        }.start()
    }

    fun processImage(input: String, output: String, relBlur: RectF?, relWm: PointF?, logoPath: String?, callback: (Boolean) -> Unit) {
        try {
            val opts = BitmapFactory.Options().apply { inMutable = true }
            val bmp = BitmapFactory.decodeFile(input, opts)
            if (bmp == null) { callback(false); return }
            
            val c = Canvas(bmp)
            val w = bmp.width.toFloat()
            val h = bmp.height.toFloat()

            relBlur?.let {
                val r = RectF(it.left * w, it.top * h, it.right * w, it.bottom * h)
                val paint = Paint().apply { 
                    color = Color.BLACK 
                    alpha = 200 
                }
                c.drawRect(r, paint)
            }
            if (!logoPath.isNullOrEmpty() && relWm != null) {
                val lb = BitmapFactory.decodeFile(logoPath)
                if (lb != null) {
                    val lw = (w * 0.15f).toInt()
                    val lh = (lw * lb.height) / lb.width
                    c.drawBitmap(Bitmap.createScaledBitmap(lb, lw, lh, true), relWm.x * w, relWm.y * h, null)
                }
            }
            val out = FileOutputStream(output)
            bmp.compress(Bitmap.CompressFormat.JPEG, 90, out)
            out.close()
            callback(true)
        } catch (e: Exception) { callback(false) }
    }
}
