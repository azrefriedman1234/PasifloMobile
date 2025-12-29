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
                        val clazz = TdApi.SetTdlibParameters::class.java
                        val ctor = clazz.constructors.find { it.parameterCount == 14 }
                        
                        val encryptionKey: ByteArray? = null
                        val params = ctor?.newInstance(
                            false, filesPath, filesPath, encryptionKey,
                            true, true, true, true,
                            currentApiId, currentApiHash,
                            "en", "Mobile", "Android", "1.0"
                        ) as TdApi.SetTdlibParameters
                        
                        client?.send(params) { }
                    } catch (e: Exception) {
                        Log.e("TdRepository", "Reflection failed", e)
                    }
                }
                is TdApi.AuthorizationStateWaitPhoneNumber -> onStatusChanged?.invoke(false)
                is TdApi.AuthorizationStateReady -> { 
                    onStatusChanged?.invoke(true)
                    loadChats() 
                }
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

    private fun loadChats() { 
        client?.send(TdApi.GetChats(TdApi.ChatListMain(), 20)) { } 
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

    // פונקציה לשליחת טקסט - עכשיו עם Reflection מלא
    fun sendTextByUsername(username: String, text: String, callback: (Boolean, String?) -> Unit) {
        client?.send(TdApi.SearchPublicChat(username)) { chatRes ->
            if (chatRes is TdApi.Chat) {
                try {
                    // יצירת תוכן טקסט באמצעות Reflection כדי למנוע קריסות קומפילציה
                    val tClass = TdApi.InputMessageText::class.java
                    // מחפשים בנאי עם 3 פרמטרים (text, disable_web_preview, clear_draft)
                    val tCtor = tClass.constructors.find { it.parameterCount == 3 }
                    
                    val content = tCtor?.newInstance(
                        TdApi.FormattedText(text, null),
                        false, 
                        true
                    ) as TdApi.InputMessageContent
                    
                    // שליחת ההודעה
                    val sClass = TdApi.SendMessage::class.java
                    val sCtor = sClass.constructors[0]
                    val msgReq = if (sCtor.parameterCount == 6) {
                         sCtor.newInstance(chatRes.id, 0L, null, null, null, content) as TdApi.SendMessage
                    } else {
                         sCtor.newInstance(chatRes.id, 0L, null, null, content) as TdApi.SendMessage
                    }
                    client?.send(msgReq) { sent -> callback(sent !is TdApi.Error, null) }
                } catch(e: Exception) {
                    Log.e("TdRepo", "Text Reflection Error", e)
                    callback(false, e.message)
                }
            } else {
                callback(false, "Chat not found")
            }
        }
    }
    
    fun sendVideoByUsername(username: String, path: String, caption: String, callback: (Boolean, String?) -> Unit) {
        client?.send(TdApi.SearchPublicChat(username)) { chatRes ->
            if (chatRes is TdApi.Chat) {
                try {
                    val thumb: TdApi.InputThumbnail? = null
                    val stickers: IntArray? = null
                    val weirdInputFile: TdApi.InputFile? = null
                    val selfDestruct: TdApi.MessageSelfDestructType? = null
                    
                    val content: TdApi.InputMessageContent
                    
                    if (path.endsWith(".mp4")) {
                        val vClass = TdApi.InputMessageVideo::class.java
                        val vCtor = vClass.constructors.find { it.parameterCount == 13 }
                        
                        content = vCtor?.newInstance(
                            TdApi.InputFileLocal(path), thumb, weirdInputFile, 0, stickers, 0, 0, 0, false,
                            TdApi.FormattedText(caption, null), false, selfDestruct, false
                        ) as TdApi.InputMessageVideo
                    } else {
                        val pClass = TdApi.InputMessagePhoto::class.java
                        val pCtor = pClass.constructors.find { it.parameterCount == 9 }
                        
                        content = pCtor?.newInstance(
                            TdApi.InputFileLocal(path), thumb, stickers, 0, 0,
                            TdApi.FormattedText(caption, null), false, selfDestruct, false
                        ) as TdApi.InputMessagePhoto
                    }

                    val sClass = TdApi.SendMessage::class.java
                    val sCtor = sClass.constructors[0]
                    val msgReq = if (sCtor.parameterCount == 6) {
                         sCtor.newInstance(chatRes.id, 0L, null, null, null, content) as TdApi.SendMessage
                    } else {
                         sCtor.newInstance(chatRes.id, 0L, null, null, content) as TdApi.SendMessage
                    }

                    client?.send(msgReq) { sent -> callback(sent !is TdApi.Error, null) }

                } catch (e: Exception) {
                    callback(false, "Internal Error: " + e.message)
                }
            } else { 
                callback(false, "Chat not found") 
            }
        }
    }
}
// Forced update for reflection build
