package com.abhang.app.data

/**
 * Approximate substring matching (Bitap / Wu–Manber, the algorithm behind `agrep` and Fuse.js).
 *
 * Finds [pattern] anywhere in a text with at most [maxErrors] edits (insert, delete, substitute),
 * and **ignores spaces in the text**, so "सुंदरते" is found in "सुंदर तें" and "हरिमुखे" in "हरि मुखे".
 * One pass over the text costs O(n · (maxErrors + 1)) bit operations, which is fast enough to scan
 * the whole corpus on a phone. Patterns longer than 63 chars are not supported ([of] returns null).
 */
class FuzzyMatcher private constructor(private val pattern: String, val maxErrors: Int) {
    private val m = pattern.length
    private val accept = 1L shl (m - 1)
    // Bit i of masks[c - base] is set when pattern[i] == c. Pattern chars all fall in one 128-wide block
    // (Devanagari U+0900.. or ASCII), which [of] guarantees.
    private val base = pattern[0].code and 0xFF80
    private val masks = LongArray(128).also { arr -> pattern.forEachIndexed { i, c -> arr[c.code - base] = arr[c.code - base] or (1L shl i) } }

    private fun mask(c: Char): Long {
        val i = c.code - base
        return if (i in 0..127) masks[i] else 0L
    }

    /** Fewest edits with which the pattern occurs in [text] (spaces in the text skipped), or -1 if more than [maxErrors]. */
    fun minErrors(text: String): Int {
        var best = -1
        scan(text) { _, e -> if (best < 0 || e < best) best = e; e == 0 }
        return best
    }

    /**
     * Calls [onMatch] with the index in [text] of the last char of each occurrence and its edit count
     * (the smallest for that end position). Returning true from [onMatch] stops the scan.
     */
    fun scan(text: String, onMatch: (end: Int, errors: Int) -> Boolean) {
        val k = maxErrors
        val r = LongArray(k + 1) { (1L shl it) - 1 } // r[d] bit i: pattern[0..i] ends here with <= d edits
        for (idx in text.indices) {
            val c = text[idx]
            if (c == ' ') continue
            val b = mask(c)
            var prevOld = r[0]
            r[0] = ((r[0] shl 1) or 1L) and b
            for (d in 1..k) {
                val old = r[d]
                r[d] = (((old shl 1) or 1L) and b) or // match
                    prevOld or                          // extra char in text
                    ((prevOld shl 1) or 1L) or          // substitution
                    ((r[d - 1] shl 1) or 1L)            // pattern char missing from text
                prevOld = old
            }
            for (d in 0..k) if (r[d] and accept != 0L) {
                if (onMatch(idx, d)) return
                break
            }
        }
    }

    companion object {
        /**
         * Shortest pattern matched fuzzily at all. Shorter ones ("हरि", or "ksyj" from a Latin "xyz") turn up
         * across word boundaries all over the corpus once spaces are ignored, so they stay exact-only.
         */
        const val MIN_LENGTH = 5

        /**
         * Typo budget by length, as in Algolia's typo tolerance but scaled for Devanagari, where one
         * akshara is usually 2–3 chars (consonant + virama/matra/anusvara): 0 typos below 5 chars,
         * 1 typo from 5, 2 typos from 9. Phonetic keys drop most vowels, so the same scale fits them.
         */
        fun typosFor(length: Int) = when {
            length >= 9 -> 2
            length >= 5 -> 1
            else -> 0
        }

        /** A matcher for one normalized query word, or null if it is too short or too long for fuzzy matching. */
        fun of(pattern: String): FuzzyMatcher? {
            if (pattern.length !in MIN_LENGTH..63 || ' ' in pattern) return null
            val block = pattern[0].code and 0xFF80
            if (pattern.any { (it.code and 0xFF80) != block }) return null
            return FuzzyMatcher(pattern, typosFor(pattern.length))
        }
    }
}
