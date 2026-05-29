package com.victor.postly.model

data class ChatThread(
    val id: String = "",
    val participants: List<String> = emptyList(),
    val lastMessage: String = "",
    val lastSenderId: String = "",
    val lastTimestamp: Long = 0L,
    val createdAt: Long = 0L,
    val updatedAt: Long = 0L
)
