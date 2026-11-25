package com.abhang.app.data

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.setValue

/** The reader's own state, kept on the device: the abhang read last, and the favourites. Observable by Compose. */
class ReadingStore(context: Context) {
    private val prefs = context.getSharedPreferences("reading", Context.MODE_PRIVATE)

    var lastRead by mutableIntStateOf(prefs.getInt(KEY_LAST, 0))
        private set

    val favorites = mutableStateListOf<Int>().apply {
        prefs.getString(KEY_FAV, "").orEmpty().split(',').mapNotNullTo(this) { it.toIntOrNull() }
    }

    fun markRead(id: Int) {
        if (id == lastRead) return
        lastRead = id
        prefs.edit().putInt(KEY_LAST, id).apply()
    }

    fun toggleFavorite(id: Int) {
        if (!favorites.remove(id)) favorites.add(0, id)
        prefs.edit().putString(KEY_FAV, favorites.joinToString(",")).apply()
    }

    private companion object {
        const val KEY_LAST = "last"
        const val KEY_FAV = "fav"
    }
}
