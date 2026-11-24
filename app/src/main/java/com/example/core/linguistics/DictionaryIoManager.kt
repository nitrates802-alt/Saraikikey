package com.example.core.linguistics

import com.example.core.database.LearnedWordEntity
import com.example.core.preferences.KeyboardLanguage

object DictionaryIoManager {

    fun exportAsJson(words: List<LearnedWordEntity>): String {
        val sb = StringBuilder("[\n")
        words.forEachIndexed { index, item ->
            val escapedWord = escapeJson(item.word)
            val escapedLang = escapeJson(item.language)
            sb.append("  {")
            sb.append("\"word\":\"$escapedWord\",")
            sb.append("\"language\":\"$escapedLang\",")
            sb.append("\"frequency\":${item.frequencyCount},")
            sb.append("\"lastUsed\":${item.lastUsedTimestamp},")
            sb.append("\"isUserCoined\":${item.isUserCoined}")
            sb.append("}")
            if (index < words.lastIndex) sb.append(",")
            sb.append("\n")
        }
        sb.append("]")
        return sb.toString()
    }

    fun exportAsCsv(words: List<LearnedWordEntity>): String {
        val sb = StringBuilder("word,language,frequency,is_user_coined\n")
        for (item in words) {
            val escaped = item.word.replace("\"", "\"\"")
            sb.append("\"$escaped\",${item.language},${item.frequencyCount},${item.isUserCoined}\n")
        }
        return sb.toString()
    }

    fun importFromText(raw: String, fallbackLanguage: KeyboardLanguage): List<Pair<String, KeyboardLanguage>> {
        val trimmed = raw.trim()
        if (trimmed.isEmpty()) return emptyList()
        return if (trimmed.startsWith("[")) {
            importJson(trimmed, fallbackLanguage)
        } else {
            importCsv(trimmed, fallbackLanguage)
        }
    }

    private fun importJson(raw: String, fallbackLanguage: KeyboardLanguage): List<Pair<String, KeyboardLanguage>> {
        val objectRegex = Regex("\\{([^}]*)\\}")
        val wordRegex = Regex("\"word\"\\s*:\\s*\"((?:\\\\.|[^\"\\\\])*)\"")
        val langRegex = Regex("\"language\"\\s*:\\s*\"((?:\\\\.|[^\"\\\\])*)\"")
        return buildList {
            for (match in objectRegex.findAll(raw)) {
                val body = match.groupValues[1]
                val wordRaw = wordRegex.find(body)?.groupValues?.get(1) ?: continue
                val word = unescapeJson(wordRaw).trim()
                val langCode = langRegex.find(body)?.groupValues?.get(1)?.let(::unescapeJson)
                val lang = KeyboardLanguage.entries.firstOrNull { it.code == langCode } ?: fallbackLanguage
                if (word.isNotEmpty()) add(word to lang)
            }
        }
    }

    private fun importCsv(raw: String, fallbackLanguage: KeyboardLanguage): List<Pair<String, KeyboardLanguage>> {
        val lines = raw.lineSequence().map { it.trim() }.filter { it.isNotEmpty() }.toList()
        return buildList {
            for ((index, line) in lines.withIndex()) {
                if (index == 0 && line.lowercase().startsWith("word,")) continue
                val parts = line.split(",")
                val word = parts.firstOrNull()?.trim()?.removeSurrounding("\"")?.replace("\"\"", "\"")?.trim().orEmpty()
                val langCode = parts.getOrNull(1)?.trim()
                val lang = KeyboardLanguage.entries.firstOrNull { it.code == langCode } ?: fallbackLanguage
                if (word.isNotEmpty()) add(word to lang)
            }
        }
    }

    private fun escapeJson(value: String): String = buildString(value.length + 4) {
        for (ch in value) {
            when (ch) {
                '\\' -> append("\\\\")
                '"' -> append("\\\"")
                '\n' -> append("\\n")
                '\r' -> append("\\r")
                '\t' -> append("\\t")
                else -> append(ch)
            }
        }
    }

    private fun unescapeJson(value: String): String = buildString(value.length) {
        var i = 0
        while (i < value.length) {
            val ch = value[i]
            if (ch == '\\' && i + 1 < value.length) {
                when (val next = value[i + 1]) {
                    '\\' -> append('\\')
                    '"' -> append('"')
                    'n' -> append('\n')
                    'r' -> append('\r')
                    't' -> append('\t')
                    else -> append(next)
                }
                i += 2
            } else {
                append(ch)
                i++
            }
        }
    }
}
