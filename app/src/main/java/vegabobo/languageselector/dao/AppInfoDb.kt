package vegabobo.languageselector.dao

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(entities = [AppInfoEntity::class], version = 2)
abstract class AppInfoDb : RoomDatabase() {
    abstract fun appInfoDao(): AppInfoDao

    companion object {
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE appinfoentity ADD COLUMN recorded_language_tag TEXT",
                )
            }
        }
    }
}
