package com.example.spotify.model

data class User(
    val id_user: Int,
    val username: String,
    val email: String,
    val password: String,
    val role: String
)