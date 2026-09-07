package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.ui.i18n.LocalAppStrings
import com.example.ui.i18n.strings
import com.example.ui.screens.AuthViewModel
import com.example.ui.screens.ChatScreen
import com.example.ui.screens.ChatViewModel
import com.example.ui.screens.LoginScreen
import com.example.ui.theme.MyApplicationTheme

class MainActivity : ComponentActivity() {

  private val authViewModel: AuthViewModel by viewModels()
  private val chatViewModel: ChatViewModel by viewModels()

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    enableEdgeToEdge()
    setContent {
      MyApplicationTheme {
        val appLanguage by chatViewModel.appLanguage.collectAsStateWithLifecycle()

        CompositionLocalProvider(LocalAppStrings provides appLanguage.strings) {
          Surface(modifier = Modifier.fillMaxSize()) {
            val isLoggedIn by authViewModel.isLoggedIn.collectAsStateWithLifecycle()

            Crossfade(
              targetState = isLoggedIn,
              animationSpec = tween(durationMillis = 300),
              label = "AuthNavigationTransition"
            ) { loggedIn ->
              if (loggedIn) {
                ChatScreen(viewModel = chatViewModel)
              } else {
                LoginScreen(viewModel = authViewModel)
              }
            }
          }
        }
      }
    }
  }
}



