package com.example

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.example.data.firebase.configureAppCheck
import com.example.data.update.PlayInAppUpdateController
import com.example.data.update.PlayUpdateUiState
import com.example.ui.AtelierRootApp
import com.example.ui.screens.AuthScreen
import com.example.ui.theme.AtelierTheme
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import kotlinx.coroutines.flow.StateFlow

class MainActivity : ComponentActivity() {

    private lateinit var playUpdateController: PlayInAppUpdateController
    private lateinit var inAppUpdateLauncher: ActivityResultLauncher<IntentSenderRequest>

    private fun ensureScreenshotsAllowed() {
        window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            setRecentsScreenshotEnabled(true)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // STEP 1: Mandatory App Check configuration with launch intent BEFORE UI or Firebase operations
        configureAppCheck(this, intent)

        // STEP 2: Initialize Google Play In-App Updates API controller & launcher
        playUpdateController = PlayInAppUpdateController(applicationContext)
        inAppUpdateLauncher = registerForActivityResult(
            ActivityResultContracts.StartIntentSenderForResult()
        ) { result ->
            playUpdateController.onActivityResult(result.resultCode)
        }
        playUpdateController.checkPlayUpdateAvailability()

        // Explicitly allow screenshots and screen capture across the app window & recents
        ensureScreenshotsAllowed()

        // STEP 3: Edge-to-edge layout
        enableEdgeToEdge()

        setContent {
            AtelierTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    AtelierAppEntry(
                        playUpdateStateFlow = playUpdateController.state,
                        onCheckPlayUpdates = { playUpdateController.checkPlayUpdateAvailability() },
                        onStartPlayInAppUpdate = { preferImmediate ->
                            playUpdateController.startInAppUpdate(
                                launcher = inAppUpdateLauncher,
                                preferImmediate = preferImmediate,
                                onFallbackOpenStore = {
                                    playUpdateController.openPlayStoreListing(this)
                                }
                            )
                        },
                        onCompleteFlexiblePlayUpdate = {
                            playUpdateController.completeFlexibleUpdate()
                        },
                        onOpenPlayStoreListing = {
                            playUpdateController.openPlayStoreListing(this)
                        }
                    )
                }
            }
        }

        window.decorView.post { ensureScreenshotsAllowed() }
    }

    override fun onStart() {
        super.onStart()
        ensureScreenshotsAllowed()
        playUpdateController.registerListener()
    }

    override fun onResume() {
        super.onResume()
        ensureScreenshotsAllowed()
        playUpdateController.handleResume(inAppUpdateLauncher)
    }

    override fun onStop() {
        playUpdateController.unregisterListener()
        super.onStop()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) {
            ensureScreenshotsAllowed()
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        configureAppCheck(this, intent)
    }
}

@Composable
private fun AtelierAppEntry(
    playUpdateStateFlow: StateFlow<PlayUpdateUiState>,
    onCheckPlayUpdates: () -> Unit,
    onStartPlayInAppUpdate: (preferImmediate: Boolean) -> Unit,
    onCompleteFlexiblePlayUpdate: () -> Unit,
    onOpenPlayStoreListing: () -> Unit
) {
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

    val user = currentUser
    if (user == null) {
        AuthScreen(
            onAuthSuccess = {
                currentUser = Firebase.auth.currentUser
            }
        )
    } else {
        key(user.uid) {
            AtelierRootApp(
                currentUserId = user.uid,
                playUpdateStateFlow = playUpdateStateFlow,
                onCheckPlayUpdates = onCheckPlayUpdates,
                onStartPlayInAppUpdate = onStartPlayInAppUpdate,
                onCompleteFlexiblePlayUpdate = onCompleteFlexiblePlayUpdate,
                onOpenPlayStoreListing = onOpenPlayStoreListing,
                onSignedOut = {
                    currentUser = null
                }
            )
        }
    }
}
