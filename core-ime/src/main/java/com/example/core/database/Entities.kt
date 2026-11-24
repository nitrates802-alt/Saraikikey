package com.example.core.database

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "learned_words",
    indices = [
        Index(value = ["language", "word"], unique = true),
        Index(value = ["language", "frequency_count"])
    ]
)
data class LearnedWordEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val word: String,
    val language: String,
    @ColumnInfo(name = "frequency_count") val frequencyCount: Int = 1,
    @ColumnInfo(name = "last_used_timestamp") val lastUsedTimestamp: Long = System.currentTimeMillis(),
    @ColumnInfo(name = "is_user_coined") val isUserCoined: Boolean = true
)

@Entity(
    tableName = "user_ngrams",
    indices = [
        Index(value = ["language", "context_prefix", "next_word"], unique = true),
        Index(value = ["language", "context_prefix"])
    ]
)
data class UserNGramEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val language: String,
    @ColumnInfo(name = "context_prefix") val contextPrefix: String,
    @ColumnInfo(name = "next_word") val nextWord: String,
    @ColumnInfo(name = "transition_count") val transitionCount: Int = 1,
    @ColumnInfo(name = "last_used_timestamp") val lastUsedTimestamp: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "base_dictionary",
    indices = [
        Index(value = ["language", "word"], unique = true),
        Index(value = ["language", "base_frequency"])
    ]
)
data class BaseDictionaryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val word: String,
    val language: String,
    @ColumnInfo(name = "base_frequency") val baseFrequency: Int = 100,
    @ColumnInfo(name = "is_inflection") val isInflection: Boolean = false
)

@Entity(
    tableName = "clipboard_items",
    indices = [Index(value = ["is_pinned", "created_at"])]
)
data class ClipboardEntryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val text: String,
    @ColumnInfo(name = "is_pinned") val isPinned: Boolean = false,
    @ColumnInfo(name = "created_at") val createdAt: Long = System.currentTimeMillis()
)
