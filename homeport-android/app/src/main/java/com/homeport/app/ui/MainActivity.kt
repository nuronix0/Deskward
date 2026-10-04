package com.homeport.app.ui

import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.navigation.compose.rememberNavController
import com.homeport.app.network.HomePortClient
import com.homeport.app.network.MeshForegroundService
import com.homeport.app.network.NfcPairingManager
import com.homeport.app.ui.screens.onboarding.IntroScreen
import com.homeport.app.ui.screens.onboarding.WelcomeScreen
import com.homeport.app.ui.theme.Background
import com.homeport.app.ui.theme.HomePortTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    private val nfcManager by lazy { NfcPairingManager.getInstance(this) }
    private val homePortClient by lazy { HomePortClient.getInstance(this) }

    override fun onCreate(savedInstanceState: Bundle?) {
        val splashScreen = installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Request Manage External Storage on Android 11+ to allow P2P sharing of entire file system
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            if (!android.os.Environment.isExternalStorageManager()) {
                try {
                    val intent = android.content.Intent(android.provider.Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION)
                    intent.data = android.net.Uri.parse("package:$packageName")
                    startActivity(intent)
                } catch (e: Exception) {
                    val intent = android.content.Intent(android.provider.Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)
                    startActivity(intent)
                }
            }
        } else {
            val perms = mutableListOf(
                android.Manifest.permission.READ_EXTERNAL_STORAGE,
                android.Manifest.permission.WRITE_EXTERNAL_STORAGE
            )
            if (checkSelfPermission(perms[0]) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                requestPermissions(perms.toTypedArray(), 1002)
            }
        }

        // Request runtime notification permission on Android 13+ (API 33+) so foreground service notification is shown
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                requestPermissions(arrayOf(android.Manifest.permission.POST_NOTIFICATIONS), 1001)
            }
        }

        // Prompt exemption from OEM battery killers so background P2P mesh never disconnects on screen-off
        MeshForegroundService.requestBatteryOptimizationExemption(this)

        // Handle NFC tap that launched the app cold
        intent?.let { nfcManager.handleIntent(it, homePortClient) }

        setContent {
            HomePortTheme {
                HomePortApp()
            }
        }
    }

    override fun onResume() {
        super.onResume()
        nfcManager.refreshAvailability()
        nfcManager.enableForegroundDispatch(this)
        MeshForegroundService.syncState(this)
    }

    override fun onPause() {
        super.onPause()
        nfcManager.disableForegroundDispatch(this)
    }

    /** Android OS calls this when an NFC tag is detected while app is in foreground */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent) // keep the intent fresh
        nfcManager.handleIntent(intent, homePortClient)
    }
}

@Composable
fun HomePortApp() {
    val context = androidx.compose.ui.platform.LocalContext.current
    val prefs = remember { context.getSharedPreferences("portal_prefs", android.content.Context.MODE_PRIVATE) }
    val showOnboarding = remember { prefs.getBoolean("pref_show_onboarding", true) }
    var appState by remember { mutableStateOf(AppState.INTRO) }
    val navController = rememberNavController()

    when (appState) {
        AppState.INTRO -> {
            IntroScreen(
                onIntroComplete = {
                    appState = if (showOnboarding) AppState.ONBOARDING else AppState.MAIN
                }
            )
        }
        AppState.ONBOARDING -> {
            WelcomeScreen(
                onGetStarted = {
                    prefs.edit().putBoolean("pref_show_onboarding", false).apply()
                    appState = AppState.MAIN
                },
                onSignIn = {
                    prefs.edit().putBoolean("pref_show_onboarding", false).apply()
                    appState = AppState.MAIN
                }
            )
        }
        AppState.MAIN -> {
            HomePortNavHost(navController)
        }
    }
}

enum class AppState { INTRO, ONBOARDING, MAIN }

