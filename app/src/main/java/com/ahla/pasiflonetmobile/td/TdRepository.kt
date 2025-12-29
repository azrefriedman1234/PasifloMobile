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

    // Handler לקבלת עדכונים
    private val resultHandler = Client.ResultHandler { update ->
        if (update is TdApi.UpdateAuthorizationState) {
            when (update.authorizationState) {
                is TdApi.AuthorizationStateWaitTdlibParameters -> {
                    // תיקון סופי: יצירת אובייקט ריק ומילוי שדות (מונע טעויות סדר)
                    val params = TdApi.SetTdlibParameters()
                    params.useTestDc = false
                    params.databaseDirectory = filesPath
                    params.filesDirectory = filesPath
                    params.databaseEncryptionKey = null
                    params.useFileDatabase = true
                    params.useChatInfoDatabase = true
                    params.useMessageDatabase = true
                    params.useSecretChats = true
                    params.apiId = currentApiId
                    params.apiHash = currentApiHash
                    params.systemLanguageCode = "en"
                    params.deviceModel = "Mobile"
                    params.systemVersion = "Android"
                    params.applicationVersion = "1.0"
                    params.enableStorageOptimizer = true
                    params.ignoreFileNames = true
                    
                    client?.send(params) { }
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
            if (r is
git add app/src/main/java/com/ahla/pasiflonetmobile/td/TdRepository.kt
git commit -m "Use field assignment instead of constructors for TDLib"
git push
cd ~/PasifloMobile

cat << 'EOF' > app/src/main/java/com/ahla/pasiflonetmobile/td/TdRepository.kt
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

    // Handler לקבלת עדכונים
    private val resultHandler = Client.ResultHandler { update ->
        if (update is TdApi.UpdateAuthorizationState) {
            when (update.authorizationState) {
                is TdApi.AuthorizationStateWaitTdlibParameters -> {
                    // תיקון סופי: יצירת אובייקט ריק ומילוי שדות (מונע טעויות סדר)
                    val params = TdApi.SetTdlibParameters()
                    params.useTestDc = false
                    params.databaseDirectory = filesPath
                    params.filesDirectory = filesPath
                    params.databaseEncryptionKey = null
                    params.useFileDatabase = true
                    params.useChatInfoDatabase = true
                    params.useMessageDatabase = true
                    params.useSecretChats = true
                    params.apiId = currentApiId
                    params.apiHash = currentApiHash
                    params.systemLanguageCode = "en"
                    params.deviceModel = "Mobile"
                    params.systemVersion = "Android"
                    params.applicationVersion = "1.0"
                    params.enableStorageOptimizer = true
                    params.ignoreFileNames = true
                    
                    client?.send(params) { }
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
                // שימוש באובייקטים ריקים ומילוי שדות - הרבה יותר בטוח
                val content: TdApi.InputMessageContent = if (path.endsWith(".mp4")) {
                    val video = TdApi.InputMessageVideo()
                    video.video = TdApi.InputFileLocal(path)
                    video.caption = TdApi.FormattedText(caption, null)
                    video
                } else {
                    val photo = TdApi.InputMessagePhoto()
                    photo.photo = TdApi.InputFileLocal(path)
                    photo.caption = TdApi.FormattedText(caption, null)
                    photo
                }

                val req = TdApi.SendMessage()
                req.chatId = chatRes.id
                req.inputMessageContent = content
                
                client?.send(req) { sent -> 
                    callback(sent !is TdApi.Error, null) 
                }
            } else { 
                callback(false, "Chat not found") 
            }
        }
    }
}
