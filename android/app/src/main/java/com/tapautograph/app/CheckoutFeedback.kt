package com.tapautograph.app

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager

/** Short original chime, played once per accepted demo order, independent of UI lifetime. */
object CheckoutFeedback {
    private val handler=Handler(Looper.getMainLooper())
    private var pool:SoundPool?=null
    private var soundId=0
    private var ready=false
    private var pending=false
    fun warmUp(context:Context){
        if(pool!=null)return
        runCatching {
            val app=context.applicationContext
            val soundPool=SoundPool.Builder().setMaxStreams(1).setAudioAttributes(AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_NOTIFICATION_EVENT).setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build()).build()
            pool=soundPool
            soundPool.setOnLoadCompleteListener{_,id,status->handler.post{
                if(status==0 && id==soundId){ready=true;if(pending && WalletTap.sound(app))soundPool.play(soundId,.55f,.55f,1,0,1f)}
                pending=false
            }}
            soundId=soundPool.load(app,R.raw.payment_chime,1)
        }
    }
    fun payment(context:Context){
        val app=context.applicationContext
        handler.post {
            if(WalletTap.sound(app)){
                warmUp(app)
                runCatching{if(ready)pool?.play(soundId,.55f,.55f,1,0,1f) else pending=true}
            }
            if(WalletTap.haptics(app))runCatching{
                val vibrator=if(Build.VERSION.SDK_INT>=31)app.getSystemService(VibratorManager::class.java).defaultVibrator else app.getSystemService(Vibrator::class.java)
                vibrator.vibrate(if(Build.VERSION.SDK_INT>=29)VibrationEffect.createPredefined(VibrationEffect.EFFECT_DOUBLE_CLICK) else VibrationEffect.createOneShot(35,VibrationEffect.DEFAULT_AMPLITUDE))
            }
        }
    }
}
