package vegabobo.languageselector.sync

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import vegabobo.languageselector.logE

class AppLanguageSyncWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result = try {
        val syncResult = AppLanguageSyncCoordinator(applicationContext).syncOnce()
        AppLanguageSyncNotifier.notify(applicationContext, syncResult)
        when (syncResult) {
            AppLanguageSyncResult.NoWork -> Result.success()
            is AppLanguageSyncResult.Completed -> Result.success()
            is AppLanguageSyncResult.NoPrivilege -> Result.failure()
            is AppLanguageSyncResult.TransientFailure -> Result.retry()
        }
    } catch (e: Throwable) {
        logE("Sync failed transiently", e)
        AppLanguageSyncNotifier.notify(
            applicationContext,
            AppLanguageSyncResult.TransientFailure(e.message ?: "sync failure"),
        )
        Result.retry()
    }
}
