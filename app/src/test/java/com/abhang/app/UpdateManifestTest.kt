package com.abhang.app

import com.abhang.app.update.UpdateManifest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream

class UpdateManifestTest {
    private val sha = "98ea6e4f216f2fb4b69fff9b3a44842c38686ca685f3f55dc48c5d3fb1107be4"
    private val url = "https://github.com/abhi-sawant/abhang/releases/download/1.2.0/abhang-1.2.0.apk"

    private fun json(
        code: Int = 10200, name: String = "1.2.0", apkUrl: String = url, sha256: String = sha, size: Long = 2331053,
    ) = """{"versionCode":$code,"versionName":"$name","apkUrl":"$apkUrl","sha256":"$sha256","size":$size}"""

    private fun rejects(text: String) {
        val r = runCatching { UpdateManifest.parse(text) }
        assertTrue("expected rejection of $text", r.isFailure)
    }

    @Test fun parsesWhatTheReleaseWorkflowWrites() {
        val info = UpdateManifest.parse(json())
        assertEquals(10200, info.versionCode)
        assertEquals("1.2.0", info.versionName)
        assertEquals(url, info.apkUrl)
        assertEquals(sha, info.sha256)
        assertEquals(2331053L, info.size)
    }

    @Test fun uppercaseChecksumIsNormalized() {
        assertEquals(sha, UpdateManifest.parse(json(sha256 = sha.uppercase())).sha256)
    }

    @Test fun newerOnlyWhenVersionCodeIsHigher() {
        val info = UpdateManifest.parse(json())
        assertTrue(info.isNewerThan(10101))
        assertFalse(info.isNewerThan(10200))
        assertFalse(info.isNewerThan(10201))
    }

    @Test fun versionCodeMatchesGradleRule() {
        assertEquals(10101, UpdateManifest.versionCode("1.1.1"))
        assertEquals(10200, UpdateManifest.versionCode("1.2.0"))
        assertEquals(999999, UpdateManifest.versionCode("99.99.99"))
        assertTrue(runCatching { UpdateManifest.versionCode("1.100.0") }.isFailure)
        assertTrue(runCatching { UpdateManifest.versionCode("1.2") }.isFailure)
    }

    @Test fun rejectsApkFromElsewhere() {
        rejects(json(apkUrl = "https://example.com/abhang-1.2.0.apk"))
        rejects(json(apkUrl = "http://github.com/abhi-sawant/abhang/releases/download/1.2.0/abhang-1.2.0.apk"))
        rejects(json(apkUrl = "https://github.com/someone-else/abhang/releases/download/1.2.0/abhang-1.2.0.apk"))
        rejects(json(apkUrl = "https://github.com/abhi-sawant/abhang/releases/download/../../x/abhang.apk"))
        rejects(json(apkUrl = "https://github.com/abhi-sawant/abhang/releases/download/1.2.0/notes.txt"))
    }

    @Test fun rejectsBadFields() {
        rejects(json(code = 10300))           // does not match versionName
        rejects(json(code = 0, name = "0.0.0"))
        rejects(json(sha256 = "abc"))
        rejects(json(size = 0))
        rejects(json(size = 500L * 1024 * 1024))
        rejects("""{"versionCode":10200}""")
        rejects("not json")
    }

    @Test fun sha256OfStream() {
        assertEquals(sha, UpdateManifest.sha256Hex(ByteArrayInputStream("hi\n".toByteArray())))
    }
}
