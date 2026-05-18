package com.victor.postly.model

data class Comment(
    val id: String = "",
    val postId: String = "",
    val userId: String = "",
    val text: String = "",
    val timestamp: Long = 0L
)
