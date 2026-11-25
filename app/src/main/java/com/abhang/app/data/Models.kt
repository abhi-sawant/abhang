package com.abhang.app.data

data class Category(val id: Int, val name: String, val description: String)

data class Abhang(
    val id: Int,
    val categoryId: Int,
    val title: String,
    val author: String,
    val body: String,
    val source: String,
    val num: Int = 0,
)

data class SearchResult(val abhang: Abhang, val categoryName: String, val snippet: String)

data class SearchOutcome(val results: List<SearchResult>, val total: Int)
