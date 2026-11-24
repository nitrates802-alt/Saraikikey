package com.example.core.linguistics

import java.util.concurrent.locks.ReentrantReadWriteLock
import kotlin.concurrent.read
import kotlin.concurrent.write
import kotlin.math.exp
import kotlin.math.min

data class TrieWordEntry(
    val word: String,
    val baseFrequency: Int = 0,
    val learnedFrequency: Int = 0,
    val lastUsedTimestamp: Long = 0L,
    val isUserCoined: Boolean = false
) {
    fun frecencyScore(nowMs: Long = System.currentTimeMillis()): Double {
        val ageHours = ((nowMs - lastUsedTimestamp).coerceAtLeast(0L)) / 3_600_000.0
        val recencyDecay = if (lastUsedTimestamp > 0L) exp(-ageHours / 168.0) else 0.0
        val learnedBoost = learnedFrequency * 220.0 * (0.35 + 0.65 * recencyDecay)
        val coinedBoost = if (isUserCoined && learnedFrequency > 0) 260.0 else 0.0
        return baseFrequency.toDouble() + learnedBoost + coinedBoost
    }
}

class PrefixTrie(private val caseInsensitive: Boolean = false) {

    private class Node {
        val children = HashMap<Char, Node>(4)
        var entry: TrieWordEntry? = null
    }

    private val root = Node()
    private val allEntries = HashMap<String, TrieWordEntry>(256)
    private val lock = ReentrantReadWriteLock()

    private fun normalizeKey(word: String): String =
        if (caseInsensitive) word.lowercase() else word

    fun insertOrUpdateBase(word: String, baseFrequency: Int) = lock.write {
        val key = normalizeKey(word)
        val existing = allEntries[key]
        val updated = if (existing != null) {
            existing.copy(baseFrequency = maxOf(existing.baseFrequency, baseFrequency))
        } else {
            TrieWordEntry(word = word, baseFrequency = baseFrequency)
        }
        putInternal(key, updated)
    }

    fun recordLearned(
        word: String,
        increment: Int = 1,
        timestamp: Long = System.currentTimeMillis(),
        isUserCoined: Boolean = true,
        absoluteLearnedCount: Int? = null
    ) = lock.write {
        val key = normalizeKey(word)
        val existing = allEntries[key]
        val updated = if (existing != null) {
            TrieWordEntry(
                word = if (existing.isUserCoined) word else existing.word,
                baseFrequency = existing.baseFrequency,
                learnedFrequency = absoluteLearnedCount ?: (existing.learnedFrequency + increment),
                lastUsedTimestamp = maxOf(existing.lastUsedTimestamp, timestamp),
                isUserCoined = existing.isUserCoined || isUserCoined
            )
        } else {
            TrieWordEntry(
                word = word,
                baseFrequency = 0,
                learnedFrequency = absoluteLearnedCount ?: increment,
                lastUsedTimestamp = timestamp,
                isUserCoined = isUserCoined
            )
        }
        putInternal(key, updated)
    }

    fun removeWord(word: String) = lock.write {
        val key = normalizeKey(word)
        val existing = allEntries[key] ?: return@write
        if (existing.baseFrequency > 0) {
            putInternal(
                key,
                existing.copy(learnedFrequency = 0, lastUsedTimestamp = 0L, isUserCoined = false)
            )
        } else {
            allEntries.remove(key)
            var current = root
            for (ch in key) {
                current = current.children[ch] ?: return@write
            }
            current.entry = null
        }
    }

    fun clearLearned() = lock.write {
        val iter = allEntries.entries.iterator()
        while (iter.hasNext()) {
            val (key, entry) = iter.next()
            if (entry.baseFrequency > 0) {
                val reset = entry.copy(learnedFrequency = 0, lastUsedTimestamp = 0L, isUserCoined = false)
                allEntries[key] = reset
                findNode(key)?.entry = reset
            } else {
                iter.remove()
                findNode(key)?.entry = null
            }
        }
    }

    fun contains(word: String): Boolean = lock.read {
        allEntries.containsKey(normalizeKey(word))
    }

    fun getEntry(word: String): TrieWordEntry? = lock.read {
        allEntries[normalizeKey(word)]
    }

    fun allWords(): List<TrieWordEntry> = lock.read {
        allEntries.values.toList()
    }

    fun prefixSearch(prefix: String, limit: Int = 8, nowMs: Long = System.currentTimeMillis()): List<TrieWordEntry> = lock.read {
        if (prefix.isEmpty()) return@read emptyList()
        val key = normalizeKey(prefix)
        val startNode = findNode(key) ?: return@read emptyList()
        val collected = ArrayList<TrieWordEntry>(32)
        collectSubtree(startNode, collected, maxCollect = 160)
        return@read collected
            .sortedWith(
                compareByDescending<TrieWordEntry> { normalizeKey(it.word) == key }
                    .thenByDescending { it.frecencyScore(nowMs) }
            )
            .take(limit)
    }

    fun fuzzySearch(
        query: String,
        maxEditDistance: Int = 2,
        limit: Int = 5,
        nowMs: Long = System.currentTimeMillis()
    ): List<Pair<TrieWordEntry, Int>> = lock.read {
        if (query.length < 2) return@read emptyList()
        val q = normalizeKey(query)
        val results = ArrayList<Pair<TrieWordEntry, Int>>()
        for ((key, entry) in allEntries) {
            if (kotlin.math.abs(key.length - q.length) > maxEditDistance) continue
            // Quick first-character or length filter for speed
            val dist = damerauLevenshtein(q, key, maxEditDistance)
            if (dist in 1..maxEditDistance) {
                results.add(entry to dist)
            }
        }
        return@read results
            .sortedWith(
                compareBy<Pair<TrieWordEntry, Int>> { it.second }
                    .thenByDescending { it.first.frecencyScore(nowMs) }
            )
            .take(limit)
    }

    private fun putInternal(key: String, entry: TrieWordEntry) {
        allEntries[key] = entry
        var current = root
        for (ch in key) {
            current = current.children.getOrPut(ch) { Node() }
        }
        current.entry = entry
    }

    private fun findNode(key: String): Node? {
        var current = root
        for (ch in key) {
            current = current.children[ch] ?: return null
        }
        return current
    }

    private fun collectSubtree(node: Node, out: MutableList<TrieWordEntry>, maxCollect: Int) {
        if (out.size >= maxCollect) return
        node.entry?.let { out.add(it) }
        for (child in node.children.values) {
            collectSubtree(child, out, maxCollect)
            if (out.size >= maxCollect) return
        }
    }

    companion object {
        fun damerauLevenshtein(a: String, b: String, maxDist: Int = 2): Int {
            val lenA = a.length
            val lenB = b.length
            if (kotlin.math.abs(lenA - lenB) > maxDist) return maxDist + 1
            val dp = Array(lenA + 1) { IntArray(lenB + 1) }
            for (i in 0..lenA) dp[i][0] = i
            for (j in 0..lenB) dp[0][j] = j
            for (i in 1..lenA) {
                var rowMin = Int.MAX_VALUE
                for (j in 1..lenB) {
                    val cost = if (a[i - 1] == b[j - 1]) 0 else 1
                    var best = min(
                        min(dp[i - 1][j] + 1, dp[i][j - 1] + 1),
                        dp[i - 1][j - 1] + cost
                    )
                    if (i > 1 && j > 1 && a[i - 1] == b[j - 2] && a[i - 2] == b[j - 1]) {
                        best = min(best, dp[i - 2][j - 2] + 1)
                    }
                    dp[i][j] = best
                    if (best < rowMin) rowMin = best
                }
                if (rowMin > maxDist) return maxDist + 1
            }
            return dp[lenA][lenB]
        }
    }
}
