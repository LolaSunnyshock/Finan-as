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
import androidx.credentials.CredentialManager
import com.example.ui.MainScreen
import com.example.ui.auth.AuthManager
import com.example.ui.auth.AuthScreen
import com.example.ui.theme.MyApplicationTheme
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            MyApplicationTheme {
                AppRootContent()
            }
        }
    }
}

@Composable
fun AppRootContent() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val credentialManager = remember { CredentialManager.create(context) }
    var currentUser by remember { mutableStateOf(Firebase.auth.currentUser) }
    var isGuestMode by remember { mutableStateOf(false) }

    DisposableEffect(Unit) {
        val listener = FirebaseAuth.AuthStateListener { auth ->
            currentUser = auth.currentUser
            if (currentUser != null) {
                isGuestMode = false
            }
        }
        Firebase.auth.addAuthStateListener(listener)

        // Attempt silent background auto sign-in if not signed in
        if (currentUser == null && !isGuestMode) {
            AuthManager.attemptAutoSignIn(
                context = context,
                credentialManager = credentialManager,
                onAuthSuccess = {
                    currentUser = Firebase.auth.currentUser
                    isGuestMode = false
                },
                onUnauthenticated = {
                    // Ready on interactive AuthScreen
                },
                scope = scope
            )
        }

        onDispose {
            Firebase.auth.removeAuthStateListener(listener)
        }
    }

    if (currentUser == null && !isGuestMode) {
        AuthScreen(
            onAuthSuccess = {
                currentUser = Firebase.auth.currentUser
                isGuestMode = false
            },
            onGuestAccess = {
                isGuestMode = true
            }
        )
    } else {
        val uid = currentUser?.uid ?: "guest_user"
        key(uid) {
            MainScreen(
                currentUserId = uid,
                onSignOutComplete = {
                    currentUser = null
                    isGuestMode = false
                }
            )
        }
    }
}
