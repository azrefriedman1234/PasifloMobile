package com.ahla.pasiflonetmobile.td
data class TelegramMsg(val id: Long, val text: String, val date: String, val type: String, val fileId: String, val miniThumb: ByteArray? = null, val thumbPath: String? = null)
