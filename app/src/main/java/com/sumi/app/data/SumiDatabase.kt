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
    version = 6,
    exportSchema = true
)
/**
 * What the database holds, and what it does not.
 *
 * The catalogue of parts of life, and the words under each one, lives in [Domains],
 * in code, where it can be read and changed without touching anybody's phone. The
 * database holds only what a person actually chose: the parts of life they picked
 * or wrote, and the words under those. Nothing from the catalogue is written here
 * until it is chosen, which is why every screen can show a short list belonging to
 * one person rather than everything Sumi knows about.
 */
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
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6)
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
                seedWordsForExistingDomains(db)
            }
        }

        /**
         * Version 5 changes no tables. It gives the parts of life already on a
         * phone the words they would have arrived with, for anybody who reached
         * version 4 before Sumi came with any.
         */
        private val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                seedWordsForExistingDomains(db)
            }
        }

        /**
         * Version 6 changes no tables. It undoes a seeding that should not have
         * happened: one build wrote every part of life Sumi knows about into the
         * database, so a phone carried twenty of them and every word under them,
         * none of it chosen by the person holding the phone.
         *
         * It runs only where that seeding is still visible, which is a database
         * carrying the whole catalogue under its own elements. What has been used
         * is kept, the name on each spoke is kept, and where those five names are
         * a ready-made life, the rest of that life is kept with them. Everything
         * the seed wrote and nobody touched goes, and its words go with it.
         */
        private val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                if (!carriesWholeCatalogue(db)) return
                val keep = partsWorthKeeping(db)
                everyDomain(db).forEach { (id, name, element) ->
                    val chosen = (element to name.trim().lowercase()) in keep
                    if (chosen || isInUse(db, id)) return@forEach
                    // The words go by hand. Room turns foreign keys off while a
                    // migration runs, so the cascade that would normally take them
                    // does nothing here, and they would be left pointing at a part
                    // of life that no longer exists.
                    db.execSQL("DELETE FROM `activities` WHERE `domainId` = ?", arrayOf<Any>(id))
                    db.execSQL("DELETE FROM `domains` WHERE `id` = ?", arrayOf<Any>(id))
                }
            }
        }

        /**
         * Whether this database carries the whole catalogue under its own
         * elements, which nobody would ever arrive at by choosing. It is the one
         * safe sign that the blanket seed ran, and it keeps the repair away from a
         * phone whose parts of life are its own.
         */
        private fun carriesWholeCatalogue(db: SupportSQLiteDatabase): Boolean =
            Domains.common.all { idea ->
                countOf(
                    db,
                    "SELECT COUNT(*) FROM `domains` WHERE `element` = ? AND `name` = ?",
                    arrayOf(idea.element.name, idea.name)
                ) > 0
            }

        /** Each element paired with the lower-cased names it should keep. */
        private fun partsWorthKeeping(db: SupportSQLiteDatabase): Set<Pair<String, String>> {
            val spokes = spokeNames(db)
            val life = Presets.all.firstOrNull { preset ->
                !preset.isBlank && Element.entries.all { element ->
                    preset.names[element].orEmpty().equals(spokes[element].orEmpty(), ignoreCase = true)
                }
            }
            return Element.entries.flatMap { element ->
                val names = life?.parts?.get(element) ?: listOfNotNull(spokes[element])
                names.map { element.name to it.trim().lowercase() }
            }.toSet()
        }

        /** The name on each spoke, which is the one part of life somebody certainly chose. */
        private fun spokeNames(db: SupportSQLiteDatabase): Map<Element, String> {
            val names = mutableMapOf<Element, String>()
            db.query("SELECT `element`, `name` FROM `goals`").use { cursor ->
                while (cursor.moveToNext()) {
                    val element = Element.entries.firstOrNull { it.name == cursor.getString(0) }
                    val name = cursor.getString(1).orEmpty().trim()
                    if (element != null && name.isNotEmpty()) names[element] = name
                }
            }
            return names
        }

        private fun everyDomain(db: SupportSQLiteDatabase): List<Triple<Long, String, String>> {
            val rows = mutableListOf<Triple<Long, String, String>>()
            db.query("SELECT `id`, `name`, `element` FROM `domains`").use { cursor ->
                while (cursor.moveToNext()) {
                    rows += Triple(cursor.getLong(0), cursor.getString(1), cursor.getString(2))
                }
            }
            return rows
        }

        /**
         * Whether anything here has been logged or tapped. Entries that were
         * deleted count too: they can come back, and they would come back pointing
         * at a part of life that no longer exists.
         */
        private fun isInUse(db: SupportSQLiteDatabase, id: Long): Boolean =
            countOf(
                db,
                "SELECT (SELECT COUNT(*) FROM `activities` WHERE `domainId` = ? AND `uses` > 0) + " +
                    "(SELECT COUNT(*) FROM `entries` WHERE `domainId` = ?)",
                arrayOf<Any>(id, id)
            ) > 0

        private fun countOf(db: SupportSQLiteDatabase, sql: String, args: Array<Any>): Int =
            db.query(sql, args).use { if (it.moveToFirst()) it.getInt(0) else 0 }

        /**
         * Gives the parts of life already on the phone the words they would have
         * arrived with. Only the ones somebody actually has: a database full of
         * every part of life Sumi knows about would put twenty of them in front of
         * a person who chose five.
         */
        private fun seedWordsForExistingDomains(db: SupportSQLiteDatabase) {
            Domains.common.forEach { idea ->
                idea.words.forEach { word ->
                    db.execSQL(
                        "INSERT OR IGNORE INTO `activities` (`domainId`, `name`, `uses`, `lastUsedAt`) " +
                            "SELECT `id`, ?, 0, NULL FROM `domains` WHERE `name` = ?",
                        arrayOf<Any>(word, idea.name)
                    )
                }
            }
        }

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
