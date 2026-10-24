package com.tapautograph.app

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat

/** User-tappable fallback when the OEM suppresses the compact activity launch. */
object TapNotice {
    private const val ID=2048
    fun show(context:Context,phase:Int)=runCatching {
        val manager=context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(NotificationChannel("tap-checkout","Tap checkout",NotificationManager.IMPORTANCE_HIGH))
        val open=PendingIntent.getActivity(context,0,Intent(context,TapActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP),PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        manager.notify(ID,NotificationCompat.Builder(context,"tap-checkout").setSmallIcon(com.tapautograph.app.R.drawable.ic_autograph)
            .setContentTitle(if(phase>=2)"Demo payment accepted" else "Tap Autograph checkout")
            .setContentText(if(phase>=2)"Tap again for your autograph. Open to choose a favorite." else "Open your tap card")
            .setContentIntent(open).setOnlyAlertOnce(true).setAutoCancel(true).setTimeoutAfter(45000).build())
    }
    fun clear(context:Context)=context.getSystemService(NotificationManager::class.java).cancel(ID)
}
