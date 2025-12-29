package com.ahla.pasiflonetmobile

import android.content.Context
import android.net.Uri
import java.io.File
import java.io.FileOutputStream

object FileUtils {
    // מעתיק קובץ מהגלריה לתיקייה הפרטית של האפליקציה
    fun getPathFromUri(context: Context, uri: Uri): String? {
        return try {
            val contentResolver = context.contentResolver
            // יצירת שם קובץ ייחודי או קבוע ללוגו
            val file = File(context.filesDir, "custom_logo.png")
            
            val inputStream = contentResolver.openInputStream(uri) ?: return null
            val outputStream = FileOutputStream(file)
            
            inputStream.use { input ->
                outputStream.use { output ->
                    input.copyTo(output)
                }
            }
            // מחזיר את הנתיב האמיתי ש-FFmpeg יכול לקרוא
            file.absolutePath
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
}
