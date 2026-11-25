package com.abhang.app.update

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInfo
import android.content.pm.PackageInstaller
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.core.content.pm.PackageInfoCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

/**
 * Self-updater for builds installed outside a store.
 *
 * 1. [check] reads `latest.json` from GitHub Releases ([UpdateManifest]) and, when it names a newer
 *    version, downloads the APK into app-private storage and verifies its size, SHA-256, package
 *    name, version code and signing certificate. The verified APK is then "ready".
 * 2. [install] hands the ready APK to [PackageInstaller]. On Android 12+ the session asks for
 *    [PackageInstaller.SessionParams.USER_ACTION_NOT_REQUIRED], so once the user has allowed
 *    "Install unknown apps" for Abhang, updates install with no prompt. Everywhere else (older
 *    Android, phone makers that always confirm, permission not yet granted) the system asks once
 *    and [InstallResultReceiver] shows that confirmation, or the home banner offers it next time
 *    the app is open.
 *
 * [UpdateWorker] runs [check] in the background and installs only while the app is not on screen,
 * because installing an update closes the running app.
 */
object Updater {
    private const val TAG = "AbhangUpdater"
    private const val PREFS = "updater"
    private const val KEY_READY = "ready_manifest"   // latest.json text of the verified, downloaded APK
    private const val KEY_BAD = "bad_version_code"   // a version Android refused to install; not retried
    private const val KEY_BG_DECLINED = "bg_declined_version_code"   // a background install that needed the user; banner only
    internal const val EXTRA_BACKGROUND = "com.abhang.app.update.BACKGROUND"

    sealed interface Status {
        data object Idle : Status
        data object Checking : Status
        data class Downloading(val fraction: Float) : Status
        data object UpToDate : Status
        data class Ready(val info: UpdateInfo) : Status
        data class Installing(val info: UpdateInfo) : Status
        data object CheckFailed : Status
        data class InstallFailed(val info: UpdateInfo) : Status
    }

    private val _status = MutableStateFlow<Status>(Status.Idle)
    val status: StateFlow<Status> = _status.asStateFlow()

    /** Set by MainActivity's onStart/onStop. Background installs wait until this is false. */
    @Volatile var appInForeground = false

    private val mutex = Mutex()

    private fun prefs(ctx: Context) = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    private fun dir(ctx: Context) = File(ctx.noBackupFilesDir, "updates").apply { mkdirs() }
    private fun apkFile(ctx: Context) = File(dir(ctx), "update.apk")

    fun installedVersionCode(ctx: Context): Long =
        PackageInfoCompat.getLongVersionCode(ctx.packageManager.getPackageInfo(ctx.packageName, 0))

    /** True when Android lets this app start installs (Android 8+ asks per app; 7 uses the global "Unknown sources"). */
    fun canInstallPackages(ctx: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.O || ctx.packageManager.canRequestPackageInstalls()

    /** Whether Android may install without asking: Android 12+, and the install permission granted. */
    fun canInstallSilently(ctx: Context): Boolean =
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && canInstallPackages(ctx)

    /** The verified update waiting to be installed, if it is still newer than what is installed. */
    private fun readyInfo(ctx: Context): UpdateInfo? {
        val text = prefs(ctx).getString(KEY_READY, null) ?: return null
        val info = runCatching { UpdateManifest.parse(text) }.getOrNull()
        if (info == null || !info.isNewerThan(installedVersionCode(ctx)) || !apkFile(ctx).isFile) {
            clearReady(ctx)
            return null
        }
        return info
    }

    private fun clearReady(ctx: Context) {
        prefs(ctx).edit().remove(KEY_READY).apply()
        dir(ctx).listFiles()?.forEach { it.delete() }
    }

    /** Restores the status at app start, and removes the APK of an update that has been installed. */
    fun restore(ctx: Context) {
        val app = ctx.applicationContext
        val ready = readyInfo(app)
        if (_status.value is Status.Idle || _status.value is Status.Ready) {
            _status.value = if (ready != null) Status.Ready(ready) else Status.Idle
        }
    }

    /**
     * Checks for a newer release and downloads it. [manual] (the About screen's button) shows
     * "checking", "up to date" and failures; a background check stays quiet unless it finds one.
     * Returns false when the check could not be completed (offline, server error), so the worker can retry.
     */
    suspend fun check(ctx: Context, manual: Boolean): Boolean = mutex.withLock {
        withContext(Dispatchers.IO) {
            val app = ctx.applicationContext
            if (_status.value is Status.Installing) return@withContext true
            if (manual) _status.value = Status.Checking
            try {
                val text = fetchText(UpdateManifest.URL)
                val info = UpdateManifest.parse(text)
                val installed = installedVersionCode(app)
                val prefs = prefs(app)
                if (!info.isNewerThan(installed) || info.versionCode == prefs.getInt(KEY_BAD, -1)) {
                    clearReady(app)
                    _status.value = if (manual) Status.UpToDate else Status.Idle
                    return@withContext true
                }
                val ready = readyInfo(app)
                if (ready != null && ready.versionCode == info.versionCode) {
                    _status.value = Status.Ready(ready)
                    return@withContext true
                }
                clearReady(app)
                val apk = apkFile(app)
                download(info, apk)
                try {
                    verifyApk(app, apk, info)
                } catch (e: Exception) {
                    apk.delete()
                    throw e
                }
                prefs.edit().putString(KEY_READY, text).apply()
                _status.value = Status.Ready(info)
                true
            } catch (e: Exception) {
                Log.w(TAG, "update check failed", e)
                val ready = readyInfo(app)
                _status.value = when {
                    ready != null -> Status.Ready(ready)
                    manual -> Status.CheckFailed
                    else -> Status.Idle
                }
                false
            }
        }
    }

    private fun open(url: String): HttpURLConnection {
        val conn = URL(url).openConnection() as HttpURLConnection
        conn.connectTimeout = 20_000
        conn.readTimeout = 30_000
        conn.instanceFollowRedirects = true   // GitHub redirects release assets to its CDN (https → https)
        conn.setRequestProperty("User-Agent", "Abhang-Android-Updater")
        conn.setRequestProperty("Cache-Control", "no-cache")
        val code = conn.responseCode
        if (code != HttpURLConnection.HTTP_OK) {
            conn.disconnect()
            throw IOException("HTTP $code for $url")
        }
        if (conn.url.protocol != "https") {
            conn.disconnect()
            throw IOException("refusing non-https download ${conn.url}")
        }
        return conn
    }

    private fun fetchText(url: String): String {
        val conn = open(url)
        try {
            val bytes = conn.inputStream.use { it.readBytes() }
            if (bytes.size > 16 * 1024) throw IOException("latest.json too large")
            return String(bytes, Charsets.UTF_8)
        } finally {
            conn.disconnect()
        }
    }

    private fun download(info: UpdateInfo, dest: File) {
        val part = File(dest.parentFile, dest.name + ".part")
        val conn = open(info.apkUrl)
        try {
            val md = MessageDigest.getInstance("SHA-256")
            var total = 0L
            var lastShown = -1
            conn.inputStream.use { input ->
                part.outputStream().use { out ->
                    val buf = ByteArray(64 * 1024)
                    while (true) {
                        val r = input.read(buf)
                        if (r < 0) break
                        total += r
                        if (total > info.size) throw IOException("APK larger than announced")
                        md.update(buf, 0, r)
                        out.write(buf, 0, r)
                        val pct = (total * 100 / info.size).toInt()
                        if (pct != lastShown) { lastShown = pct; _status.value = Status.Downloading(pct / 100f) }
                    }
                }
            }
            if (total != info.size) throw IOException("APK size $total, expected ${info.size}")
            val sha = UpdateManifest.hex(md.digest())
            if (sha != info.sha256) throw IOException("APK checksum mismatch")
            if (!part.renameTo(dest)) throw IOException("could not save APK")
        } finally {
            conn.disconnect()
            part.delete()
        }
    }

    /** Rejects an APK that is not a newer build of this app signed with the same key. */
    private fun verifyApk(ctx: Context, apk: File, info: UpdateInfo) {
        val pm = ctx.packageManager
        @Suppress("DEPRECATION")
        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) PackageManager.GET_SIGNING_CERTIFICATES else PackageManager.GET_SIGNATURES
        val archive = pm.getPackageArchiveInfo(apk.path, flags) ?: throw IOException("not a valid APK")
        if (archive.packageName != ctx.packageName) throw IOException("APK is for ${archive.packageName}")
        if (PackageInfoCompat.getLongVersionCode(archive) != info.versionCode.toLong()) throw IOException("APK version does not match latest.json")
        val newCerts = certDigests(archive)
        val installedCerts = certDigests(pm.getPackageInfo(ctx.packageName, flags))
        // Older Android versions do not report certificates for archives; the installer still enforces them.
        if (newCerts.isNotEmpty() && installedCerts.isNotEmpty() && newCerts != installedCerts) {
            throw IOException("APK is signed with a different key")
        }
    }

    @Suppress("DEPRECATION")
    private fun certDigests(info: PackageInfo): Set<String> {
        val sigs = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val s = info.signingInfo ?: return emptySet()
            if (s.hasMultipleSigners()) s.apkContentsSigners else s.signingCertificateHistory
        } else info.signatures
        return sigs.orEmpty().map { UpdateManifest.hex(MessageDigest.getInstance("SHA-256").digest(it.toByteArray())) }.toSet()
    }

    /**
     * Background path: installs only when the app is not on screen and Android should not need to ask.
     * A version whose background install already needed the user (a confirmation, a Play Protect
     * prompt that was declined) is not retried in the background, so no prompt pops up again and
     * again; the home banner offers it instead.
     */
    suspend fun installIfIdle(ctx: Context) {
        val ready = _status.value as? Status.Ready ?: return
        if (appInForeground || !canInstallSilently(ctx)) return
        if (prefs(ctx).getInt(KEY_BG_DECLINED, -1) == ready.info.versionCode) return
        install(ctx, background = true)
    }

    private fun markBackgroundDeclined(ctx: Context, info: UpdateInfo) {
        prefs(ctx).edit().putInt(KEY_BG_DECLINED, info.versionCode).apply()
    }

    /**
     * Starts installing the ready update. On success Android replaces the app and closes it.
     * If Android needs the user to confirm, [InstallResultReceiver] shows the confirmation while
     * the app is open, or the status stays [Status.Ready] so the home banner offers it again.
     */
    suspend fun install(ctx: Context, background: Boolean = false) = mutex.withLock {
        withContext(Dispatchers.IO) {
            val app = ctx.applicationContext
            val info = readyInfo(app) ?: return@withContext
            val apk = apkFile(app)
            _status.value = Status.Installing(info)
            try {
                val installer = app.packageManager.packageInstaller
                // A session left over from an earlier attempt that the user never confirmed.
                installer.mySessions.forEach { runCatching { installer.abandonSession(it.sessionId) } }
                val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL).apply {
                    setAppPackageName(app.packageName)
                    setSize(apk.length())
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) setInstallReason(PackageManager.INSTALL_REASON_USER)
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_NOT_REQUIRED)
                }
                val id = installer.createSession(params)
                installer.openSession(id).use { session ->
                    apk.inputStream().use { input ->
                        session.openWrite("abhang.apk", 0, apk.length()).use { out ->
                            input.copyTo(out)
                            session.fsync(out)
                        }
                    }
                    val intent = Intent(app, InstallResultReceiver::class.java).setPackage(app.packageName)
                        .putExtra(EXTRA_BACKGROUND, background)
                    // The installer adds its result to this intent, so it must be mutable (it is explicit, so that is safe).
                    val piFlags = PendingIntent.FLAG_UPDATE_CURRENT or
                        (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) PendingIntent.FLAG_MUTABLE else 0)
                    val pi = PendingIntent.getBroadcast(app, id, intent, piFlags)
                    session.commit(pi.intentSender)
                }
            } catch (e: Exception) {
                Log.w(TAG, "install failed to start", e)
                if (background) { markBackgroundDeclined(app, info); _status.value = Status.Ready(info) }
                else _status.value = Status.InstallFailed(info)
            }
        }
    }

    // --- Results from InstallResultReceiver ---

    internal fun onNeedsUserAction(ctx: Context, confirm: Intent?, background: Boolean) {
        val info = readyInfo(ctx) ?: return
        Log.i(TAG, "install of ${info.versionName} needs the user (background=$background, foreground=$appInForeground)")
        // Android wants a confirmation on this phone, so background installs of this version cannot be silent.
        if (background) markBackgroundDeclined(ctx, info)
        if (confirm != null && appInForeground) {
            try {
                ctx.startActivity(confirm.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            } catch (e: Exception) {
                Log.w(TAG, "could not show install confirmation", e)
            }
        }
        // Back to Ready either way: if the user leaves the confirmation without answering, the
        // banner's button works again (install() abandons the unanswered session).
        _status.value = Status.Ready(info)
    }

    internal fun onInstallResult(ctx: Context, status: Int, message: String?, background: Boolean) {
        val info = readyInfo(ctx)
        Log.i(TAG, "install result $status ($message), background=$background")
        when (status) {
            PackageInstaller.STATUS_SUCCESS -> { clearReady(ctx); _status.value = Status.Idle }
            PackageInstaller.STATUS_FAILURE_ABORTED -> {   // the user (or Play Protect) said no; offer it again by banner
                if (background && info != null) markBackgroundDeclined(ctx, info)
                _status.value = if (info != null) Status.Ready(info) else Status.Idle
            }
            PackageInstaller.STATUS_FAILURE_INVALID,
            PackageInstaller.STATUS_FAILURE_INCOMPATIBLE,
            PackageInstaller.STATUS_FAILURE_CONFLICT -> {
                // Android will never accept this APK (for example, a different signing key): drop it and skip this version.
                Log.w(TAG, "update rejected: $status $message")
                if (info != null) prefs(ctx).edit().putInt(KEY_BAD, info.versionCode).apply()
                clearReady(ctx)
                _status.value = Status.Idle
            }
            else -> {   // storage full, blocked by Play Protect, etc.: keep the APK and try again later
                Log.w(TAG, "update failed: $status $message")
                if (background && info != null) {
                    markBackgroundDeclined(ctx, info)       // a background failure falls back to the one-tap banner
                    _status.value = Status.Ready(info)
                } else {
                    _status.value = if (info != null) Status.InstallFailed(info) else Status.Idle
                }
            }
        }
    }
}
