package com.victor.postly.model

data class Post(
    val id: String = "",
    val userId: String = "",
    val description: String = "",
    val image: String? = null,
    val timestamp: Long = 0L,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val locationName: String? = null,
    val commentCount: Int = 0,
    val likeCount: Int = 0,
    val likedBy: List<String> = emptyList()
)
