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

    /**
     * One query word: matched against the Devanagari index if typed in Devanagari, else the phonetic one.
     *
     * Matching is tiered (see [cost]): an exact substring costs 0; otherwise [FuzzyMatcher] looks for the
     * word with spaces ignored ("सुंदरते" finds "सुंदर तें") and, for longer words, with a typo or two.
     */
    private class Token(val dev: String?, val phonetic: String?, val fine: String?) {
        private val key = dev ?: phonetic!!
        private val fuzzy = FuzzyMatcher.of(key)

        private fun norm(text: String) = if (dev != null) DevanagariNormalizer.normalize(text) else PhoneticKey.of(text)

        /** 0 = exact substring, 1 + typos = found only with spaces ignored and/or typos, null = no match. */
        fun cost(devHay: () -> String, phoneticHay: () -> String): Int? {
            val hay = if (dev != null) devHay() else phoneticHay()
            if (hay.contains(key)) return 0
            val e = fuzzy?.minErrors(hay) ?: return null
            return if (e < 0) null else 1 + e
        }

        /** Does this single word contain the token exactly (after normalization)? */
        fun matchesWord(word: String) = norm(word).contains(key)

        /** Is this word an exact (not just similar-sounding) match for the token? */
        fun exactWord(word: String) =
            if (dev != null) DevanagariNormalizer.normalize(word) == dev else PhoneticKey.fine(word) == fine

        /** Indices of the [words] that the best fuzzy (space-ignoring / typo) occurrence of the token runs across. */
        fun fuzzyWords(words: List<String>): Set<Int> {
            val f = fuzzy ?: return emptySet()
            val compact = StringBuilder()
            val owner = ArrayList<Int>() // word index of each char in `compact`
            words.forEachIndexed { i, w -> norm(w).forEach { c -> if (c != ' ') { compact.append(c); owner.add(i) } } }
            val text = compact.toString()
            val best = f.minErrors(text)
            if (best < 0) return emptySet()
            val out = HashSet<Int>()
            f.scan(text) { end, e ->
                if (e == best) {
                    // Shortest plausible span; overlapping a word by one char is enough to mark it.
                    val start = (end - key.length + 1 + e).coerceIn(0, end)
                    for (j in start..end) out.add(owner[j])
                }
                false
            }
            return out
        }
    }

    /** Sum of the tokens' match costs (every token must match), or null if any token is missing. */
    private fun List<Token>.cost(devHay: () -> String, phoneticHay: () -> String): Int? {
        var total = 0
        for (t in this) total += t.cost(devHay, phoneticHay) ?: return null
        return total
    }

    /** [cost] against an arbitrary piece of text, normalized on demand. */
    private fun List<Token>.costIn(text: String): Int? {
        val dev by lazy { DevanagariNormalizer.normalize(text) }
        val ph by lazy { PhoneticKey.of(text) }
        return cost({ dev }, { ph })
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
        // Exact matches first, then space-insensitive / typo matches; corpus order within a tier (stable sort).
        all.mapNotNull { a -> tokens.cost({ titleIndex.getValue(a.id) }, { phoneticTitleIndex.getValue(a.id) })?.let { a to it } }
            .sortedBy { it.second }.map { it.first }
    }

    /** Ranked results (best first) plus the total number of matches before the [limit] cut. */
    suspend fun searchWithTotal(query: String, limit: Int = 300): SearchOutcome = withContext(Dispatchers.Default) {
        val tokens = tokenize(query)
        if (tokens.isEmpty()) return@withContext SearchOutcome(emptyList(), 0)
        val hits = abhangs.mapNotNull { a ->
            tokens.cost({ searchIndex.getValue(a.id) }, { phoneticSearchIndex.getValue(a.id) })?.let { a to it }
        }
        val scoreBody = hits.size <= 1500 // exact-word scoring of long bodies is skipped for very broad queries
        // Ranked like Algolia: fewest typos first (exact, then spaces ignored, then 1 and 2 typos), then the
        // relevance score. Scores are computed once per hit; the sort is stable, so ties keep corpus order.
        val ranked = hits.map { (a, cost) -> Triple(a, cost, score(a, tokens, scoreBody)) }
            .sortedWith(compareBy<Triple<Abhang, Int, Int>> { it.second }.thenByDescending { it.third })
            .map { it.first }
        val shown = ranked.take(limit).map { a -> SearchResult(a, category(a.categoryId).name, snippet(a.body, tokens).trim()) }
        SearchOutcome(shown, ranked.size)
    }

    /** The body line that matches best (fewest typos), else one matching the first word, else the first line. */
    private fun snippet(body: String, tokens: List<Token>): String {
        val lines = body.lines()
        var best: String? = null
        var bestCost = Int.MAX_VALUE
        for (l in lines) {
            val c = tokens.costIn(l) ?: continue
            if (c < bestCost) { best = l; bestCost = c; if (c == 0) break }
        }
        return best ?: lines.firstOrNull { tokens.take(1).costIn(it) != null } ?: lines.first()
    }

    suspend fun search(query: String, limit: Int = 300): List<SearchResult> = searchWithTotal(query, limit).results

    /**
     * Higher is better: a title that contains every word beats a body-only match, and an exact
     * word (not just a similar-sounding one) in the title beats everything else.
     */
    private fun score(a: Abhang, tokens: List<Token>, scoreBody: Boolean): Int {
        var s = 0
        val titleWords = a.title.split(Regex("\\s+"))
        if (tokens.cost({ titleIndex.getValue(a.id) }, { phoneticTitleIndex.getValue(a.id) }) != null) s += 100
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
        val words = Regex("[^\\s।॥]+").findAll(text).toList()
        val hit = BooleanArray(words.size)
        for (t in tokens) {
            var exact = false
            words.forEachIndexed { i, w -> if (t.matchesWord(w.value)) { hit[i] = true; exact = true } }
            // No single word contains it as typed: mark the words a space-ignoring / typo match runs across.
            if (!exact) t.fuzzyWords(words.map { it.value }).forEach { hit[it] = true }
        }
        return words.indices.filter { hit[it] }.map { words[it].range }
    }
}
