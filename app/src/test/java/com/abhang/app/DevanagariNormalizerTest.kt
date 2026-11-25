package com.abhang.app

import com.abhang.app.data.DevanagariNormalizer.normalize
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DevanagariNormalizerTest {
    @Test fun stripsDandaAndDigits() {
        assertEquals("हरि मुखे म्हणा", normalize("हरि मुखे म्हणा ॥२॥"))
    }

    @Test fun longShortVowelsMatch() {
        assertEquals(normalize("हरी"), normalize("हरि"))
        assertEquals(normalize("तुळसी"), normalize("तुलसि"))
    }

    @Test fun joinersRemoved() {
        assertEquals(normalize("विठ्ठल"), normalize("विठ्‍ठल"))
    }

    @Test fun queryIsSubstringOfNormalizedText() {
        assertTrue(normalize("सुंदर ते ध्यान उभे विटेवरी । कर").contains(normalize("ध्यान")))
    }
}
