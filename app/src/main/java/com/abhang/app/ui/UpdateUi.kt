package com.abhang.app.ui

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.abhang.app.R
import com.abhang.app.update.UpdateInfo
import com.abhang.app.update.Updater
import kotlinx.coroutines.launch

/**
 * Returns the action behind every "update now" button. If Android does not yet let Abhang install
 * apps (Android 8+ asks per app), it first explains the one-time "Install unknown apps" switch in
 * Marathi and opens that setting; on return it installs.
 */
@Composable
private fun rememberInstallAction(): () -> Unit {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var askPermission by remember { mutableStateOf(false) }
    val install = { scope.launch { Updater.install(ctx) }; Unit }
    val settings = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        if (Updater.canInstallPackages(ctx)) install()
    }
    if (askPermission) {
        AlertDialog(
            onDismissRequest = { askPermission = false },
            title = { Text(stringResource(R.string.update_permission_title)) },
            text = { Text(stringResource(R.string.update_permission_body), style = MaterialTheme.typography.bodyLarge) },
            confirmButton = {
                TextButton(onClick = {
                    askPermission = false
                    try {
                        settings.launch(Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${ctx.packageName}")))
                    } catch (_: ActivityNotFoundException) {
                        install()   // no such settings screen on this phone: the system installer will ask instead
                    }
                }) { Text(stringResource(R.string.update_permission_open)) }
            },
            dismissButton = { TextButton(onClick = { askPermission = false }) { Text(stringResource(R.string.update_later)) } },
        )
    }
    return { if (Updater.canInstallPackages(ctx)) install() else askPermission = true }
}

/** Big "new version ready" card, on the home screen and in About. Shows nothing when no update is ready. */
@Composable
fun UpdateBanner(modifier: Modifier = Modifier) {
    val status by Updater.status.collectAsState()
    val info: UpdateInfo = when (val s = status) {
        is Updater.Status.Ready -> s.info
        is Updater.Status.Installing -> s.info
        is Updater.Status.InstallFailed -> s.info
        else -> return
    }
    val installing = status is Updater.Status.Installing
    val failed = status is Updater.Status.InstallFailed
    val onInstall = rememberInstallAction()
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primary, contentColor = MaterialTheme.colorScheme.onPrimary),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(R.string.update_ready_title), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Text(
                stringResource(if (failed) R.string.update_failed else R.string.update_ready_body, info.versionName.toDevanagariDigits()),
                style = MaterialTheme.typography.bodyLarge,
            )
            Button(
                onClick = onInstall,
                enabled = !installing,
                modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.onPrimary,
                    contentColor = MaterialTheme.colorScheme.primary,
                ),
            ) {
                Text(
                    stringResource(when { installing -> R.string.update_installing; failed -> R.string.update_retry; else -> R.string.update_now }),
                    fontSize = 19.sp, fontWeight = FontWeight.Bold,
                )
            }
        }
    }
}

/** The About screen's update section: status, progress and a manual "check now" button. */
@Composable
fun UpdateSection() {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val status by Updater.status.collectAsState()
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(stringResource(R.string.update_title), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
        when (val s = status) {
            is Updater.Status.Ready, is Updater.Status.Installing, is Updater.Status.InstallFailed -> UpdateBanner()
            is Updater.Status.Downloading -> {
                Text(stringResource(R.string.update_downloading, (s.fraction * 100).toInt().toString().toDevanagariDigits()), style = MaterialTheme.typography.bodyLarge)
                LinearProgressIndicator(progress = { s.fraction }, modifier = Modifier.fillMaxWidth())
            }
            else -> {
                Text(
                    stringResource(when (s) {
                        Updater.Status.Checking -> R.string.update_checking
                        Updater.Status.UpToDate -> R.string.update_up_to_date
                        Updater.Status.CheckFailed -> R.string.update_check_failed
                        else -> R.string.update_auto_text
                    }),
                    style = MaterialTheme.typography.bodyLarge,
                )
                OutlinedButton(
                    onClick = { scope.launch { Updater.check(ctx, manual = true) } },
                    enabled = s != Updater.Status.Checking,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
                ) { Text(stringResource(R.string.update_check), fontSize = 17.sp) }
            }
        }
    }
}
