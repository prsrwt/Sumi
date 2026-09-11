package com.beaver.app.data

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
abstract class BeaverDatabase : RoomDatabase() {

    abstract fun dao(): BeaverDao

    companion object {
        @Volatile
        private var instance: BeaverDatabase? = null

        fun get(context: Context): BeaverDatabase =
            instance ?: synchronized(this) {
                instance ?: build(context.applicationContext).also { instance = it }
            }

        private fun build(context: Context): BeaverDatabase =
            Room.databaseBuilder(context, BeaverDatabase::class.java, "beaver.db")
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
