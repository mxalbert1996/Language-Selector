package vegabobo.languageselector.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface AppInfoDao {
    @Query("SELECT * FROM appinfoentity")
    fun getAll(): List<AppInfoEntity>

    @Query("SELECT * FROM appinfoentity WHERE pkg = :pkg")
    fun findByPkg(pkg: String): AppInfoEntity?

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    fun insertIgnore(aie: AppInfoEntity): Long

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    fun insertAll(vararg aie: AppInfoEntity)

    @Delete
    fun delete(aie: AppInfoEntity)

    @Query("DELETE FROM appinfoentity")
    fun deleteAll(): Int

    //

    @Query("SELECT (SELECT COUNT(*) FROM appinfoentity) == 0")
    fun isEmpty(): Boolean

    //

    @Query("UPDATE appinfoentity SET last_selected = NULL")
    fun cleanLastSelectedAll()

    @Query("UPDATE appinfoentity SET last_selected = :lastSelected WHERE pkg = :pkg")
    fun setLastSelected(pkg: String, lastSelected: Long)

    @Query("UPDATE appinfoentity SET recorded_language_tag = :recordedLanguageTag WHERE pkg = :pkg")
    fun setRecordedLanguageTag(pkg: String, recordedLanguageTag: String?)

    @Query(
        "UPDATE appinfoentity SET name = :name, recorded_language_tag = :recordedLanguageTag WHERE pkg = :pkg",
    )
    fun setSelectionNameAndRecordedLanguage(pkg: String, name: String, recordedLanguageTag: String?)

    @Query("UPDATE appinfoentity SET name = :name, last_selected = :lastSelected WHERE pkg = :pkg")
    fun setHistorySelection(pkg: String, name: String, lastSelected: Long)

    @Query("UPDATE appinfoentity SET name = :name WHERE pkg = :pkg")
    fun setHistoryName(pkg: String, name: String)

    @Query(
        "DELETE FROM appinfoentity WHERE pkg = :pkg AND last_selected IS NULL AND recorded_language_tag IS NULL",
    )
    fun deleteIfEmpty(pkg: String): Int

    @Query(
        "DELETE FROM appinfoentity WHERE last_selected IS NULL AND recorded_language_tag IS NULL",
    )
    fun deleteEmptyRows(): Int

    @Query("UPDATE appinfoentity SET last_selected = NULL WHERE last_selected IS NOT NULL")
    fun clearAllLastSelected(): Int

    @Query(
        "SELECT * FROM appinfoentity WHERE last_selected IS NOT NULL ORDER BY last_selected DESC",
    )
    fun getHistory(): List<AppInfoEntity>

    @Query(
        "SELECT * FROM appinfoentity WHERE recorded_language_tag IS NOT NULL ORDER BY name ASC",
    )
    fun getRecordedApps(): List<AppInfoEntity>
}
