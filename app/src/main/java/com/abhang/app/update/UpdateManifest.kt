package com.abhang.app.update

import org.json.JSONObject
import java.io.InputStream
import java.security.MessageDigest

/** One published release, as described by `latest.json` on the GitHub Release next to the APK. */
data class UpdateInfo(
    val versionCode: Int,
    val versionName: String,
    val apkUrl: String,
    val sha256: String,
    val size: Long,
) {
    fun isNewerThan(installedVersionCode: Long) = versionCode > installedVersionCode
}

/**
 * The update feed. `.github/workflows/release.yml` uploads `latest.json` with every release, and
 * GitHub's `releases/latest/download/<asset>` URL always redirects to the newest one, so the app
 * needs no API calls (and hits no API rate limit).
 *
 * Example:
 * `{"versionCode":10200,"versionName":"1.2.0","apkUrl":"https://github.com/…/1.2.0/abhang-1.2.0.apk","sha256":"…","size":2331053}`
 */
object UpdateManifest {
    const val REPO = "abhi-sawant/abhang"
    const val URL = "https://github.com/$REPO/releases/latest/download/latest.json"

    /** The APK must come from this repository's releases; anything else in the feed is rejected. */
    private const val APK_PREFIX = "https://github.com/$REPO/releases/download/"
    private val SHA256_HEX = Regex("^[0-9a-f]{64}$")
    private const val MAX_APK_BYTES = 100L * 1024 * 1024

    /** Parses and validates `latest.json`. Throws on anything malformed. */
    fun parse(text: String): UpdateInfo {
        val o = JSONObject(text)
        val info = UpdateInfo(
            versionCode = o.getInt("versionCode"),
            versionName = o.getString("versionName"),
            apkUrl = o.getString("apkUrl"),
            sha256 = o.getString("sha256").lowercase(),
            size = o.getLong("size"),
        )
        require(info.versionCode > 0) { "bad versionCode ${info.versionCode}" }
        require(info.versionCode == versionCode(info.versionName)) { "versionCode does not match versionName ${info.versionName}" }
        require(info.apkUrl.startsWith(APK_PREFIX) && info.apkUrl.endsWith(".apk") && ".." !in info.apkUrl) { "unexpected apkUrl ${info.apkUrl}" }
        require(SHA256_HEX.matches(info.sha256)) { "bad sha256" }
        require(info.size in 1..MAX_APK_BYTES) { "bad size ${info.size}" }
        return info
    }

    /** Same rule as app/build.gradle.kts: `X.Y.Z` → `X*10000 + Y*100 + Z`, each part 0..99. */
    fun versionCode(versionName: String): Int {
        val parts = versionName.split(".")
        require(parts.size == 3) { "version must be X.Y.Z: $versionName" }
        val n = parts.map { p -> p.toIntOrNull()?.takeIf { it in 0..99 } ?: throw IllegalArgumentException("bad version part in $versionName") }
        return n[0] * 10000 + n[1] * 100 + n[2]
    }

    fun sha256Hex(input: InputStream): String {
        val md = MessageDigest.getInstance("SHA-256")
        val buf = ByteArray(64 * 1024)
        while (true) {
            val r = input.read(buf)
            if (r < 0) break
            md.update(buf, 0, r)
        }
        return hex(md.digest())
    }

    fun hex(bytes: ByteArray): String = bytes.joinToString("") { "%02x".format(it) }
}
