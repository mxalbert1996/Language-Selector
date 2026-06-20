package vegabobo.languageselector.sync

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters

class AppLanguageSyncWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        AppLanguageSyncCoordinator(applicationContext).syncOnce()
        return Result.success()
    }
}
