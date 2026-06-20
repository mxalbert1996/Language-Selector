package vegabobo.languageselector.sync

import android.content.Context
import android.content.pm.PackageManager
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import vegabobo.languageselector.di.appSingletonEntryPoint
import vegabobo.languageselector.service.UserServiceProvider

class AppLanguageSyncCoordinator(private val context: Context) {

    private val db by lazy(LazyThreadSafetyMode.NONE) {
        appSingletonEntryPoint(context).appInfoDb()
    }

    suspend fun syncOnce() = withContext(Dispatchers.IO) {
        val dao = db.appInfoDao()
        val recorded = dao.getAll().filter { it.recordedLanguageTag != null }
        val installed = context.packageManager.getInstalledApplications(
            PackageManager.ApplicationInfoFlags.of(0),
        ).map { it.packageName }.toSet()
        recorded.forEach { row ->
            try {
                if (!installed.contains(row.pkg)) {
                    dao.delete(row)
                    return@forEach
                }
                val service = UserServiceProvider.connection.SERVICE ?: return@forEach
                val tag = row.recordedLanguageTag ?: return@forEach
                val current = service.getApplicationLocales(row.pkg)
                val target = android.os.LocaleList.forLanguageTags(tag)
                if (current.toLanguageTags() != target.toLanguageTags()) {
                    service.setApplicationLocales(row.pkg, target)
                }
            } catch (_: Throwable) {
                // best-effort: continue with remaining rows
            }
        }
    }

    fun syncImmediate() {
        CoroutineScope(Dispatchers.IO).launch {
            syncOnce()
        }
    }

    companion object {
        fun enqueuePeriodic(context: Context) {
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                "app-language-sync",
                ExistingPeriodicWorkPolicy.UPDATE,
                PeriodicWorkRequestBuilder<AppLanguageSyncWorker>(1, TimeUnit.DAYS)
                    .setConstraints(Constraints.NONE)
                    .build(),
            )
        }
    }
}
