package com.example.spotify.model

data class Song(
    val id: Int,
    val title: String,
    val artist: String,
    val duration: Int, // Durasi dalam detik
    val genre: String
)
