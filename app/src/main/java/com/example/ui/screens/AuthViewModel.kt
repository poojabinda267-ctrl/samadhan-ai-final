package com.example.ui.screens

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.auth.AuthRepository
import com.example.data.auth.UserProfile
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class AuthViewModel(application: Application) : AndroidViewModel(application) {

    private val authRepository = AuthRepository(application)

    val currentUser: StateFlow<UserProfile?> = authRepository.currentUser

    val isLoggedIn: StateFlow<Boolean> = authRepository.isLoggedIn

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    fun clearError() {
        _errorMessage.value = null
    }

    fun signInWithGoogle(context: Context) {
        if (_isLoading.value) return
        _isLoading.value = true
        _errorMessage.value = null

        viewModelScope.launch {
            try {
                val result = authRepository.signInWithGoogle(context)
                if (result.isFailure) {
                    _errorMessage.value = result.exceptionOrNull()?.localizedMessage ?: "Google Sign-In failed"
                }
            } catch (e: Exception) {
                _errorMessage.value = e.localizedMessage ?: "Sign in error"
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun signInWithEmail(email: String, name: String?, password: String) {
        if (_isLoading.value) return
        _isLoading.value = true
        _errorMessage.value = null

        viewModelScope.launch {
            try {
                val result = authRepository.signInWithEmail(email = email, name = name, password = password)
                if (result.isFailure) {
                    _errorMessage.value = result.exceptionOrNull()?.localizedMessage ?: "Email Sign-In failed"
                }
            } catch (e: Exception) {
                _errorMessage.value = e.localizedMessage ?: "Sign in error"
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun signOut() {
        authRepository.signOut()
    }
}
