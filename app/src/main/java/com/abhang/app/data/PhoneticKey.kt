package com.abhang.app.data

/**
 * Lets people search Devanagari text by typing Marathi in Latin letters ("sundar" finds "सुंदर").
 *
 * Both sides are reduced to the same lossy phonetic key, so spelling variants collapse:
 * Devanagari text is romanized, and a Latin query is cleaned up the same way. The key
 * drops the inherent "a" (schwa), merges long/short vowels, aspirated/plain consonants
 * (th/t, dh/d, kh/k...), retroflex/dental, s/sh, v/w and doubled letters, so
 * "vitthal", "vithal", "viththal" and "विठ्ठल" all give the same key.
 */
object PhoneticKey {
    fun hasDevanagari(s: String) = s.any { it in 'ऀ'..'ॿ' }

    /** Phonetic key of any text (Devanagari, Latin or mixed). Words stay separated by single spaces. */
    fun of(input: String): String = key(romanize(input))

    private fun romanize(input: String): String {
        val sb = StringBuilder(input.length * 2)
        for (ch in input) {
            when (ch) {
                // consonants (inherent "a" is dropped by the key, so only the consonant is emitted)
                'क' -> sb.append("k"); 'ख' -> sb.append("kh"); 'ग' -> sb.append("g"); 'घ' -> sb.append("gh")
                'ङ', 'ञ', 'ण', 'न' -> sb.append("n")
                'च' -> sb.append("ch"); 'छ' -> sb.append("chh"); 'ज' -> sb.append("j"); 'झ' -> sb.append("jh")
                'ट', 'त' -> sb.append("t"); 'ठ', 'थ' -> sb.append("th")
                'ड', 'द' -> sb.append("d"); 'ढ', 'ध' -> sb.append("dh")
                'प' -> sb.append("p"); 'फ' -> sb.append("ph"); 'ब' -> sb.append("b"); 'भ' -> sb.append("bh")
                'म' -> sb.append("m"); 'य' -> sb.append("y"); 'र' -> sb.append("r")
                'ल', 'ळ' -> sb.append("l"); 'व' -> sb.append("v")
                'श', 'ष' -> sb.append("sh"); 'स' -> sb.append("s"); 'ह' -> sb.append("h")
                // independent vowels
                'अ', 'आ', 'ऑ' -> sb.append("a"); 'इ', 'ई' -> sb.append("i"); 'उ', 'ऊ' -> sb.append("u")
                'ऋ' -> sb.append("ru"); 'ए', 'ऍ' -> sb.append("e"); 'ऐ' -> sb.append("ai")
                'ओ' -> sb.append("o"); 'औ' -> sb.append("au")
                // vowel signs
                'ा' -> sb.append("a"); 'ि', 'ी' -> sb.append("i"); 'ु', 'ू' -> sb.append("u")
                'ृ' -> sb.append("ru"); 'े', 'ॅ' -> sb.append("e"); 'ै' -> sb.append("ai")
                'ो', 'ॉ' -> sb.append("o"); 'ौ' -> sb.append("au")
                'ं', 'ँ' -> sb.append("n")
                'ः', '्', '़', '‌', '‍' -> Unit // visarga, virama, nukta, joiners
                else -> if (ch in 'a'..'z' || ch in 'A'..'Z') sb.append(ch.lowercaseChar()) else sb.append(' ')
            }
        }
        return sb.toString()
    }

    private val fineDoubled = Regex("(th|dh|kh|gh|ch|ph|bh|jh|.)\\1+")

    /**
     * A finer key for ranking only: like [of] but it keeps aspirates (th, dh...) so that
     * "vitthal" / "विठ्ठल" agree with each other and not with the near-homophone "विटाळ".
     * Meant for a single word; the result is not used to find candidates, only to order them.
     */
    fun fine(word: String): String =
        romanize(word).lowercase()
            .replace("ee", "i").replace("oo", "u")
            .replace('w', 'v').replace('z', 'j').replace('f', 'p').replace('q', 'k')
            .replace("a", "").replace(" ", "")
            .replace(fineDoubled, "$1")

    private val aspirated = Regex("([kgcjtdpb])h")
    private val nasalBeforeConsonant = Regex("[nm](?=[b-df-hj-np-tv-z])")
    private val doubled = Regex("(.)\\1+")
    private val spaces = Regex("\\s+")

    private fun key(latin: String): String {
        var t = latin.replace("dny", "jn").replace("gny", "jn")
            .replace("ee", "i").replace("oo", "u")
            .replace("chh", "c").replace("ch", "c")
            .replace(aspirated, "$1")
            .replace("sh", "s")
            .replace('w', 'v').replace('z', 'j').replace('f', 'p').replace('q', 'k')
            .replace("x", "ks")
            .replace("a", "")
        t = t.replace(nasalBeforeConsonant, "n")
        t = t.replace(spaces, " ").trim()
        return t.replace(doubled, "$1")
    }
}
