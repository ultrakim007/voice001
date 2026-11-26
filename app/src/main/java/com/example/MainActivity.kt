package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.auth.authStateFlow
import com.example.ui.AuthScreen
import com.example.ui.VoiceMemoScreen
import com.example.ui.theme.SeniorVoiceMemoTheme
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            SeniorVoiceMemoTheme {
                AppContent()
            }
        }
    }
}

@Composable
private fun AppContent(auth: FirebaseAuth = Firebase.auth) {
    val currentUser by auth.authStateFlow().collectAsStateWithLifecycle(
        initialValue = auth.currentUser
    )
    val user = currentUser
    if (user == null) {
        AuthScreen(onAuthSuccess = { /* Reactive authStateFlow transitions automatically */ })
    } else {
        key(user.uid) {
            VoiceMemoScreen(currentUserId = user.uid)
        }
    }
}
