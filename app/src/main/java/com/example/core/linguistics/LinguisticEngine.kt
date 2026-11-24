package com.example.core.linguistics

import com.example.core.database.KeyboardRepository
import com.example.core.preferences.KeyboardLanguage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.exp
import kotlin.math.hypot

data class SuggestionCandidate(
    val text: String,
    val language: KeyboardLanguage,
    val isAutocorrect: Boolean = false,
    val isLearned: Boolean = false,
    val isEmoji: Boolean = false,
    val score: Double = 0.0
)

typealias KeyCenter = com.example.core.ime.KeyCenter
typealias GlidePoint = com.example.core.ime.GlidePoint

private data class NGramStat(
    val count: Int,
    val lastUsedTimestamp: Long
) {
    fun score(nowMs: Long): Double {
        val ageHours = ((nowMs - lastUsedTimestamp).coerceAtLeast(0L)) / 3_600_000.0
        return count * 250.0 * (0.4 + 0.6 * exp(-ageHours / 168.0))
    }
}

private data class PendingToken(
    val word: String,
    val prevWord: String?,
    val prev2Word: String?,
    val language: KeyboardLanguage,
    val existedBefore: Boolean
)

class LinguisticEngine(
    private val repository: KeyboardRepository?,
    val saraikiConfig: SaraikiCharacterConfig = SaraikiCharacterConfig.defaultConfig()
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val tries: Map<KeyboardLanguage, PrefixTrie> = mapOf(
        KeyboardLanguage.SARAIKI to PrefixTrie(caseInsensitive = false),
        KeyboardLanguage.URDU to PrefixTrie(caseInsensitive = false),
        KeyboardLanguage.ENGLISH to PrefixTrie(caseInsensitive = true)
    )

    private val nGrams: Map<KeyboardLanguage, ConcurrentHashMap<String, ConcurrentHashMap<String, NGramStat>>> = mapOf(
        KeyboardLanguage.SARAIKI to ConcurrentHashMap(),
        KeyboardLanguage.URDU to ConcurrentHashMap(),
        KeyboardLanguage.ENGLISH to ConcurrentHashMap()
    )

    @Volatile
    private var pendingToken: PendingToken? = null

    @Volatile
    private var isHydrated: Boolean = false

    fun hydrateAsync() {
        if (repository == null) return
        scope.launch {
            hydrateBlocking()
        }
    }

    suspend fun hydrateBlocking() {
        val repo = repository ?: return
        repo.ensureSeeded()
        val baseEntries = repo.getAllBaseEntries()
        for (entry in baseEntries) {
            val lang = KeyboardLanguage.fromCode(entry.language)
            tries[lang]?.insertOrUpdateBase(entry.word, entry.baseFrequency)
        }
        val learnedEntries = repo.getAllLearnedWords()
        for (learned in learnedEntries) {
            val lang = KeyboardLanguage.fromCode(learned.language)
            tries[lang]?.recordLearned(
                word = learned.word,
                timestamp = learned.lastUsedTimestamp,
                isUserCoined = learned.isUserCoined,
                absoluteLearnedCount = learned.frequencyCount
            )
        }
        val storedNGrams = repo.getAllNGrams()
        for (ng in storedNGrams) {
            val lang = KeyboardLanguage.fromCode(ng.language)
            val langMap = nGrams[lang] ?: continue
            val nextMap = langMap.getOrPut(ng.contextPrefix) { ConcurrentHashMap() }
            nextMap[ng.nextWord] = NGramStat(ng.transitionCount, ng.lastUsedTimestamp)
        }
        isHydrated = true
    }

    fun seedForTesting(language: KeyboardLanguage, words: List<Pair<String, Int>>) {
        val trie = tries[language] ?: return
        for ((word, freq) in words) {
            trie.insertOrUpdateBase(word, freq)
        }
        isHydrated = true
    }

    /**
     * Captures a committed word immediately into RAM Trie & N-gram cache (so the very next sentence
     * can predict it) while holding an undoable pending guard against immediate Backspace typos.
     */
    fun onWordCommitted(
        rawWord: String,
        prevWord: String?,
        prev2Word: String?,
        activeLanguage: KeyboardLanguage,
        learnEnabled: Boolean,
        isIncognito: Boolean,
        confirmedBySuggestionTap: Boolean = false
    ) {
        val word = rawWord.trim()
        if (word.length < 2 || word.all { !it.isLetter() }) return
        if (!learnEnabled || isIncognito) return

        // Confirm any prior pending token first
        flushPendingToken()

        val targetLang = resolveWordLanguage(word, activeLanguage)
        val trie = tries[targetLang] ?: return
        val existed = trie.contains(word)

        val now = System.currentTimeMillis()
        // Update RAM immediately so next-sentence prediction works without restart
        trie.recordLearned(
            word = word,
            increment = 1,
            timestamp = now,
            isUserCoined = !existed
        )
        recordNGramInMemory(targetLang, prevWord, prev2Word, word, now)

        if (confirmedBySuggestionTap || existed) {
            // Known word or explicitly tapped suggestion -> persist immediately
            pendingToken = null
            persistAsync(word, prevWord, prev2Word, targetLang, isUserCoined = !existed)
        } else {
            // Newly coined word -> keep typo-protection guard active until next non-backspace action
            pendingToken = PendingToken(
                word = word,
                prevWord = prevWord,
                prev2Word = prev2Word,
                language = targetLang,
                existedBefore = false
            )
        }
    }

    /**
     * Called when user starts typing the next word or presses another non-backspace key, confirming
     * the pending newly coined word is NOT an accidental typo.
     */
    fun flushPendingToken() {
        val pending = pendingToken ?: return
        pendingToken = null
        persistAsync(
            word = pending.word,
            prevWord = pending.prevWord,
            prev2Word = pending.prev2Word,
            language = pending.language,
            isUserCoined = true
        )
    }

    /**
     * Smart Typo-Protection Filter: if user immediately hits Backspace after committing a newly coined
     * token, discard it from RAM so accidental typos are never permanently stored.
     */
    fun onImmediateBackspaceAfterCommit(): Boolean {
        val pending = pendingToken ?: return false
        pendingToken = null
        if (!pending.existedBefore) {
            tries[pending.language]?.removeWord(pending.word)
        }
        return true
    }

    private fun persistAsync(
        word: String,
        prevWord: String?,
        prev2Word: String?,
        language: KeyboardLanguage,
        isUserCoined: Boolean
    ) {
        val repo = repository ?: return
        scope.launch {
            repo.recordWordAndContext(word, prevWord, prev2Word, language, isUserCoined)
        }
    }

    fun forgetLearnedWord(word: String, language: KeyboardLanguage) {
        tries[language]?.removeWord(word)
        nGrams[language]?.values?.forEach { it.remove(word) }
        val repo = repository ?: return
        scope.launch {
            repo.forgetWord(word, language)
        }
    }

    fun clearAllLearnedInMemory() {
        tries.values.forEach { it.clearLearned() }
        nGrams.values.forEach { it.clear() }
        pendingToken = null
    }

    fun predictCandidates(
        composing: String,
        prevWord: String?,
        prev2Word: String?,
        activeLanguage: KeyboardLanguage,
        enabledLanguages: List<KeyboardLanguage>,
        multilingualEnabled: Boolean,
        autocorrectEnabled: Boolean,
        nextWordPredictionEnabled: Boolean,
        maxResults: Int = 4
    ): List<SuggestionCandidate> {
        val now = System.currentTimeMillis()
        val results = ArrayList<SuggestionCandidate>(maxResults + 2)

        if (composing.isBlank()) {
            if (!nextWordPredictionEnabled) return emptyList()
            val contextLang = if (!prevWord.isNullOrBlank()) {
                resolveWordLanguage(prevWord, activeLanguage)
            } else activeLanguage
            val langMap = nGrams[contextLang]
            val mergedNext = HashMap<String, Double>()

            if (!prev2Word.isNullOrBlank() && !prevWord.isNullOrBlank()) {
                langMap?.get("${prev2Word.trim()} ${prevWord.trim()}")?.forEach { (w, stat) ->
                    mergedNext[w] = (mergedNext[w] ?: 0.0) + stat.score(now) * 1.5
                }
            }
            if (!prevWord.isNullOrBlank()) {
                langMap?.get(prevWord.trim())?.forEach { (w, stat) ->
                    mergedNext[w] = (mergedNext[w] ?: 0.0) + stat.score(now)
                }
            }

            if (mergedNext.isNotEmpty()) {
                mergedNext.entries
                    .sortedByDescending { it.value }
                    .take(maxResults)
                    .forEach { (w, score) ->
                        val entry = tries[contextLang]?.getEntry(w)
                        results.add(
                            SuggestionCandidate(
                                text = w,
                                language = contextLang,
                                isLearned = (entry?.learnedFrequency ?: 0) > 0,
                                score = score
                            )
                        )
                    }
            } else {
                // Default starter words for the active language
                defaultStarterWords(activeLanguage).take(maxResults).forEach { w ->
                    results.add(SuggestionCandidate(text = w, language = activeLanguage, score = 100.0))
                }
            }
            return orderGboardStrip(results, composing = "")
        }

        // Determine candidate languages from script detection + multilingual settings
        val candidateLanguages = detectSearchLanguages(composing, activeLanguage, enabledLanguages, multilingualEnabled)
        val primaryLang = candidateLanguages.first()
        val seenWords = HashSet<String>()

        // 1. Exact/Prefix matches across candidate languages
        for ((langIdx, lang) in candidateLanguages.withIndex()) {
            val trie = tries[lang] ?: continue
            val weight = if (langIdx == 0) 1.0 else 0.55
            val matches = trie.prefixSearch(composing, limit = 6, nowMs = now)
            for (entry in matches) {
                if (seenWords.add(entry.word)) {
                    results.add(
                        SuggestionCandidate(
                            text = entry.word,
                            language = lang,
                            isLearned = entry.learnedFrequency > 0,
                            score = entry.frecencyScore(now) * weight
                        )
                    )
                }
            }
        }

        // 2. Fuzzy Damerau-Levenshtein autocorrect if composing is not an exact valid word
        // CRITICAL: Never replace valid Saraiki words (or words with Saraiki-specific graphemes) with Urdu
        val primaryEntry = tries[primaryLang]?.getEntry(composing)
        val hasSaraikiChar = saraikiConfig.isSaraikiSpecificWord(composing)
        val isKnownValid = primaryEntry != null || (hasSaraikiChar && activeLanguage == KeyboardLanguage.SARAIKI)

        if (!isKnownValid && autocorrectEnabled && composing.length >= 2) {
            val fuzzy = tries[primaryLang]?.fuzzySearch(composing, maxEditDistance = 2, limit = 3, nowMs = now).orEmpty()
            for ((entry, dist) in fuzzy) {
                if (seenWords.add(entry.word)) {
                    val penalty = if (dist == 1) 0.92 else 0.60
                    results.add(
                        SuggestionCandidate(
                            text = entry.word,
                            language = primaryLang,
                            isAutocorrect = dist == 1 && results.none { it.isAutocorrect },
                            isLearned = entry.learnedFrequency > 0,
                            score = entry.frecencyScore(now) * penalty
                        )
                    )
                }
            }
        }

        // 3. Always offer the user's literal typed text as an option if it's not exact top match
        if (seenWords.add(composing)) {
            results.add(
                SuggestionCandidate(
                    text = composing,
                    language = primaryLang,
                    isLearned = false,
                    score = 150.0
                )
            )
        }

        // 4. Contextual emoji suggestion
        lookupContextualEmoji(composing)?.let { emoji ->
            results.add(
                SuggestionCandidate(
                    text = emoji,
                    language = primaryLang,
                    isEmoji = true,
                    score = 50.0
                )
            )
        }

        val sorted = results.sortedByDescending { it.score }.take(maxResults)
        return orderGboardStrip(sorted, composing)
    }

    private fun orderGboardStrip(
        candidates: List<SuggestionCandidate>,
        composing: String
    ): List<SuggestionCandidate> {
        if (candidates.size < 2) return candidates
        // Put top-ranked non-emoji candidate in slot 1 (center) so UI mimics Gboard [left | CENTER | right]
        val emojis = candidates.filter { it.isEmoji }
        val words = candidates.filterNot { it.isEmoji }
        if (words.isEmpty()) return candidates
        val best = words.first()
        val rest = words.drop(1)
        val ordered = mutableListOf<SuggestionCandidate>()
        if (rest.isNotEmpty()) ordered.add(rest.first())
        ordered.add(best.copy(isAutocorrect = best.isAutocorrect || (composing.isNotEmpty() && best.text != composing)))
        ordered.addAll(rest.drop(1))
        ordered.addAll(emojis)
        return ordered
    }

    fun decodeGlidePath(
        points: List<GlidePoint>,
        keyCenters: List<KeyCenter>,
        activeLanguage: KeyboardLanguage
    ): List<SuggestionCandidate> {
        if (points.size < 3 || keyCenters.isEmpty()) return emptyList()
        val letterKeys = keyCenters.filter { it.label.length == 1 && it.label[0].isLetter() }
        if (letterKeys.isEmpty()) return emptyList()

        // Collapse path into ordered sequence of nearest keys
        val pathLabels = ArrayList<String>(16)
        for (pt in points) {
            val nearest = letterKeys.minByOrNull { hypot((it.x - pt.x).toDouble(), (it.y - pt.y).toDouble()) }
                ?: continue
            if (pathLabels.lastOrNull() != nearest.label) {
                pathLabels.add(nearest.label)
            }
        }
        if (pathLabels.size < 2) return emptyList()

        val firstChar = pathLabels.first()
        val lastChar = pathLabels.last()
        val trie = tries[activeLanguage] ?: return emptyList()
        val now = System.currentTimeMillis()
        val pathString = pathLabels.joinToString("")

        return trie.allWords()
            .asSequence()
            .filter { entry ->
                val w = if (activeLanguage == KeyboardLanguage.ENGLISH) entry.word.lowercase() else entry.word
                w.length >= 2 &&
                    w.startsWith(firstChar, ignoreCase = activeLanguage == KeyboardLanguage.ENGLISH) &&
                    w.endsWith(lastChar, ignoreCase = activeLanguage == KeyboardLanguage.ENGLISH)
            }
            .map { entry ->
                val w = if (activeLanguage == KeyboardLanguage.ENGLISH) entry.word.lowercase() else entry.word
                val overlap = w.count { ch -> pathString.contains(ch, ignoreCase = true) }.toDouble() / w.length
                val lenPenalty = kotlin.math.abs(w.length - pathLabels.size) * 35.0
                val score = entry.frecencyScore(now) * (0.4 + overlap) - lenPenalty
                SuggestionCandidate(
                    text = entry.word,
                    language = activeLanguage,
                    isLearned = entry.learnedFrequency > 0,
                    score = score
                )
            }
            .sortedByDescending { it.score }
            .take(3)
            .toList()
    }

    private fun recordNGramInMemory(
        language: KeyboardLanguage,
        prevWord: String?,
        prev2Word: String?,
        word: String,
        timestamp: Long
    ) {
        val langMap = nGrams[language] ?: return
        if (!prevWord.isNullOrBlank()) {
            val nextMap = langMap.getOrPut(prevWord.trim()) { ConcurrentHashMap() }
            val old = nextMap[word]
            nextMap[word] = NGramStat((old?.count ?: 0) + 1, timestamp)
            if (!prev2Word.isNullOrBlank()) {
                val biKey = "${prev2Word.trim()} ${prevWord.trim()}"
                val biMap = langMap.getOrPut(biKey) { ConcurrentHashMap() }
                val oldBi = biMap[word]
                biMap[word] = NGramStat((oldBi?.count ?: 0) + 1, timestamp)
            }
        }
    }

    fun resolveWordLanguage(word: String, activeLanguage: KeyboardLanguage): KeyboardLanguage {
        val hasLatin = word.any { it in 'A'..'Z' || it in 'a'..'z' }
        if (hasLatin) return KeyboardLanguage.ENGLISH
        if (saraikiConfig.isSaraikiSpecificWord(word)) return KeyboardLanguage.SARAIKI
        return if (activeLanguage == KeyboardLanguage.ENGLISH) KeyboardLanguage.SARAIKI else activeLanguage
    }

    private fun detectSearchLanguages(
        composing: String,
        activeLanguage: KeyboardLanguage,
        enabledLanguages: List<KeyboardLanguage>,
        multilingualEnabled: Boolean
    ): List<KeyboardLanguage> {
        val hasLatin = composing.any { it in 'A'..'Z' || it in 'a'..'z' }
        val hasSaraiki = saraikiConfig.isSaraikiSpecificWord(composing)
        if (!multilingualEnabled) return listOf(activeLanguage)

        return when {
            hasLatin -> listOf(KeyboardLanguage.ENGLISH)
            hasSaraiki -> listOf(KeyboardLanguage.SARAIKI)
            activeLanguage == KeyboardLanguage.ENGLISH -> {
                enabledLanguages.filter { it != KeyboardLanguage.ENGLISH } + KeyboardLanguage.ENGLISH
            }
            activeLanguage == KeyboardLanguage.SARAIKI -> {
                buildList {
                    add(KeyboardLanguage.SARAIKI)
                    if (KeyboardLanguage.URDU in enabledLanguages) add(KeyboardLanguage.URDU)
                }
            }
            else -> {
                buildList {
                    add(KeyboardLanguage.URDU)
                    if (KeyboardLanguage.SARAIKI in enabledLanguages) add(KeyboardLanguage.SARAIKI)
                }
            }
        }
    }

    private fun defaultStarterWords(language: KeyboardLanguage): List<String> = when (language) {
        KeyboardLanguage.SARAIKI -> listOf("سلام", "سرائیکی", "کیویں")
        KeyboardLanguage.URDU -> listOf("السلام", "اردو", "کیسے")
        KeyboardLanguage.ENGLISH -> listOf("Hello", "Thanks", "The")
    }

    private fun lookupContextualEmoji(word: String): String? {
        val lower = word.lowercase()
        return EMOJI_MAP[lower]
    }

    companion object {
        private val EMOJI_MAP = mapOf(
            "سلام" to "🤲",
            "خوش" to "😊",
            "پیار" to "❤️",
            "محبت" to "❤️",
            "دل" to "💖",
            "شکریہ" to "🙏",
            "مہربانی" to "🙏",
            "چاہ" to "☕",
            "روٹی" to "🫓",
            "بارش" to "🌧️",
            "ملتان" to "🕌",
            "پاکستان" to "🇵🇰",
            "happy" to "😊",
            "love" to "❤️",
            "thanks" to "🙏",
            "thank" to "🙏",
            "hello" to "👋",
            "good" to "👍",
            "great" to "✨",
            "awesome" to "🔥",
            "sorry" to "😔",
            "morning" to "☀️",
            "night" to "🌙",
            "tea" to "☕",
            "coffee" to "☕",
            "pakistan" to "🇵🇰"
        )
    }
}
