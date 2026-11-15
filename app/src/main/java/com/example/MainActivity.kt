package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.local.ThemePreferences
import com.example.ui.JournalMainScreen
import com.example.ui.auth.AuthScreen
import com.example.ui.theme.JournalWorkflowTheme
import com.example.ui.theme.ThemeMode
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    enableEdgeToEdge()
    setContent {
      JournalWorkflowRoot()
    }
  }
}

@Composable
fun JournalWorkflowRoot() {
  val context = LocalContext.current
  val themePrefs = remember(context) { ThemePreferences(context.applicationContext) }
  val themeMode by themePrefs.themeMode.collectAsStateWithLifecycle(initialValue = ThemeMode.SYSTEM)
  val scope = rememberCoroutineScope()

  var currentUser by remember { mutableStateOf(Firebase.auth.currentUser) }

  DisposableEffect(Unit) {
    val listener = FirebaseAuth.AuthStateListener { auth ->
      currentUser = auth.currentUser
    }
    Firebase.auth.addAuthStateListener(listener)
    onDispose {
      Firebase.auth.removeAuthStateListener(listener)
    }
  }

  JournalWorkflowTheme(themeMode = themeMode) {
    val user = currentUser
    if (user == null) {
      AuthScreen(
        onAuthSuccess = {
          currentUser = Firebase.auth.currentUser
        },
      )
    } else {
      key(user.uid) {
        JournalMainScreen(
          firebaseUser = user,
          themeMode = themeMode,
          onCycleTheme = {
            val next = when (themeMode) {
              ThemeMode.SYSTEM -> ThemeMode.LIGHT
              ThemeMode.LIGHT -> ThemeMode.DARK
              ThemeMode.DARK -> ThemeMode.SYSTEM
            }
            scope.launch { themePrefs.setThemeMode(next) }
          },
        )
      }
    }
  }
}
