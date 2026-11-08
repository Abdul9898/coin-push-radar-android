package com.coinpush.radar.engine

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.coinpush.radar.MainActivity
import com.coinpush.radar.R
import java.util.concurrent.ConcurrentHashMap

object AlertNotificationManager {

    const val CHANNEL_ID = "coin_push_breakout_alerts"
    private const val CHANNEL_NAME = "Coin Push Breakout Alerts"
    private val lastAlertTime = ConcurrentHashMap<String, Long>()
    private const val ALERT_COOLDOWN_MS = 180_000L // 3 minutes per coin cooldown

    fun initNotificationChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val soundUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
            val audioAttributes = AudioAttributes.Builder()
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .setUsage(AudioAttributes.USAGE_NOTIFICATION_COMMUNICATION_INSTANT)
                .build()

            val channel = NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Real-time crypto breakout and momentum alert notifications"
                enableLights(true)
                enableVibration(true)
                vibrationPattern = longArrayOf(0, 250, 150, 250)
                setSound(soundUri, audioAttributes)
                lockscreenVisibility = NotificationCompat.VISIBILITY_PUBLIC
            }

            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    fun triggerAlertNotification(
        context: Context,
        symbol: String,
        alertType: String,
        priceFormatted: String,
        changeFormatted: String,
        isTest: Boolean = false
    ): Boolean {
        val now = System.currentTimeMillis()
        if (!isTest) {
            val last = lastAlertTime[symbol] ?: 0L
            if (now - last < ALERT_COOLDOWN_MS) {
                return false // Cooldown active, don't spam
            }
        }
        lastAlertTime[symbol] = now

        // Trigger phone haptic vibration
        try {
            val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
            if (vibrator != null && vibrator.hasVibrator()) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    vibrator.vibrate(VibrationEffect.createWaveform(longArrayOf(0, 180, 100, 200), -1))
                } else {
                    vibrator.vibrate(longArrayOf(0, 180, 100, 200), -1)
                }
            }
        } catch (e: Exception) {
            // Ignore vibration error
        }

        // Post heads-up system notification
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("selected_symbol", symbol)
        }

        val pendingIntent = PendingIntent.getActivity(
            context,
            symbol.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val soundUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
        val icon = when (alertType) {
            "Breaks up!" -> R.drawable.break_up_mini
            "Going up!" -> R.drawable.going_up_mini
            "Imminent movement!" -> R.drawable.squeeze_mini
            "Breaks down!" -> R.drawable.break_down_mini
            else -> R.drawable.going_up_mini
        }

        val title = "🚀 $symbol $alertType"
        val body = "Price: $$priceFormatted • 24h Change: $changeFormatted • Bybit Linear Perpetual"

        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(icon)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText("$body\nTap to inspect live candlestick chart inside app."))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setDefaults(NotificationCompat.DEFAULT_ALL)
            .setSound(soundUri)
            .setVibrate(longArrayOf(0, 250, 150, 250))
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)

        try {
            NotificationManagerCompat.from(context).notify(symbol.hashCode(), builder.build())
            return true
        } catch (e: SecurityException) {
            // Permission missing
            return false
        }
    }
}
