package com.victor.postly.notifications

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.widget.Toast
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.victor.postly.R
import com.victor.postly.ui.ChatActivity
import com.victor.postly.ui.HomeActivity
import com.victor.postly.ui.PostActivity
import kotlin.math.absoluteValue

object NotificationHelper {

    private const val CHANNEL_SOCIAL = "postly_social_notifications"
    const val TYPE_MESSAGE = "message"
    const val TYPE_COMMENT = "comment"
    const val TYPE_LIKE = "like"

    fun createChannels(context: Context) {
        val channel = NotificationChannel(
            CHANNEL_SOCIAL,
            context.getString(R.string.notification_channel_social_name),
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = context.getString(R.string.notification_channel_social_description)
        }

        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(channel)
    }

    fun showNotification(
        context: Context,
        title: String,
        body: String,
        type: String,
        data: Map<String, String>
    ) {
        if (
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            Toast.makeText(context, "$title: $body", Toast.LENGTH_LONG).show()
            return
        }

        createChannels(context)

        val intent = notificationIntent(context, type, data)
        val requestCode = listOf(type, data["chatId"], data["postId"], data["senderId"], body)
            .joinToString("|")
            .hashCode()
            .absoluteValue

        val pendingIntent = PendingIntent.getActivity(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_SOCIAL)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title.ifBlank { context.getString(R.string.app_name) })
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pendingIntent)
            .build()

        NotificationManagerCompat.from(context).notify(requestCode, notification)
    }

    private fun notificationIntent(
        context: Context,
        type: String,
        data: Map<String, String>
    ): Intent {
        return when (type) {
            TYPE_MESSAGE -> Intent(context, ChatActivity::class.java).apply {
                putExtra(ChatActivity.EXTRA_CHAT_ID, data["chatId"].orEmpty())
                putExtra(ChatActivity.EXTRA_USER_ID, data["senderId"].orEmpty())
            }

            TYPE_COMMENT, TYPE_LIKE -> Intent(context, PostActivity::class.java).apply {
                putExtra(PostActivity.EXTRA_POST_ID, data["postId"].orEmpty())
            }

            else -> Intent(context, HomeActivity::class.java)
        }.apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
    }
}
