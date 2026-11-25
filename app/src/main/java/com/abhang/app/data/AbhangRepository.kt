package com.abhang.app.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

/** Loads the bundled JSON once and serves everything from memory (fully offline). Construct off the main thread. */
class AbhangRepository(json: String) {
    val categories: List<Category>
    private val abhangs: List<Abhang>
    private val byId: Map<Int, Abhang>
    private val byCategory: Map<Int, List<Abhang>>

    init {
        val root = JSONObject(json)
        categories = root.getJSONArray("categories").let { arr ->
            List(arr.length()) {
                val o = arr.getJSONObject(it)
                Category(o.getInt("id"), o.getString("name"), o.getString("description"))
            }
        }
        abhangs = root.getJSONArray("abhangs").let { arr ->
            List(arr.length()) {
                val o = arr.getJSONObject(it)
                Abhang(
                    o.getInt("id"), o.getInt("categoryId"), o.getString("title"),
                    o.getString("author"), o.getString("body"), o.optString("source"), o.optInt("num", 0),
                )
            }
        }
        byId = abhangs.associateBy { it.id }
        byCategory = abhangs.groupBy { it.categoryId }
    }

    companion object {
        fun load(context: Context) =
            AbhangRepository(context.assets.open("abhangs.json").bufferedReader(Charsets.UTF_8).use { it.readText() })
    }

    // Normalized text is built lazily on first use (search / filter), never at startup.
    private val categoryNames by lazy { categories.associate { it.id to it.name } }
    private val searchIndex by lazy {
        abhangs.associate {
            it.id to DevanagariNormalizer.normalize("${it.title} ${it.author} ${categoryNames[it.categoryId]} ${it.body}")
        }
    }
    private val titleIndex by lazy {
        abhangs.associate { it.id to DevanagariNormalizer.normalize("${it.title} ${it.author}") }
    }
    // Phonetic keys let Latin-typed queries ("sundar") match Devanagari text ("सुंदर").
    private val phoneticSearchIndex by lazy {
        abhangs.associate {
            it.id to PhoneticKey.of("${it.title} ${it.author} ${categoryNames[it.categoryId]} ${it.body}")
        }
    }
    private val phoneticTitleIndex by lazy {
        abhangs.associate { it.id to PhoneticKey.of("${it.title} ${it.author}") }
    }

    /** One query word: matched against the Devanagari index if typed in Devanagari, else the phonetic one. */
    private class Token(val dev: String?, val phonetic: String?, val fine: String?) {
        fun matches(devHay: () -> String, phoneticHay: () -> String) =
            if (dev != null) devHay().contains(dev) else phoneticHay().contains(phonetic!!)

        /** Does this single word match the token (substring, same rules as [matches])? */
        fun matchesWord(word: String) =
            if (dev != null) DevanagariNormalizer.normalize(word).contains(dev) else PhoneticKey.of(word).contains(phonetic!!)

        /** Is this word an exact (not just similar-sounding) match for the token? */
        fun exactWord(word: String) =
            if (dev != null) DevanagariNormalizer.normalize(word) == dev else PhoneticKey.fine(word) == fine
    }

    private fun tokenize(query: String): List<Token> =
        query.split(Regex("\\s+")).mapNotNull { w ->
            if (PhoneticKey.hasDevanagari(w)) {
                DevanagariNormalizer.normalize(w).takeIf { it.isNotEmpty() }?.let { Token(it, null, null) }
            } else {
                PhoneticKey.of(w).takeIf { it.isNotEmpty() }?.let { Token(null, it, PhoneticKey.fine(w)) }
            }
        }

    fun category(id: Int) = categories.first { it.id == id }
    fun abhangsIn(categoryId: Int): List<Abhang> = byCategory[categoryId].orEmpty()
    fun abhang(id: Int): Abhang = byId.getValue(id)
    fun abhangOrNull(id: Int): Abhang? = byId[id]
    fun count(categoryId: Int) = byCategory[categoryId]?.size ?: 0

    /** Builds every lazy index. Call once off the main thread after loading so the first search is instant. */
    fun warmUp() {
        searchIndex.size; titleIndex.size; phoneticSearchIndex.size; phoneticTitleIndex.size
    }

    /** Parses a query made only of digits (Devanagari or ASCII), e.g. "१२३" -> 123; null otherwise. */
    private fun numberQuery(q: String): Int? {
        val t = q.trim()
        if (t.isEmpty() || t.length > 5) return null
        val digits = t.map { c -> when (c) { in '०'..'९' -> c - '०'; in '0'..'9' -> c - '0'; else -> return null } }
        return digits.fold(0) { acc, d -> acc * 10 + d }
    }

    /** Filter the abhangs of one category by title/author text or by abhang number. */
    suspend fun filterCategory(categoryId: Int, query: String): List<Abhang> = withContext(Dispatchers.Default) {
        val all = abhangsIn(categoryId)
        if (query.isBlank()) return@withContext all
        numberQuery(query)?.let { n -> return@withContext all.filter { it.num == n } }
        val tokens = tokenize(query)
        if (tokens.isEmpty()) return@withContext all
        all.filter { a -> tokens.all { it.matches({ titleIndex.getValue(a.id) }, { phoneticTitleIndex.getValue(a.id) }) } }
    }

    /** Ranked results (best first) plus the total number of matches before the [limit] cut. */
    suspend fun searchWithTotal(query: String, limit: Int = 300): SearchOutcome = withContext(Dispatchers.Default) {
        val tokens = tokenize(query)
        if (tokens.isEmpty()) return@withContext SearchOutcome(emptyList(), 0)
        val hits = abhangs.filter { a ->
            tokens.all { it.matches({ searchIndex.getValue(a.id) }, { phoneticSearchIndex.getValue(a.id) }) }
        }
        val scoreBody = hits.size <= 1500 // exact-word scoring of long bodies is skipped for very broad queries
        // Scores are computed once per hit; the sort is stable, so equal scores keep corpus order.
        val ranked = hits.map { it to score(it, tokens, scoreBody) }.sortedByDescending { it.second }.map { it.first }
        val shown = ranked.take(limit).map { a ->
            val line = a.body.lines().firstOrNull { l -> tokens.all { it.matchesWord(l) || it.matches({ DevanagariNormalizer.normalize(l) }, { PhoneticKey.of(l) }) } }
                ?: a.body.lines().firstOrNull { l -> tokens.first().matches({ DevanagariNormalizer.normalize(l) }, { PhoneticKey.of(l) }) }
                ?: a.body.lines().first()
            SearchResult(a, category(a.categoryId).name, line.trim())
        }
        SearchOutcome(shown, ranked.size)
    }

    suspend fun search(query: String, limit: Int = 300): List<SearchResult> = searchWithTotal(query, limit).results

    /**
     * Higher is better: a title that contains every word beats a body-only match, and an exact
     * word (not just a similar-sounding one) in the title beats everything else.
     */
    private fun score(a: Abhang, tokens: List<Token>, scoreBody: Boolean): Int {
        var s = 0
        val titleWords = a.title.split(Regex("\\s+"))
        if (tokens.all { it.matches({ titleIndex.getValue(a.id) }, { phoneticTitleIndex.getValue(a.id) }) }) s += 100
        if (tokens.all { t -> titleWords.any { t.exactWord(it) } }) s += 200
        else if (scoreBody) {
            val bodyWords = a.body.split(Regex("\\s+"))
            if (tokens.all { t -> bodyWords.any { t.exactWord(it) } }) s += 50
        }
        return s
    }

    /** Character ranges of the words in [text] that match [query]; used to highlight search results. */
    fun highlights(text: String, query: String): List<IntRange> {
        val tokens = tokenize(query)
        if (tokens.isEmpty()) return emptyList()
        return Regex("[^\\s।॥]+").findAll(text).filter { m -> tokens.any { it.matchesWord(m.value) } }.map { it.range }.toList()
    }
}
