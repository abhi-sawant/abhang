package com.abhang.app.update

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import androidx.core.content.IntentCompat

/** Receives the result of the [PackageInstaller] session that [Updater.install] commits. */
class InstallResultReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val app = context.applicationContext
        val status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)
        val background = intent.getBooleanExtra(Updater.EXTRA_BACKGROUND, false)
        if (status == PackageInstaller.STATUS_PENDING_USER_ACTION) {
            Updater.onNeedsUserAction(app, IntentCompat.getParcelableExtra(intent, Intent.EXTRA_INTENT, Intent::class.java), background)
        } else {
            Updater.onInstallResult(app, status, intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE), background)
        }
    }
}
