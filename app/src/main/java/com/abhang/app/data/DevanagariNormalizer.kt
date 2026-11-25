package com.abhang.app.data

import java.text.Normalizer

/**
 * Normalizes Devanagari text so search tolerates common spelling variants:
 * invisible joiners, danda marks, digits, nukta, chandrabindu/anusvara,
 * short/long i and u vowels, and ळ/ल.
 */
object DevanagariNormalizer {
    fun normalize(input: String): String {
        val nfc = Normalizer.normalize(input, Normalizer.Form.NFC)
        val sb = StringBuilder(nfc.length)
        for (ch in nfc) {
            when (ch) {
                '‌', '‍', '़', '।', '॥' -> Unit // joiners, nukta, dandas dropped
                'ँ' -> sb.append('ं')
                'ी' -> sb.append('ि')
                'ू' -> sb.append('ु')
                'ई' -> sb.append('इ')
                'ऊ' -> sb.append('उ')
                'ळ' -> sb.append('ल')
                else -> when {
                    ch in '०'..'९' || ch in '0'..'9' -> Unit // digits
                    ch.isLetter() || ch.isWhitespace() || ch in 'ऀ'..'ॿ' -> sb.append(ch.lowercaseChar())
                    else -> sb.append(' ')
                }
            }
        }
        return sb.toString().trim().replace(Regex("\\s+"), " ")
    }
}
