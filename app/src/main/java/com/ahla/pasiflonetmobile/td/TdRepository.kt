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

    // טיפול בתשובות מטלגרם
    private val resultHandler = Client.ResultHandler { update ->
        if (update is TdApi.UpdateAuthorizationState) {
            when (update.authorizationState) {
                is TdApi.AuthorizationStateWaitTdlibParameters -> {
                    // תיקון: שימוש בפונקציה החדשה שמקבלת את כל הפרמטרים ישירות
                    // סדר הפרמטרים: useTestDc, databaseDir, filesDir, encryptionKey, useFileDb, useChatDb, useMsgDb, useSecretChats, apiId, apiHash, lang, model, sysVer, appVer, enableStorageOptimizer, ignoreFileNames
                    val request = TdApi.SetTdlibParameters(
                        false,                  // use_test_dc
                        filesPath,              // database_directory
                        filesPath,              // files_directory
                        null,                   // database_encryption_key
                        true,                   // use_file_database
                        true,                   // use_chat_info_database
                        true,                   // use_message_database
                        true,                   // use_secret_chats
                        currentApiId,           // api_id
                        currentApiHash,         // api_hash
                        "en",                   // system_language_code
                        "Mobile",               // device_model
                        "Android",              // system_version
                        "1.0",                  // application_version
                        true,                   // enable_storage_optimizer
                        true                    // ignore_file_names
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

        // יצירת הקליינט
        client = Client.create(resultHandler, null, null)
        
        // הגדרת לוגים
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
        // בגרסה החדשה GetChats מקבל רק רשימה וכמות
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
                // יצירת תוכן ההודעה בהתאם לגרסה החדשה (פרמטרים רבים)
                val content: TdApi.InputMessageContent = if (path.endsWith(".mp4")) {
                    TdApi.InputMessageVideo(
                        TdApi.InputFileLocal(path), // video
                        null,                       // thumbnail
                        null,                       // added_sticker_file_ids
                        0,                          // duration
                        0,                          // width
                        0,                          // height
                        false,                      // supports_streaming
                        TdApi.FormattedText(caption, null), // caption
                        false,                      // show_caption_above_media
                        false,                      // is_self_destructing
                        0                           // self_destruct_time
                    )
                } else {
                    TdApi.InputMessagePhoto(
                        TdApi.InputFileLocal(path), // photo
                        null,                       // thumbnail
                        null,                       // added_sticker_file_ids
                        0,                          // width
                        0,                          // height
                        TdApi.FormattedText(caption, null), // caption
                        false,                      // show_caption_above_media
                        0                           // self_destruct_time
                    )
                }

                // שליחת ההודעה - שימוש ב-null עבור reply_to
                client?.send(TdApi.SendMessage(
                    chatRes.id, 
                    0,      // message_thread_id
                    null,   // reply_to (חשוב: null במקום 0)
                    null,   // options
                    null,   // reply_markup
                    content // input_message_content
                )) { sent -> 
                    callback(sent !is TdApi.Error, null) 
                }
            } else { 
                callback(false, "Chat not found") 
            }
        }
    }
}
