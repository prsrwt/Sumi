package com.sumi.app.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        GoalEntity::class,
        DomainEntity::class,
        ActivityEntity::class,
        EntryEntity::class,
        SettingsEntity::class,
        SyncStateEntity::class,
        DirtyMonthEntity::class
    ],
    version = 4,
    exportSchema = true
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
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4)
                .build()

        /**
         * Version 2 adds Google Sheets sync: the link to the sheet and the queue of
         * months waiting to be written. Both tables are new, so nothing already on
         * the phone is touched. The SQL must match what Room would create itself;
         * app/schemas/.../2.json holds that exact statement.
         */
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `sync_state` (`id` INTEGER NOT NULL, " +
                        "`accountEmail` TEXT NOT NULL, `spreadsheetId` TEXT NOT NULL, " +
                        "`lastSyncedAt` INTEGER, `needsReconnect` INTEGER NOT NULL, PRIMARY KEY(`id`))"
                )
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `dirty_months` (`month` TEXT NOT NULL, PRIMARY KEY(`month`))"
                )
            }
        }

        /**
         * Version 3 remembers when the first-launch introduction was finished. The
         * column starts empty for everyone, so people upgrading see the
         * introduction once too, which is how they find out it exists.
         */
        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `settings` ADD COLUMN `onboardedAt` INTEGER")
            }
        }

        /**
         * Version 4 gives each element several domains, and each domain its own
         * activities, with the entry carrying which two it was logged under.
         *
         * Nothing already logged moves: every entry keeps the element it was
         * saved with, and the two new columns start empty. The five names people
         * already chose become the first domain under their element, so an
         * upgrade opens with the life they had already described rather than an
         * empty list.
         */
        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `domains` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`name` TEXT NOT NULL, `element` TEXT NOT NULL, `position` INTEGER NOT NULL)"
                )
                db.execSQL(
                    "CREATE UNIQUE INDEX IF NOT EXISTS `index_domains_element_name` ON `domains` (`element`, `name`)"
                )
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `activities` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`domainId` INTEGER NOT NULL, `name` TEXT NOT NULL, `uses` INTEGER NOT NULL, " +
                        "`lastUsedAt` INTEGER, FOREIGN KEY(`domainId`) REFERENCES `domains`(`id`) " +
                        "ON UPDATE NO ACTION ON DELETE CASCADE )"
                )
                db.execSQL(
                    "CREATE UNIQUE INDEX IF NOT EXISTS `index_activities_domainId_name` ON `activities` (`domainId`, `name`)"
                )
                db.execSQL("ALTER TABLE `entries` ADD COLUMN `domainId` INTEGER")
                db.execSQL("ALTER TABLE `entries` ADD COLUMN `activityId` INTEGER")
                db.execSQL(
                    "INSERT INTO `domains` (`name`, `element`, `position`) " +
                        "SELECT TRIM(`name`), `element`, 0 FROM `goals` WHERE TRIM(`name`) != ''"
                )
                seedDomains(db)
            }
        }

        /**
         * Creates the five goal rows, each on its default element, and the
         * settings row, so every other query can assume they exist. Raw SQL
         * because the DAO is not usable from inside the creation callback.
         */
        /**
         * Every part of life Sumi knows about, under its own element, each with the
         * words it comes with. A phone that has never been set up can still offer
         * something to tap, and picking a ready-made life only decides which of
         * them comes first.
         *
         * Anything already there is left alone: this runs on an upgrade too, where
         * the names somebody chose are already domains of their own.
         */
        private fun seedDomains(db: SupportSQLiteDatabase) {
            Domains.common.forEachIndexed { index, idea ->
                db.execSQL(
                    "INSERT OR IGNORE INTO `domains` (`name`, `element`, `position`) VALUES (?, ?, ?)",
                    arrayOf<Any>(idea.name, idea.element.name, index + 1)
                )
                idea.words.forEach { word ->
                    db.execSQL(
                        "INSERT OR IGNORE INTO `activities` (`domainId`, `name`, `uses`, `lastUsedAt`) " +
                            "SELECT `id`, ?, 0, NULL FROM `domains` WHERE `element` = ? AND `name` = ?",
                        arrayOf<Any>(word, idea.element.name, idea.name)
                    )
                }
            }
        }

        private object Seed : RoomDatabase.Callback() {
            override fun onCreate(db: SupportSQLiteDatabase) {
                super.onCreate(db)
                seedDomains(db)
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
