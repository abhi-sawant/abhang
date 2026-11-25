package com.abhang.app.ui

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.os.Build
import android.provider.Settings
import android.speech.RecognizerIntent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.res.stringResource
import androidx.core.content.ContextCompat
import com.abhang.app.R

/** System speech-recognizer intent (Marathi). The recognizer app does the listening, so we need no mic permission. */
private fun voiceSearchIntent(prompt: String) = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
    putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
    putExtra(RecognizerIntent.EXTRA_LANGUAGE, "mr-IN")
    putExtra(RecognizerIntent.EXTRA_PROMPT, prompt)
    // No EXTRA_PREFER_OFFLINE: it forces the on-device model, and without a downloaded Marathi
    // pack Google's recognizer fails with LANGUAGE_PACK_ERROR. Left alone it uses the offline pack
    // when present and falls back to online recognition otherwise.
}

/**
 * True when the default network has working internet. Needs only ACCESS_NETWORK_STATE; the app
 * itself still never connects. VALIDATED avoids treating a just-joined or captive Wi-Fi as online.
 */
private fun isOnline(context: Context): Boolean {
    val cm = context.getSystemService(ConnectivityManager::class.java) ?: return false
    val caps = cm.getNetworkCapabilities(cm.activeNetwork ?: return false) ?: return false
    return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
        caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
}

/** Live [isOnline], tracked with a network callback only while [watch] is true. */
@Composable
private fun rememberOnlineState(context: Context, watch: Boolean): State<Boolean> =
    produceState(initialValue = isOnline(context), watch) {
        value = isOnline(context)
        if (!watch) return@produceState
        val cm = context.getSystemService(ConnectivityManager::class.java) ?: return@produceState
        val main = ContextCompat.getMainExecutor(context)
        val callback = object : ConnectivityManager.NetworkCallback() {
            private fun update() = main.execute { value = isOnline(context) }
            override fun onAvailable(network: Network) = update()
            override fun onCapabilitiesChanged(network: Network, caps: NetworkCapabilities) = update()
            override fun onLost(network: Network) = update()
        }
        cm.registerDefaultNetworkCallback(callback)
        awaitDispose { cm.unregisterNetworkCallback(callback) }
    }

/** Opens the quick internet panel (Wi-Fi + mobile data) where available, else the network settings. */
private fun openInternetSettings(context: Context) {
    val intent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) Intent(Settings.Panel.ACTION_INTERNET_CONNECTIVITY)
    else Intent(Settings.ACTION_WIRELESS_SETTINGS)
    try { context.startActivity(intent) } catch (_: ActivityNotFoundException) {
        try { context.startActivity(Intent(Settings.ACTION_SETTINGS)) } catch (_: ActivityNotFoundException) {}
    }
}

/**
 * Voice search via the system recognizer. Returns the mic click handler, or null when the device has
 * no recognizer (the caller then hides the mic).
 *
 * Most phones have no offline Marathi speech pack, so recognition needs internet. When the phone is
 * offline we ask the user to turn internet on (or try anyway, for those who do have the pack), and
 * start listening by ourselves once they come back online.
 */
@Composable
fun rememberVoiceSearch(onResult: (String) -> Unit): (() -> Unit)? {
    val context = LocalContext.current
    val prompt = stringResource(R.string.voice_prompt)
    val intent = remember(prompt) { voiceSearchIntent(prompt) }
    val available = remember(intent) { intent.resolveActivity(context.packageManager) != null }
    if (!available) return null

    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val spoken = result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()?.trim()
        if (result.resultCode == Activity.RESULT_OK && !spoken.isNullOrEmpty()) onResult(spoken)
    }
    fun listen() { try { launcher.launch(intent) } catch (_: ActivityNotFoundException) {} }

    var askInternet by rememberSaveable { mutableStateOf(false) }
    var waitingForInternet by rememberSaveable { mutableStateOf(false) } // user went to settings from our prompt

    // Back from settings with internet on: carry on with what they asked for. On Android 14+ the
    // internet panel is a System UI dialog over our activity (no pause/resume), so watch the network
    // itself and wait until our window has focus again, i.e. the panel is closed.
    val online by rememberOnlineState(context, watch = waitingForInternet)
    val focused = LocalWindowInfo.current.isWindowFocused
    LaunchedEffect(waitingForInternet, online, focused) {
        if (waitingForInternet && online && focused) {
            waitingForInternet = false
            listen()
        }
    }

    if (askInternet) {
        AlertDialog(
            onDismissRequest = { askInternet = false },
            title = { Text(stringResource(R.string.voice_needs_internet_title)) },
            text = { Text(stringResource(R.string.voice_needs_internet_body)) },
            confirmButton = {
                TextButton(onClick = {
                    askInternet = false
                    waitingForInternet = true
                    openInternetSettings(context)
                }) { Text(stringResource(R.string.voice_turn_on_internet)) }
            },
            dismissButton = {
                TextButton(onClick = { askInternet = false; listen() }) { Text(stringResource(R.string.voice_try_anyway)) }
            },
        )
    }

    return {
        waitingForInternet = false
        if (isOnline(context)) listen() else askInternet = true
    }
}
