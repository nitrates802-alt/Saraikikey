package com.example.core.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [
        LearnedWordEntity::class,
        UserNGramEntity::class,
        BaseDictionaryEntity::class,
        ClipboardEntryEntity::class
    ],
    version = 1,
    exportSchema = false
)
abstract class SaraikiKeyboardDatabase : RoomDatabase() {
    abstract fun keyboardDao(): KeyboardDao

    companion object {
        @Volatile
        private var INSTANCE: SaraikiKeyboardDatabase? = null

        fun getInstance(context: Context): SaraikiKeyboardDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    SaraikiKeyboardDatabase::class.java,
                    "saraiki_pro_keyboard.db"
                )
                    .setJournalMode(JournalMode.WRITE_AHEAD_LOGGING)
                    .build()
                    .also { INSTANCE = it }
            }
        }
    }
}
