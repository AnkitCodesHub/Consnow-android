package com.Consnow.features.connections.push

import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.Consnow.MainActivity
import com.Consnow.R

/** Builds the connect-request notifications (with Accept / Decline buttons). */
object ConnectionNotifications {
    private const val CHANNEL_ID = "connection_requests"

    fun requestNotificationId(requestId: String) = ("req:$requestId").hashCode()

    fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID, "Connect requests", NotificationManager.IMPORTANCE_HIGH
            ).apply { description = "When a friend asks to connect, or accepts your request" }
            context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }

    /** "Rahul wants to connect" with Accept / Decline right on the notification. */
    fun showRequest(context: Context, requestId: String, peerId: String, peerName: String) {
        val notificationId = requestNotificationId(requestId)
        fun action(action: String, code: Int) = PendingIntent.getBroadcast(
            context,
            notificationId * 31 + code,
            Intent(context, ConnectionActionReceiver::class.java)
                .setAction(action)
                .putExtra(ConnectionActionReceiver.EXTRA_REQUEST_ID, requestId)
                .putExtra(ConnectionActionReceiver.EXTRA_PEER_ID, peerId)
                .putExtra(ConnectionActionReceiver.EXTRA_PEER_NAME, peerName)
                .putExtra(ConnectionActionReceiver.EXTRA_NOTIFICATION_ID, notificationId),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val name = peerName.ifBlank { "A friend" }
        notify(
            context, notificationId,
            base(context, notificationId)
                .setContentTitle("$name wants to connect")
                .setContentText("Accept to see each other's steps and activity again.")
                .addAction(0, "Decline", action(ConnectionActionReceiver.ACTION_DECLINE, 2))
                .addAction(0, "Accept", action(ConnectionActionReceiver.ACTION_ACCEPT, 1))
                .setTimeoutAfter(24 * 60 * 60 * 1000L) // requests expire after a day
        )
    }

    fun showAccepted(context: Context, peerId: String, peerName: String) {
        val id = ("acc:$peerId").hashCode()
        val name = peerName.ifBlank { "Your friend" }
        notify(
            context, id,
            base(context, id)
                .setContentTitle("$name accepted your request")
                .setContentText("You can see each other's activity again.")
        )
    }

    fun showInfo(context: Context, notificationId: Int, title: String, text: String) =
        notify(context, notificationId, base(context, notificationId).setContentTitle(title).setContentText(text))

    fun cancel(context: Context, notificationId: Int) =
        NotificationManagerCompat.from(context).cancel(notificationId)

    private fun base(context: Context, requestCode: Int): NotificationCompat.Builder {
        ensureChannel(context)
        val open = PendingIntent.getActivity(
            context, requestCode,
            Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        return NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_consnow_logo)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_SOCIAL)
            .setContentIntent(open)
            .setAutoCancel(true)
    }

    @SuppressLint("MissingPermission") // checked via areNotificationsEnabled()
    private fun notify(context: Context, id: Int, builder: NotificationCompat.Builder) {
        val manager = NotificationManagerCompat.from(context)
        if (manager.areNotificationsEnabled()) manager.notify(id, builder.build())
    }
}
