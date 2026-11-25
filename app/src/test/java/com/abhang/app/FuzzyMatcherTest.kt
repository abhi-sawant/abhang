package com.abhang.app

import com.abhang.app.data.DevanagariNormalizer.normalize
import com.abhang.app.data.FuzzyMatcher
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class FuzzyMatcherTest {
    private fun errors(pattern: String, text: String) = FuzzyMatcher.of(normalize(pattern))!!.minErrors(normalize(text))

    @Test fun spacesInTextAreIgnored() {
        assertEquals(0, errors("सुंदरते", "सुंदर तें ध्यान"))
        assertEquals(0, errors("हरिमुखे", "हरि मुखे म्हणा"))
    }

    @Test fun oneTypoAllowedForMediumWords() {
        assertEquals(1, errors("विठठल", "माझा विठ्ठल")) // virama missing
        assertEquals(1, errors("पंडरपुर", "पंढरपुर"))   // ड for ढ
    }

    @Test fun tooManyTyposIsNoMatch() = assertEquals(-1, errors("विठ्ठल", "विटाळ"))

    @Test fun latinKeys() {
        val m = FuzzyMatcher.of("sundrt")!!
        assertEquals(0, m.minErrors("sundr te"))
        assertEquals(1, m.minErrors("sundr de"))
    }

    @Test fun typoBudgetGrowsWithLength() {
        assertEquals(0, FuzzyMatcher.typosFor(4))
        assertEquals(1, FuzzyMatcher.typosFor(5))
        assertEquals(2, FuzzyMatcher.typosFor(9))
    }

    @Test fun shortPatternsAreNotFuzzy() = assertNull(FuzzyMatcher.of("हरि"))
}
