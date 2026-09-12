package xyz.zongtech.campuswall

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.work.*
import java.util.concurrent.TimeUnit

class NotificationWorker(context: Context, params: WorkerParameters) :
    CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val prefs = applicationContext.getSharedPreferences("preferences", 0)
        if (!prefs.getBoolean("notifications", false)) return Result.success()
        return try {
            val api = WallApi(applicationContext)
            val r = api.request("/api/user/me/notifications?page=1&page_size=20")
            val unread = r.optInt("unread")
            val newest = r.objects("notifications").firstOrNull()?.s("id").orEmpty()
            if (
                unread > 0 &&
                    newest != prefs.getString("last_notification", "") &&
                    NotificationManagerCompat.from(applicationContext).areNotificationsEnabled()
            ) {
                val intent =
                    PendingIntent.getActivity(
                        applicationContext,
                        0,
                        Intent(applicationContext, MainActivity::class.java)
                            .putExtra("open_notifications", true),
                        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
                    )
                val notification =
                    NotificationCompat.Builder(applicationContext, "campuswall.messages")
                        .setSmallIcon(xyz.zongtech.campuswall.R.drawable.ic_wall)
                        .setContentTitle("校园墙有 $unread 条未读消息")
                        .setContentText("点击查看评论、回复与审核进度")
                        .setContentIntent(intent)
                        .setAutoCancel(true)
                        .build()
                try {
                    NotificationManagerCompat.from(applicationContext).notify(1, notification)
                } catch (_: SecurityException) {}
            }
            prefs.edit().putString("last_notification", newest).apply()
            Result.success()
        } catch (_: Exception) {
            Result.retry()
        }
    }

    companion object {
        fun enable(context: Context) {
            context
                .getSharedPreferences("preferences", 0)
                .edit()
                .putBoolean("notifications", true)
                .apply()
            context
                .getSystemService(NotificationManager::class.java)
                .createNotificationChannel(
                    NotificationChannel(
                        "campuswall.messages",
                        "校园墙消息",
                        NotificationManager.IMPORTANCE_DEFAULT,
                    )
                )
            WorkManager.getInstance(context)
                .enqueueUniquePeriodicWork(
                    "campuswall.messages",
                    ExistingPeriodicWorkPolicy.UPDATE,
                    PeriodicWorkRequestBuilder<NotificationWorker>(15, TimeUnit.MINUTES)
                        .setConstraints(
                            Constraints.Builder()
                                .setRequiredNetworkType(NetworkType.CONNECTED)
                                .build()
                        )
                        .build(),
                )
        }

        fun disable(context: Context) {
            context
                .getSharedPreferences("preferences", 0)
                .edit()
                .putBoolean("notifications", false)
                .remove("last_notification")
                .apply()
            WorkManager.getInstance(context).cancelUniqueWork("campuswall.messages")
            NotificationManagerCompat.from(context).cancel(1)
        }
    }
}
