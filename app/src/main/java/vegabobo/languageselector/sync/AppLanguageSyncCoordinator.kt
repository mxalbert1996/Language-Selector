package vegabobo.languageselector.sync

import android.content.Context
import android.content.pm.PackageManager
import android.os.LocaleList
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.Operation
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import vegabobo.languageselector.di.appSingletonEntryPoint
import vegabobo.languageselector.service.PrivilegedAcquisitionPolicy
import vegabobo.languageselector.service.PrivilegedAcquisitionResult
import vegabobo.languageselector.service.PrivilegedServiceManager

sealed class AppLanguageSyncResult {
    data object NoWork : AppLanguageSyncResult()
    data class Completed(val processed: Int, val updated: Int, val deleted: Int) :
        AppLanguageSyncResult()

    data class NoPrivilege(val reason: String) : AppLanguageSyncResult()
    data class TransientFailure(val reason: String) : AppLanguageSyncResult()
}

class AppLanguageSyncCoordinator(private val context: Context) {

    private val db by lazy(LazyThreadSafetyMode.NONE) {
        appSingletonEntryPoint(context).appInfoDb()
    }

    suspend fun syncOnce(): AppLanguageSyncResult = withContext(Dispatchers.IO) {
        val dao = db.appInfoDao()
        val recorded = dao.getAll().filter { it.recordedLanguageTag != null }
        if (recorded.isEmpty()) return@withContext AppLanguageSyncResult.NoWork
        val installed = context.packageManager.getInstalledApplications(
            PackageManager.ApplicationInfoFlags.of(0),
        ).map { it.packageName }.toSet()
        val outcome =
            PrivilegedServiceManager.acquireLease(context, PrivilegedAcquisitionPolicy.AUTO)
        if (outcome is PrivilegedAcquisitionResult.NoPrivilege) {
            return@withContext AppLanguageSyncResult.NoPrivilege(
                "privileged backend unavailable",
            )
        }
        if (outcome is PrivilegedAcquisitionResult.TransientFailure) {
            return@withContext AppLanguageSyncResult.TransientFailure(
                outcome.error.message ?: "bind failure",
            )
        }
        val lease = (outcome as PrivilegedAcquisitionResult.Acquired).lease
        val service = lease.service
        var processed = 0
        var updated = 0
        var deleted = 0
        recorded.forEach { row ->
            try {
                processed++
                if (!installed.contains(row.pkg)) {
                    dao.delete(row)
                    deleted++
                    return@forEach
                }
                val tag = row.recordedLanguageTag ?: return@forEach
                val current = service.getApplicationLocales(row.pkg)
                val target = LocaleList.forLanguageTags(tag)
                if (current.toLanguageTags() != target.toLanguageTags()) {
                    service.setApplicationLocales(row.pkg, target)
                    updated++
                }
            } catch (e: Throwable) {
                lease.release()
                return@withContext AppLanguageSyncResult.TransientFailure(
                    e.message ?: "sync failure",
                )
            }
        }
        lease.release()
        AppLanguageSyncResult.Completed(processed, updated, deleted)
    }

    fun syncImmediate() {
        CoroutineScope(Dispatchers.IO).launch {
            syncOnce()
        }
    }

    companion object {
        fun enqueuePeriodic(context: Context): Operation =
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                "app-language-sync",
                ExistingPeriodicWorkPolicy.UPDATE,
                PeriodicWorkRequestBuilder<AppLanguageSyncWorker>(1, TimeUnit.DAYS)
                    .setConstraints(Constraints.NONE)
                    .build(),
            )
    }
}
