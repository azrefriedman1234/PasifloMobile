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
                    // הגדרה מפורשת של מפתח ההצפנה כ-ByteArray (למניעת בלבול)
                    val encryptionKey: ByteArray? = null
                    
                    // בנאי עם 14 פרמטרים בדיוק (לפי דרישת גרסה 1.8.56)
                    val request = TdApi.SetTdlibParameters(
                        false,          // use_test_dc
                        filesPath,      // database_directory
                        filesPath,      // files_directory
                        encryptionKey,  // database_encryption_key
                        true,           // use_file_database
                        true,           // use_chat_info_database
                        true,           // use_message_database
                        true,           // use_secret_chats
                        currentApiId,   // api_id
                        currentApiHash, // api_hash
                        "en",           // system_language_code
                        "Mobile",       // device_model
                        "Android",      // system_version
                        "1.0"           // application_version
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
        // בגרסה 1.8.56 offsetOrder הוא long (מספר ענק)
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
                
                // הכנת המשתנים מראש כדי למנוע בלבול בסוגים (Types)
                val thumb: TdApi.InputThumbnail? = null
                val addedStickerFileIds: IntArray? = null
                val selfDestructType: TdApi.MessageSelfDestructType? = null
                val replyTo: TdApi.InputMessageReplyTo? = null
                val options: TdApi.MessageSendOptions? = null
                val markup: TdApi.ReplyMarkup? = null

                val content: TdApi.InputMessageContent = if (path.endsWith(".mp4")) {
                    TdApi.InputMessageVideo(
                        TdApi.InputFileLocal(path), 
                        thumb,                       
                        addedStickerFileIds, // IntArray?
                        0, // duration
                        0, // width
                        0, // height 
                        false, // supports_streaming
                        TdApi.FormattedText(caption, null), 
                        false, // show_caption_above_media
                        false, // is_self_destructing
                        0      // self_destruct_time
                    )
                } else {
                    TdApi.InputMessagePhoto(
                        TdApi.InputFileLocal(path), 
                        thumb,                       
                        addedStickerFileIds, // IntArray?
                        0, // width                         
                        0, // height                         
                        TdApi.FormattedText(caption, null), 
                        false, // show_caption_above_media
                        0      // self_destruct_time                       
                    )
                }

                // השינוי הקריטי: 0L (לונג) ולא 0 (אינט) עבור message_thread_id
                client?.send(TdApi.SendMessage(
                    chatRes.id, 
                    0L,        // חובה בגרסה 1.8.56!
                    replyTo,    
                    options,    
                    markup,    
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
