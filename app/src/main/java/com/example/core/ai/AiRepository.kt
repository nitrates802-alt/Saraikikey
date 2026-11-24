package com.example.core.ai

import com.example.core.preferences.KeyboardLanguage

enum class AiTaskType(val title: String, val shortLabel: String) {
    TRANSLATE("Translate", "Translate"),
    PROOFREAD("Fix Grammar & Spelling", "Grammar"),
    REWRITE_POLITE("Rewrite Polite Tone", "Polite"),
    REWRITE_CONCISE("Shorten Text", "Shorten"),
    SMART_REPLY("Generate Smart Reply", "Reply")
}

data class AiResult(
    val outputText: String,
    val notice: String,
    val isPlaceholder: Boolean = true
)

/**
 * Single repository abstraction for all keyboard & companion-app AI functions.
 */
interface AiRepository {
    suspend fun runTask(
        task: AiTaskType,
        inputText: String,
        targetLanguage: KeyboardLanguage
    ): AiResult
}

/**
 * Placeholder implementation used when Firebase has not been enabled.
 * Returns clearly labeled sample outputs alongside an explicit notice that
 * live Gemini AI features require Firebase.
 */
class PlaceholderAiRepository : AiRepository {

    override suspend fun runTask(
        task: AiTaskType,
        inputText: String,
        targetLanguage: KeyboardLanguage
    ): AiResult {
        val cleaned = inputText.trim().ifEmpty {
            when (targetLanguage) {
                KeyboardLanguage.SARAIKI -> "تساڋا حال کیویں ہے؟"
                KeyboardLanguage.URDU -> "آپ کیسے ہیں؟"
                KeyboardLanguage.ENGLISH -> "How are you doing today?"
            }
        }

        val sampleBody = when (task) {
            AiTaskType.TRANSLATE -> when (targetLanguage) {
                KeyboardLanguage.SARAIKI -> "تساں دا ٻہوں شکریہ، اساں خیریت نال آں۔ ($cleaned)"
                KeyboardLanguage.URDU -> "آپ کا بہت شکریہ، ہم خیریت سے ہیں۔ ($cleaned)"
                KeyboardLanguage.ENGLISH -> "Thank you very much, everything is going well. ($cleaned)"
            }
            AiTaskType.PROOFREAD -> when (targetLanguage) {
                KeyboardLanguage.SARAIKI -> "$cleaned (درست املا و جملہ)"
                KeyboardLanguage.URDU -> "$cleaned (درست املا اور گرامر)"
                KeyboardLanguage.ENGLISH -> "$cleaned (grammar & punctuation checked)"
            }
            AiTaskType.REWRITE_POLITE -> when (targetLanguage) {
                KeyboardLanguage.SARAIKI -> "مہربانی فرما کے: $cleaned"
                KeyboardLanguage.URDU -> "براہِ کرم: $cleaned"
                KeyboardLanguage.ENGLISH -> "Kindly note: $cleaned"
            }
            AiTaskType.REWRITE_CONCISE -> cleaned.split(Regex("\\s+")).take(8).joinToString(" ")
            AiTaskType.SMART_REPLY -> when (targetLanguage) {
                KeyboardLanguage.SARAIKI -> "جی ہاں، بالکل ٹھیک ہے۔ میں تھوڋی دیر وچ رابطہ کرینداں۔"
                KeyboardLanguage.URDU -> "جی ہاں، بالکل ٹھیک ہے۔ میں جلد رابطہ کرتا ہوں۔"
                KeyboardLanguage.ENGLISH -> "Sounds great! I will follow up shortly."
            }
        }

        return AiResult(
            outputText = "[Sample AI Response] $sampleBody",
            notice = NOTICE_FIREBASE_REQUIRED,
            isPlaceholder = true
        )
    }

    companion object {
        const val NOTICE_FIREBASE_REQUIRED =
            "AI features require Firebase. Showing sample output until Firebase AI Logic is enabled."
    }
}
