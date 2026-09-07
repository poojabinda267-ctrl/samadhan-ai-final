package com.example.data.auth

data class UserProfile(
    val id: String,
    val name: String,
    val email: String,
    val photoUrl: String? = null,
    val provider: String = "google" // "google" or "email"
)
