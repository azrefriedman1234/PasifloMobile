package com.ahla.pasiflonetmobile.td

import android.content.Context
import android.util.Log
import org.drinkless.tdlib.Client
import org.drinkless.tdlib.TdApi
import java.io.File
import java.lang.reflect.Constructor

object TdRepository {
    private var client: Client? = null
    var onNewMessage: ((TelegramMsg) -> Unit)? = null
    var onStatusChanged: ((Boolean) -> Unit)? = null
    val downloadedFiles = mutableMapOf<Int, String>()

    private val resultHandler = Client.ResultHandler { update ->
        if (update is TdApi.UpdateAuthorizationState) {
            when (update.authorizationState) {
                is TdApi.AuthorizationStateWaitTdlibParameters -> {
                    try {
                        // שימוש ב-Reflection כדי ליצור את האובייקט בלי שגיאות קומפילציה
                        // מחפשים בנאי עם 14 פרמטרים (כפי שראינו בלוגים)
                        val clazz = TdApi.SetTdlibParameters::class.java
                        val ctor = clazz.constructors.find { it.parameterCount == 14 }
                        
                        val encryptionKey: ByteArray? = null
                        val params = ctor?.newInstance(
                            false,          // use_test_dc
                            filesPath,      // database_directory
                            filesPath,      // files_directory
                            encryptionKey,  // encryption_key
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
                        ) as TdApi.SetTdlibParameters
                        
                        client?.send(params) { }
                    } catch (e: Exception) {
                        Log.e("TdRepository", "Reflection failed for SetTdlibParameters", e)
                    }
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
                try {
                    // הכנת משתנים
                    val thumb: TdApi.InputThumbnail? = null
                    val stickers: IntArray? = null
                    val weirdInputFile: TdApi.InputFile? = null
                    val selfDestruct: TdApi.MessageSelfDestructType? = null
                    
                    val content: TdApi.InputMessageContent
                    
                    if (path.endsWith(".mp4")) {
                        // שימוש ב-Reflection לוידאו (13 פרמטרים)
                        val vClass = TdApi.InputMessageVideo::class.java
                        val vCtor = vClass.constructors.find { it.parameterCount == 13 }
                        
                        content = vCtor?.newInstance(
                            TdApi.InputFileLocal(path), // p0
                            thumb,                      // p1
                            weirdInputFile,             // p2 (InputFile!)
                            0,                          // p3 (duration)
                            stickers,                   // p4 (IntArray!)
                            0,                          // p5 (width)
                            0,                          // p6 (height)
                            0,                          // p7 (ttl)
                            false,                      // p8 (streaming)
                            TdApi.FormattedText(caption, null), // p9
                            false,                      // p10
                            selfDestruct,               // p11
                            false                       // p12
                        ) as TdApi.InputMessageVideo
                    } else {
                        // שימוש ב-Reflection לתמונה (9 פרמטרים)
                        val pClass = TdApi.InputMessagePhoto::class.java
                        val pCtor = pClass.constructors.find { it.parameterCount == 9 }
                        
                        content = pCtor?.newInstance(
                            TdApi.InputFileLocal(path), // p0
                            thumb,                      // p1
                            stickers,                   // p2
                            0,                          // p3
                            0,                          // p4
                            TdApi.FormattedText(caption, null), // p5
                            false,                      // p6
                            selfDestruct,               // p7
                            false                       // p8
                        ) as TdApi.InputMessagePhoto
                    }

                    // שימוש ב-Reflection להודעה (למקרה שהפרמטרים לא תואמים)
                    // בדרך כלל SendMessage פשוט יותר, אבל ליתר ביטחון נשתמש בבנאי היחיד שיש לו
                    val sClass = TdApi.SendMessage::class.java
                    val sCtor = sClass.constructors[0] // לוקחים את הראשון (בדרך כלל היחיד)
                    
                    // ננסה להתאים פרמטרים לפי הכמות
                    val msgReq = if (sCtor.parameterCount == 6) {
                         sCtor.newInstance(
                            chatRes.id, 
                            0L, 
                            null, 
                            null, 
                            null, 
                            content
                        ) as TdApi.SendMessage
                    } else {
                        // Fallback למקרה שיש 5 פרמטרים (גרסאות ישנות)
                         sCtor.newInstance(
                            chatRes.id, 
                            0L, 
                            null, 
                            null, 
                            content
                        ) as TdApi.SendMessage
                    }

                    client?.send(msgReq) { sent -> 
                        callback(sent !is TdApi.Error, null) 
                    }

                } catch (e: Exception) {
                    Log.e("TdRepository", "Reflection error in sendVideo", e)
                    callback(false, "Internal Error: " + e.message)
                }
            } else { 
                callback(false, "Chat not found") 
            }
        }
    }
}
