package com.victor.postly.model

data class ChatMessage(
    val id: String = "",
    val chatId: String = "",
    val senderId: String = "",
    val text: String = "",
    val timestamp: Long = 0L,
    val type: String = TYPE_TEXT,   // "text" | "image" | "audio"
    val mediaBase64: String = "",   // imagem ou áudio em Base64
    val mediaMimeType: String = ""  // "image/jpeg" | "audio/aac"
) {
    companion object {
        const val TYPE_TEXT  = "text"
        const val TYPE_IMAGE = "image"
        const val TYPE_AUDIO = "audio"
    }
}
