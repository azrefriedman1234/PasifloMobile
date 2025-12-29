package com.ahla.pasiflonetmobile.video

import android.graphics.*
import android.util.Log
import com.arthenica.ffmpegkit.FFmpegKit
import com.arthenica.ffmpegkit.FFprobeKit
import java.io.File
import java.io.FileOutputStream

object VideoProcessor {
    fun processVideo(input: String, output: String, relBlur: RectF?, relWm: PointF?, logoPath: String?, callback: (Boolean) -> Unit) {
        Thread {
            try {
                if (!File(input).exists()) { callback(false); return@Thread }
                
                // בדיקת מידע על הוידאו עם FFprobeKit החדש
                val mediaInfo = FFprobeKit.getMediaInformation(input)
                val streams = mediaInfo.getMediaInformation().getStreams()
                var vW = 1280; var vH = 720
                
                for (s in streams) { 
                    if (s.getType() == "video") { 
                        vW = s.getWidth().toInt()
                        vH = s.getHeight().toInt()
                        break 
                    } 
                }

                val filters = mutableListOf<String>()
                relBlur?.let {
                    val x = (it.left * vW).toInt(); val y = (it.top * vH).toInt()
                    val w = ((it.right - it.left) * vW).toInt(); val h = ((it.bottom - it.top) * vH).toInt()
                    filters.add("delogo=x=$x:y=$y:w=$w:h=$h")
                }

                var cmd = ""
                if (!logoPath.isNullOrEmpty() && relWm != null && File(logoPath).exists()) {
                    val lx = (relWm.x * vW).toInt(); val ly = (relWm.y * vH).toInt()
                    val fStr = if (filters.isNotEmpty()) filters.joinToString(",") + "," else ""
                    val lw = (vW * 0.15).toInt()
                    cmd = "-i \"$input\" -i \"$logoPath\" -filter_complex \"[1:v]scale=$lw:-1[img];[0:v]${fStr}[bg];[bg][img]overlay=$lx:$ly\" -c:v libx264 -preset ultrafast -c:a copy -y \"$output\""
                } else {
                    val fCmd = if (filters.isNotEmpty()) "-vf \"" + filters.joinToString(",") + "\"" else ""
                    cmd = "-i \"$input\" $fCmd -c:v libx264 -preset ultrafast -c:a copy -y \"$output\""
                }
                
                // הרצת הפקודה עם FFmpegKit החדש
                val session = FFmpegKit.execute(cmd)
                callback(session.getReturnCode().isValueSuccess())

            } catch (e: Exception) { callback(false) }
        }.start()
    }

    fun processImage(input: String, output: String, relBlur: RectF?, relWm: PointF?, logoPath: String?, callback: (Boolean) -> Unit) {
        try {
            val opts = BitmapFactory.Options().apply { inMutable = true }
            val bmp = BitmapFactory.decodeFile(input, opts)
            val c = Canvas(bmp)
            val w = bmp.width.toFloat(); val h = bmp.height.toFloat()

            relBlur?.let {
                val r = RectF(it.left * w, it.top * h, it.right * w, it.bottom * h)
                c.drawRect(r, Paint().apply { color = Color.BLACK; alpha = 200; maskFilter = BlurMaskFilter(50f, BlurMaskFilter.Blur.NORMAL) })
            }
            if (!logoPath.isNullOrEmpty() && relWm != null) {
                val lb = BitmapFactory.decodeFile(logoPath)
                val lw = (w * 0.15f).toInt()
                val lh = (lw * lb.height) / lb.width
                c.drawBitmap(Bitmap.createScaledBitmap(lb, lw, lh, true), relWm.x * w, relWm.y * h, null)
            }
            val out = FileOutputStream(output)
            bmp.compress(Bitmap.CompressFormat.JPEG, 90, out)
            out.close()
            callback(true)
        } catch (e: Exception) { callback(false) }
    }
}
