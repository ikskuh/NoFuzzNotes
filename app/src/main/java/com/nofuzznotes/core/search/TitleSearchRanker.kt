package com.nofuzznotes.core.search

object TitleSearchRanker {
    private const val NoMatch = Int.MAX_VALUE

    // Rank titles with exact matches first because list search must prefer literal user intent over fuzzy recovery.
    fun rank(query: String, title: String): Int? {
        val normalizedQuery = query.lowercase()
        val normalizedTitle = title.lowercase()
        if (normalizedTitle.contains(normalizedQuery)) return exactRank(normalizedQuery, normalizedTitle)
        val fuzzyDistance = bestFuzzyDistance(normalizedQuery, normalizedTitle)
        return fuzzyDistance?.let { 10_000 + it * 100 + kotlin.math.abs(normalizedTitle.length - normalizedQuery.length) }
    }

    // Put earlier and shorter exact matches first because deterministic list order needs a simple tie-breaker.
    private fun exactRank(query: String, title: String): Int {
        assert(title.contains(query))
        return title.indexOf(query) * 10 + kotlin.math.abs(title.length - query.length)
    }

    // Compare plausible substrings because list search should find a fuzzy title occurrence, not only whole-title typos.
    private fun bestFuzzyDistance(query: String, title: String): Int? {
        if (query.isEmpty()) return 0
        if (title.isEmpty()) return null
        val allowed = allowedDistance(query.length)
        var best = NoMatch
        val minimumLength = (query.length - allowed).coerceAtLeast(1)
        val maximumLength = (query.length + allowed).coerceAtMost(title.length)
        for (start in title.indices) {
            for (length in minimumLength..maximumLength) {
                val end = start + length
                if (end <= title.length) {
                    val distance = damerauLevenshtein(query, title.substring(start, end))
                    if (distance < best) best = distance
                }
            }
        }
        return if (best <= allowed) best else null
    }

    // Limit fuzziness because acronym-like unrelated abbreviations must not become matches.
    private fun allowedDistance(length: Int): Int {
        assert(length >= 0)
        return when {
            length <= 2 -> 0
            length <= 5 -> 1
            else -> 2
        }
    }

    // Count adjacent swaps as one edit because character flips are explicitly supported by fuzzy search.
    private fun damerauLevenshtein(left: String, right: String): Int {
        val distances = Array(left.length + 1) { row -> IntArray(right.length + 1) { column -> row + column } }
        for (row in 0..left.length) distances[row][0] = row
        for (column in 0..right.length) distances[0][column] = column
        for (row in 1..left.length) {
            for (column in 1..right.length) {
                val substitutionCost = if (left[row - 1] == right[column - 1]) 0 else 1
                var value = minOf(
                    distances[row - 1][column] + 1,
                    distances[row][column - 1] + 1,
                    distances[row - 1][column - 1] + substitutionCost,
                )
                if (row > 1 && column > 1 && left[row - 1] == right[column - 2] && left[row - 2] == right[column - 1]) {
                    value = minOf(value, distances[row - 2][column - 2] + 1)
                }
                distances[row][column] = value
            }
        }
        return distances[left.length][right.length]
    }
}
