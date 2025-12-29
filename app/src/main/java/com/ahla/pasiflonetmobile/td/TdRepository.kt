package com.ahla.pasiflonetmobile.td

import android.content.Context
import org.drinkless.tdlib.Client
import org.drinkless.tdlib.TdApi
import java.io.File

object TdRepository {
    private var client: Client? = null
    var onNewMessage: ((TelegramMsg) -> Unit)? = null
    var onStatusChanged: ((Boolean) -> Unit)? = null
    val downloadedFiles = mutableMapOf<Int, String>()

    private val resultHandler = Client.ResultHandler { update ->
        if (update is TdApi.UpdateAuthorizationState) {
            when (update.authorizationState) {
                is TdApi.AuthorizationStateWaitTdlibParameters -> {
                    // תיקון: שימוש בבנאי עם 14 פרמטרים בדיוק (לפי לוג שגיאה קודם)
                    val request = TdApi.SetTdlibParameters(
                        false,          // p0: use_test_dc
                        filesPath,      // p1: database_directory
                        filesPath,      // p2: files_directory
                        null,           // p3: encryption_key (ByteArray)
                        true,           // p4: use_file_database
                        true,           // p5: use_chat_info_database
                        true,           // p6: use_message_database
                        true,           // p7: use_secret_chats
                        currentApiId,   // p8: api_id
                        currentApiHash, // p9: api_hash
                        "en",           // p10: system_language_code
                        "Mobile",       // p11: device_model
                        "Android",      // p12: system_version
                        "1.0"           // p13: application_version
                    )
                    client?.send(request) { }
                }
                is TdApi.AuthorizationStateWaitPhoneNumber -> onStatusChanged?.invoke(false)
                is TdApi.AuthorizationStateReady -> { 
                    onStatusChanged?.invoke(true)
                    loadChats() 
                }
            }
        } else if (update is TdApi.UpdateNewMessage) {
            handleNewMessage(update.message)
        } else if (update is TdApi.UpdateFile) {
            if (update.file.local.isDownloadingCompleted) {
                downloadedFiles[update.file.id] = update.file.local.path
            }
        }
    }

    private var currentApiId: Int = 0
    private var currentApiHash: String = ""
    private var filesPath: String = ""

    fun init(context: Context) {
        if (client != null) return
        val prefs = context.getSharedPreferences("PasifloPrefs", Context.MODE_PRIVATE)
        val apiIdStr = prefs.getString("api_id", "")
        val apiHash = prefs.getString("api_hash", "")
        if (apiIdStr.isNullOrEmpty() || apiHash.isNullOrEmpty()) return

        currentApiId = apiIdStr.toIntOrNull() ?: return
        currentApiHash = apiHash!!

        val dir = File(context.filesDir, "tdlib")
        if (!dir.exists()) dir.mkdirs()
        filesPath = dir.absolutePath

        client = Client.create(resultHandler, null, null)
        client?.send(TdApi.SetLogVerbosityLevel(1)) {}
    }

    private fun handleNewMessage(msg: TdApi.Message) {
        val content = msg.content
        var text = ""
        var fileId = "0"
        var thumbPath: String? = null
        
        if (content is TdApi.MessageText) { 
            text = content.text.text 
        } else if (content is TdApi.MessageVideo) {
            text = content.caption.text
            fileId = content.video.video.id.toString()
            downloadFile(content.video.video.id)
            thumbPath = content.video.thumbnail?.file?.local?.path
        } else if (content is TdApi.MessagePhoto) {
            text = content.caption.text
            val size = content.photo.sizes.lastOrNull()
            if (size != null) { 
                fileId = size.photo.id.toString()
                downloadFile(size.photo.id)
                thumbPath = size.photo.local.path 
            }
        }
        
        onNewMessage?.invoke(TelegramMsg(
            msg.id, 
            text, 
            java.text.SimpleDateFormat("HH:mm").format(java.util.Date(msg.date.toLong() * 1000)), 
            if (content is TdApi.MessageText) "text" else "media", 
            fileId, 
            null, 
            thumbPath
        ))
    }

    private fun loadChats() { 
        client?.send(TdApi.GetChats(TdApi.ChatListMain(), 20)) { } 
    }
    
    private fun downloadFile(fileId: Int) { 
        client?.send(TdApi.DownloadFile(fileId, 32, 0, 0, true)) { } 
    }

    fun sendCode(phone: String, cb: (Boolean, String?) -> Unit) { 
        client?.send(TdApi.SetAuthenticationPhoneNumber(phone, null)) { r -> 
            if (r is TdApi.Error) cb(false, r.message) else cb(true, null) 
        } 
    }
    
    fun verifyCode(code: String, cb: (Boolean, String?) -> Unit) { 
        client?.send(TdApi.CheckAuthenticationCode(code)) { r -> 
            if (r is TdApi.Error) cb(false, r.message) else cb(true, null) 
        } 
    }
    
    fun verifyPassword(pass: String, cb: (Boolean, String?) -> Unit) { 
        client?.send(TdApi.CheckAuthenticationPassword(pass)) { r -> 
            if (r is TdApi.Error) cb(false, r.message) else cb(true, null) 
        } 
    }
    
    fun sendVideoByUsername(username: String, path: String, caption: String, callback: (Boolean, String?) -> Unit) {
        client?.send(TdApi.SearchPublicChat(username)) { chatRes ->
            if (chatRes is TdApi.Chat) {
                
                val content: TdApi.InputMessageContent = if (path.endsWith(".mp4")) {
                    // וידאו: 13 פרמטרים בדיוק
                    TdApi.InputMessageVideo(
                        TdApi.InputFileLocal(path), // p0: file
                        null,                       // p1: thumbnail
                        null,                       // p2: added_sticker_file_ids
                        0,                          // p3: duration
                        null,                       // p4: width (IntArray? or Int) -> Log said IntArray!
                        0,                          // p5: width
                        0,                          // p6: height
                        0,                          // p7: ttl
                        false,                      // p8: supports_streaming
                        TdApi.FormattedText(caption, null), // p9: caption
                        false,                      // p10: show_caption_above
                        null,                       // p11: self_destruct
                        false                       // p12: spoiler
                    )
                } else {
                    // תמונה: 9 פרמטרים בדיוק
                    TdApi.InputMessagePhoto(
                        TdApi.InputFileLocal(path), // p0: file
                        null,                       // p1: thumbnail
                        null,                       // p2: added_sticker_file_ids
                        0,                          // p3: width
                        0,                          // p4: height
                        TdApi.FormattedText(caption, null), // p5: caption
                        false,                      // p6: show_caption_above
                        null,                       // p7: self_destruct
                        false                       // p8: spoiler
                    )
                }

                client?.send(TdApi.SendMessage(
                    chatRes.id, 
                    0L,      // message_thread_id (Long!)
                    null,    // reply_to
                    null,    // options
                    null,    // reply_markup
                    content
                )) { sent -> 
                    callback(sent !is TdApi.Error, null) 
                }
            } else { 
                callback(false, "Chat not found") 
            }
        }
    }
}
