package vegabobo.languageselector.sync

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import vegabobo.languageselector.R

object AppLanguageSyncNotifier {

    private const val CHANNEL_ID = "app_language_sync"
    private const val NOTIFICATION_ID = 1001

    fun notify(context: Context, result: AppLanguageSyncResult) {
        if (ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS,
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }

        ensureChannel(context)

        val message = when (result) {
            AppLanguageSyncResult.NoWork -> context.getString(R.string.sync_notification_no_work)
            is AppLanguageSyncResult.Completed -> context.getString(
                R.string.sync_notification_completed,
                result.processed,
                result.updated,
                result.deleted,
            )
            is AppLanguageSyncResult.NoPrivilege ->
                context.getString(R.string.sync_notification_no_privilege)
            is AppLanguageSyncResult.TransientFailure -> context.getString(
                R.string.sync_notification_transient_failure,
                result.reason,
            )
        }

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.qs_tile)
            .setContentTitle(context.getString(R.string.sync_notification_title))
            .setContentText(message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .build()

        NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notification)
    }

    private fun ensureChannel(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java)
        val channel = NotificationChannel(
            CHANNEL_ID,
            context.getString(R.string.sync_notification_channel_name),
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = context.getString(R.string.sync_notification_channel_description)
        }
        manager.createNotificationChannel(channel)
    }
}
