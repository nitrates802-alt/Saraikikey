package com.example.core.linguistics

import android.content.Context
import org.json.JSONObject

data class PopupSpec(
    val items: List<String>,
    val centerIndex: Int
)

class SaraikiCharacterConfig(
    val longPressPopups: Map<String, PopupSpec>,
    val mandatoryUnicodeChars: Set<String>
) {
    fun getPopupFor(keyLabel: String): PopupSpec? = longPressPopups[keyLabel]

    fun isSaraikiSpecificWord(word: String): Boolean =
        word.any { ch -> ch.toString() in mandatoryUnicodeChars }

    companion object {
        val DEFAULT_MANDATORY_CHARS = setOf("ٻ", "ڄ", "ڳ", "ڱ", "ڋ", "ݨ", "ݮ")

        // Mandatory Gboard-style popup geometry:
        // ج -> ڄ centered; د -> ڋ centered; ن -> ݨ centered; ب -> ٻ centered;
        // گ -> ڳ centered (index 1) and ڱ immediately to the left of ڳ (index 0).
        val DEFAULT_POPUPS: Map<String, PopupSpec> = mapOf(
            "ج" to PopupSpec(items = listOf("ڄ"), centerIndex = 0),
            "د" to PopupSpec(items = listOf("ڋ"), centerIndex = 0),
            "ن" to PopupSpec(items = listOf("ݨ"), centerIndex = 0),
            "ب" to PopupSpec(items = listOf("ٻ"), centerIndex = 0),
            "گ" to PopupSpec(items = listOf("ڱ", "ڳ"), centerIndex = 1),
            "چ" to PopupSpec(items = listOf("ݮ"), centerIndex = 0),
            "ا" to PopupSpec(items = listOf("آ", "أ", "إ"), centerIndex = 1),
            "ر" to PopupSpec(items = listOf("ڑ"), centerIndex = 0),
            "ت" to PopupSpec(items = listOf("ٹ", "ث"), centerIndex = 0),
            "ہ" to PopupSpec(items = listOf("ھ", "ۂ"), centerIndex = 0),
            "ی" to PopupSpec(items = listOf("ئ", "ے"), centerIndex = 0),
            "و" to PopupSpec(items = listOf("ؤ"), centerIndex = 0),
            "س" to PopupSpec(items = listOf("ص"), centerIndex = 0),
            "ز" to PopupSpec(items = listOf("ژ", "ض"), centerIndex = 0),
            "ط" to PopupSpec(items = listOf("ظ"), centerIndex = 0),
            "ح" to PopupSpec(items = listOf("خ"), centerIndex = 0),
            "ع" to PopupSpec(items = listOf("غ"), centerIndex = 0)
        )

        fun defaultConfig(): SaraikiCharacterConfig =
            SaraikiCharacterConfig(DEFAULT_POPUPS, DEFAULT_MANDATORY_CHARS)

        fun fromAssets(context: Context): SaraikiCharacterConfig {
            return try {
                val raw = context.assets.open("saraiki_char_map.json").bufferedReader().use { it.readText() }
                fromJson(raw)
            } catch (_: Exception) {
                defaultConfig()
            }
        }

        fun fromJson(rawJson: String): SaraikiCharacterConfig {
            val root = JSONObject(rawJson)
            val mandatory = mutableSetOf<String>()
            val mandatoryArr = root.optJSONArray("mandatory_unicode_chars")
            if (mandatoryArr != null) {
                for (i in 0 until mandatoryArr.length()) {
                    val ch = mandatoryArr.getJSONObject(i).optString("char")
                    if (ch.isNotEmpty()) mandatory.add(ch)
                }
            }
            val popups = mutableMapOf<String, PopupSpec>()
            val popupObj = root.optJSONObject("long_press_popups")
            if (popupObj != null) {
                val keys = popupObj.keys()
                while (keys.hasNext()) {
                    val key = keys.next()
                    val specObj = popupObj.getJSONObject(key)
                    val itemsArr = specObj.getJSONArray("items")
                    val items = buildList {
                        for (i in 0 until itemsArr.length()) {
                            add(itemsArr.getString(i))
                        }
                    }
                    val centerIndex = specObj.optInt("centerIndex", 0).coerceIn(0, (items.size - 1).coerceAtLeast(0))
                    if (items.isNotEmpty()) {
                        popups[key] = PopupSpec(items, centerIndex)
                    }
                }
            }
            return SaraikiCharacterConfig(
                longPressPopups = if (popups.isEmpty()) DEFAULT_POPUPS else popups,
                mandatoryUnicodeChars = if (mandatory.isEmpty()) DEFAULT_MANDATORY_CHARS else mandatory
            )
        }

        fun ensureWesternArabicDigits(text: String): String {
            if (text.isEmpty()) return text
            val sb = StringBuilder(text.length)
            for (ch in text) {
                val mapped = when (ch) {
                    '۰', '٠' -> '0'
                    '۱', '١' -> '1'
                    '۲', '٢' -> '2'
                    '۳', '٣' -> '3'
                    '۴', '٤' -> '4'
                    '۵', '٥' -> '5'
                    '۶', '٦' -> '6'
                    '۷', '٧' -> '7'
                    '۸', '٨' -> '8'
                    '۹', '٩' -> '9'
                    else -> ch
                }
                sb.append(mapped)
            }
            return sb.toString()
        }
    }
}
