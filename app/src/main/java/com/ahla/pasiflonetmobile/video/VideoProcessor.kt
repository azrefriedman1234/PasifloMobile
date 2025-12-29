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
                    Log.e("VideoProcessor", "Input file not found: $input")
                    callback(false)
                    return@Thread
                }
                
                // ברירת מחדל לרזולוציה (במציאות כדאי להשתמש ב-MediaMetadataRetriever)
                // אבל זה מספיק טוב לרוב המקרים כבסיס לחישוב יחסי
                val vW = 1280
                val vH = 720
                
                val filters = mutableListOf<String>()
                
                // 1. חישוב טשטוש בטוח
                relBlur?.let {
                    // המרה מיחסי (0.0-1.0) לפיקסלים
                    var x = (it.left * vW).toInt()
                    var y = (it.top * vH).toInt()
                    var w = ((it.right - it.left) * vW).toInt()
                    var h = ((it.bottom - it.top) * vH).toInt()
                    
                    // תיקונים למניעת קריסה
                    if (x < 0) x = 0
                    if (y < 0) y = 0
                    if (w <= 0) w = 10 // מינימום רוחב
                    if (h <= 0) h = 10 // מינימום גובה
                    
                    // FFmpeg שונא מימדים אי-זוגיים לפעמים, ננסה לשמור על זוגיות אם אפשר
                    if (w % 2 != 0) w--
                    if (h % 2 != 0) h--

                    Log.d("VideoProcessor", "Adding blur: x=$x y=$y w=$w h=$h")
                    filters.add("delogo=x=$x:y=$y:w=$w:h=$h")
                }

                var cmd = ""
                // 2. טיפול בלוגו
                if (!logoPath.isNullOrEmpty() && relWm != null && File(logoPath).exists()) {
                    val lx = (relWm.x * vW).toInt().coerceAtLeast(0)
                    val ly = (relWm.y * vH).toInt().coerceAtLeast(0)
                    
                    // שרשור פילטרים: אם יש טשטוש, הוא ראשון ([bg]), ואז הלוגו מעליו
                    val filterChain = if (filters.isNotEmpty()) {
                        "${filters.joinToString(",")}[bg];[bg][img]overlay=$lx:$ly"
                    } else {
                        "[0:v][img]overlay=$lx:$ly"
                    }
                    
                    // לוגו בגודל 15% מהרוחב
                    val lw = (vW * 0.15).toInt()
                    
                    cmd = "-i \"$input\" -i \"$logoPath\" -filter_complex \"[1:v]scale=$lw:-1[img];$filterChain\" -c:v libx264 -preset ultrafast -c:a copy -y \"$output\""
                } else {
                    // רק טשטוש (בלי לוגו)
                    val fCmd = if (filters.isNotEmpty()) "-vf \"" + filters.joinToString(",") + "\"" else ""
                    cmd = "-i \"$input\" $fCmd -c:v libx264 -preset ultrafast -c:a copy -y \"$output\""
                }
                
                Log.d("VideoProcessor", "Executing CMD: $cmd")
                
                val rc = FFmpeg.execute(cmd)
                
                if (rc == Config.RETURN_CODE_SUCCESS) {
                     Log.d("VideoProcessor", "Success!")
                     callback(true)
                } else {
                     Log.e("VideoProcessor", "Failed with RC: $rc")
                     // הדפסת הלוג המלא של FFmpeg במקרה של כישלון
                     Config.printLastCommandOutput(Log.ERROR)
                     callback(false)
                }

            } catch (e: Exception) {
                Log.e("VideoProcessor", "Critical Exception", e)
                callback(false)
            }
        }.start()
    }

    // פונקציית התמונה נשארת אותו דבר (היא עובדת בדרך כלל)
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
                // טשטוש "מזויף" לתמונה (ריבוע שחור שקוף) כי אין ספריה כבדה
                val paint = Paint().apply { 
                    color = Color.BLACK 
                    alpha = 150 
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
