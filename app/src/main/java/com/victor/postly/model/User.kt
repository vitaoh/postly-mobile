package com.victor.postly.model

data class User(
    val uid: String = "",
    val name: String = "",
    val username: String = "",
    val email: String = "",
    val photo: String? = null
)