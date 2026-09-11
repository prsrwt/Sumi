package com.fings.app.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [GoalEntity::class, DayEntryEntity::class],
    version = 1,
    exportSchema = false
)
abstract class FingsDatabase : RoomDatabase() {

    abstract fun dao(): FingsDao

    companion object {
        @Volatile
        private var instance: FingsDatabase? = null

        fun get(context: Context): FingsDatabase =
            instance ?: synchronized(this) {
                instance ?: build(context.applicationContext).also { instance = it }
            }

        private fun build(context: Context): FingsDatabase =
            Room.databaseBuilder(context, FingsDatabase::class.java, "fings.db")
                .addCallback(SeedGoals)
                .build()

        /**
         * Creates the five goal rows up front so every other query can assume
         * they exist. Raw SQL here rather than the DAO, because the database is
         * not yet open to the rest of the app at this point.
         */
        private object SeedGoals : RoomDatabase.Callback() {
            override fun onCreate(db: SupportSQLiteDatabase) {
                super.onCreate(db)
                for (slot in 0 until GOAL_COUNT) {
                    db.execSQL(
                        "INSERT INTO goals (slot, name) VALUES (?, ?)",
                        arrayOf<Any>(slot, "")
                    )
                }
            }
        }
    }
}
