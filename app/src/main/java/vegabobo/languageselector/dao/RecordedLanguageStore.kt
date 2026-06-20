package vegabobo.languageselector.dao

import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Singleton
class RecordedLanguageStore @Inject constructor(
    private val dao: AppInfoDao,
) {
    suspend fun recordLanguageSelection(pkg: String, name: String, recordedLanguageTag: String?) =
        withContext(Dispatchers.IO) {
            ensureRow(pkg, name)
            dao.setSelectionNameAndRecordedLanguage(pkg, name, recordedLanguageTag)
            cleanupIfEmpty(pkg)
        }

    suspend fun recordHistorySelection(pkg: String, name: String, lastSelected: Long) =
        withContext(Dispatchers.IO) {
            ensureRow(pkg, name)
            dao.setHistorySelection(pkg, name, lastSelected)
        }

    suspend fun clearRecordedLanguage(pkg: String) = withContext(Dispatchers.IO) {
        dao.setRecordedLanguageTag(pkg, null)
        cleanupIfEmpty(pkg)
    }

    suspend fun clearAllHistory() = withContext(Dispatchers.IO) {
        dao.clearAllLastSelected()
        dao.deleteEmptyRows()
    }

    private fun cleanupIfEmpty(pkg: String) {
        dao.deleteIfEmpty(pkg)
    }

    private fun ensureRow(pkg: String, name: String) {
        val inserted = dao.insertIgnore(
            AppInfoEntity(
                pkg = pkg,
                name = name,
                lastSelected = null,
                recordedLanguageTag = null,
            ),
        )
        if (inserted == -1L) {
            dao.setHistoryName(pkg, name)
        }
    }
}
