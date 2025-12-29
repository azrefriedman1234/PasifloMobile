package com.ahla.pasiflonetmobile.td
import android.content.Context
import android.util.Log
import org.drinkless.tdlib.Client
import org.drinkless.tdlib.TdApi
import java.io.File

object TdRepository {
    private var client: Client? = null
    var onNewMessage: ((TelegramMsg) -> Unit)? = null
    var onStatusChanged: ((Boolean) -> Unit)? = null
    val downloadedFiles = mutableMapOf<Int, String>()

    fun init(context: Context) {
        if (client != null) return
        val prefs = context.getSharedPreferences("PasifloPrefs", Context.MODE_PRIVATE)
        val apiIdStr = prefs.getString("api_id", "")
        val apiHash = prefs.getString("api_hash", "")
        if (apiIdStr.isNullOrEmpty() || apiHash.isNullOrEmpty()) return

        val apiId = apiIdStr.toIntOrNull() ?: return
        val filesDir = context.filesDir.absolutePath
        val path = File(filesDir, "tdlib")
        if (!path.exists()) path.mkdirs()

        client = Client.create { update ->
            if (update is TdApi.UpdateAuthorizationState) {
                when (update.authorizationState) {
                    is TdApi.AuthorizationStateWaitTdlibParameters -> {
                        val parameters = TdApi.TdlibParameters()
                        parameters.databaseDirectory = path.absolutePath
                        parameters.useMessageDatabase = true
                        parameters.useSecretChats = true
                        parameters.apiId = apiId
                        parameters.apiHash = apiHash
                        parameters.systemLanguageCode = "en"
                        parameters.deviceModel = "Tablet"
                        parameters.applicationVersion = "1.0"
                        client?.send(TdApi.SetTdlibParameters(parameters)) { }
                    }
                    is TdApi.AuthorizationStateWaitPhoneNumber -> onStatusChanged?.invoke(false)
                    is TdApi.AuthorizationStateReady -> { onStatusChanged?.invoke(true); loadChats() }
                }
            } else if (update is TdApi.UpdateNewMessage) {
                handleNewMessage(update.message)
            } else if (update is TdApi.UpdateFile) {
                if (update.file.local.isDownloadingCompleted) downloadedFiles[update.file.id] = update.file.local.path
            }
        }
    }
    private fun handleNewMessage(msg: TdApi.Message) {
        val content = msg.content
        var text = ""; var fileId = "0"; var thumbPath: String? = null
        if (content is TdApi.MessageText) { text = content.text.text }
        else if (content is TdApi.MessageVideo) {
            text = content.caption.text; fileId = content.video.video.id.toString()
            downloadFile(content.video.video.id); thumbPath = content.video.thumbnail?.file?.local?.path
        } else if (content is TdApi.MessagePhoto) {
            text = content.caption.text; val size = content.photo.sizes.lastOrNull()
            if (size != null) { fileId = size.photo.id.toString(); downloadFile(size.photo.id); thumbPath = size.photo.local.path }
        }
        onNewMessage?.invoke(TelegramMsg(msg.id, text, java.text.SimpleDateFormat("HH:mm").format(java.util.Date(msg.date.toLong() * 1000)), if (content is TdApi.MessageText) "text" else "media", fileId, null, thumbPath))
    }
    private fun loadChats() { client?.send(TdApi.GetChats(TdApi.ChatListMain(), 9223372036854775807L, 0, 20)) { } }
    private fun downloadFile(fileId: Int) { client?.send(TdApi.DownloadFile(fileId, 32, 0, 0, true)) { } }
    fun sendCode(phone: String, cb: (Boolean, String?) -> Unit) { client?.send(TdApi.SetAuthenticationPhoneNumber(phone, null)) { r -> if (r is TdApi.Error) cb(false, r.message) else cb(true, null) } }
    fun verifyCode(code: String, cb: (Boolean, String?) -> Unit) { client?.send(TdApi.CheckAuthenticationCode(code)) { r -> if (r is TdApi.Error) cb(false, r.message) else cb(true, null) } }
    fun verifyPassword(pass: String, cb: (Boolean, String?) -> Unit) { client?.send(TdApi.CheckAuthenticationPassword(pass)) { r -> if (r is TdApi.Error) cb(false, r.message) else cb(true, null) } }
    fun sendVideoByUsername(username: String, path: String, caption: String, callback: (Boolean, String?) -> Unit) {
        client?.send(TdApi.SearchPublicChat(username)) { chatRes ->
            if (chatRes is TdApi.Chat) {
                val content = if (path.endsWith(".mp4")) TdApi.InputMessageVideo(TdApi.InputFileLocal(path), null, null, 0, 0, 0, null, TdApi.FormattedText(caption, null), 0) else TdApi.InputMessagePhoto(TdApi.InputFileLocal(path), null, null, 0, 0, TdApi.FormattedText(caption, null), 0)
                client?.send(TdApi.SendMessage(chatRes.id, 0, 0, null, null, content)) { sent -> callback(sent !is TdApi.Error, null) }
            } else { callback(false, "Chat not found") }
        }
    }
}
