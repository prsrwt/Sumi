package com.sumi.app.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [GoalEntity::class, EntryEntity::class, SettingsEntity::class],
    version = 1,
    exportSchema = false
)
abstract class SumiDatabase : RoomDatabase() {

    abstract fun dao(): SumiDao

    companion object {
        @Volatile
        private var instance: SumiDatabase? = null

        fun get(context: Context): SumiDatabase =
            instance ?: synchronized(this) {
                instance ?: build(context.applicationContext).also { instance = it }
            }

        private fun build(context: Context): SumiDatabase =
            Room.databaseBuilder(context, SumiDatabase::class.java, "sumi.db")
                .addCallback(Seed)
                .build()

        /**
         * Creates the five goal rows, each on its default element, and the
         * settings row, so every other query can assume they exist. Raw SQL
         * because the DAO is not usable from inside the creation callback.
         */
        private object Seed : RoomDatabase.Callback() {
            override fun onCreate(db: SupportSQLiteDatabase) {
                super.onCreate(db)
                Element.defaultOrder.forEachIndexed { slot, element ->
                    db.execSQL(
                        "INSERT INTO goals (slot, name, element) VALUES (?, ?, ?)",
                        arrayOf<Any>(slot, "", element.name)
                    )
                }
                val defaults = Settings.Default
                db.execSQL(
                    "INSERT INTO settings (id, askIntervalMinutes, quietStartMinute, quietEndMinute) " +
                        "VALUES (0, ?, ?, ?)",
                    arrayOf<Any>(
                        defaults.askInterval.toMinutes().toInt(),
                        defaults.quietStart.toSecondOfDay() / 60,
                        defaults.quietEnd.toSecondOfDay() / 60
                    )
                )
            }
        }
    }
}
