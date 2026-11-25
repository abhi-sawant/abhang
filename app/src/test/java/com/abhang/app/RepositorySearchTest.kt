package com.abhang.app

import com.abhang.app.data.AbhangRepository
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.BeforeClass
import org.junit.Test
import java.io.File

/** Runs the real repository + normalizer against the bundled abhangs.json. */
class RepositorySearchTest {
    companion object {
        lateinit var repo: AbhangRepository
        @BeforeClass @JvmStatic fun load() {
            repo = AbhangRepository(File("src/main/assets/abhangs.json").readText(Charsets.UTF_8))
        }
    }

    private fun hits(q: String) = runBlocking { repo.search(q) }

    @Test fun nineCategoriesAndAllAbhangsAssigned() {
        assertEquals(9, repo.categories.size)
        assertTrue(repo.categories.sumOf { repo.count(it.id) } > 4000)
    }

    @Test fun vitthalFound() = assertTrue(hits("विठ्ठल").size >= 100)

    @Test fun longShortVowelVariantsMatch() {
        // "हरी" typed with long ी must find text written with short ि and vice versa
        assertEquals(hits("हरि").map { it.abhang.id }, hits("हरी").map { it.abhang.id })
        assertTrue(hits("हरी").isNotEmpty())
    }

    @Test fun multiWordAndPadaSearch() {
        val r = hits("सुंदर ते ध्यान")
        assertTrue(r.any { it.abhang.title.contains("सुंदर") && it.abhang.title.contains("ध्यान") })
    }

    @Test fun titleSearchPasaydan() = assertTrue(hits("पसायदान").any { it.abhang.title == "पसायदान" })

    @Test fun modernSpellingOfMhaneFound() = assertTrue(hits("तुका म्हणे").size >= 300)

    @Test fun latinQueryFindsDevanagari() {
        val r = hits("sundar")
        assertTrue(r.isNotEmpty())
        assertTrue(r.any { it.abhang.title.contains("सुंदर") })
        assertTrue(hits("sundar te dhyan").any { it.abhang.title.contains("सुंदर") && it.abhang.title.contains("ध्यान") })
    }

    @Test fun latinSpellingVariantsMatch() {
        val base = hits("vitthal").map { it.abhang.id }
        assertTrue(base.size >= 100)
        assertEquals(base, hits("viththal").map { it.abhang.id })
        assertEquals(base, hits("Vithal").map { it.abhang.id })
        assertTrue(hits("tuka mhane").size >= 300)
        assertTrue(hits("pasaydan").any { it.abhang.title == "पसायदान" })
    }

    @Test fun exactWordsOutrankSimilarSounding() {
        // "vitthal" must list real विठ्ठल titles first, and keep the near-homophone विटाळ out of the top results.
        val top = hits("vitthal").take(20)
        assertTrue(top.take(5).all { it.abhang.title.contains("विठ्ठल") })
        assertTrue(top.none { it.abhang.title.contains("विटाळ") })
    }

    @Test fun totalIsReportedWhenCapped() {
        val o = runBlocking { repo.searchWithTotal("tukaram", limit = 50) }
        assertEquals(50, o.results.size)
        assertTrue(o.total > 50)
    }

    @Test fun highlightsMarkMatchingWordsOnly() {
        val r = repo.highlights("विठ्ठल सोयरा सज्जन", "vitthal")
        assertEquals(listOf(0..5), r)
    }

    @Test fun noMatchReturnsEmpty() = assertTrue(hits("xyz").isEmpty())

    @Test fun categoryNumberLookup() {
        val r = runBlocking { repo.filterCategory(2, "२") }
        assertTrue(r.all { it.num == 2 })
        assertTrue(r.isNotEmpty())
    }

    @Test fun haripathHas28() = assertEquals(28, repo.count(8))

    @Test fun everyAbhangHasTitleAndBody() {
        for (c in repo.categories) for (a in repo.abhangsIn(c.id)) {
            assertTrue("id ${a.id}", a.title.isNotBlank() && a.body.isNotBlank())
        }
    }
}
