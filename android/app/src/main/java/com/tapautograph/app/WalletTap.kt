package com.tapautograph.app

import android.app.Activity
import android.app.role.RoleManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.nfc.NfcAdapter
import android.nfc.cardemulation.CardEmulation
import android.os.Build
import android.os.SystemClock

/** Explicit, persistent opt-in. Only synthetic TAP2 orders are supported. */
object WalletTap {
    const val ACTION = "com.tapautograph.app.OPEN_TAP"
    @Volatile var designRevision=0L
    private fun prefs(context:Context)=context.getSharedPreferences("wallet-tap",Context.MODE_PRIVATE)
    fun enabled(context:Context)=prefs(context).getBoolean("automatic",false)
    fun enable(context:Context,value:Boolean){prefs(context).edit().putBoolean("automatic",value).apply();if(!value)AutographSharing.checkout.stop()}
    fun selectedCard(context:Context)=prefs(context).getInt("selected-card",0).coerceIn(0,1)
    fun selectCard(context:Context,index:Int){prefs(context).edit().putInt("selected-card",index.coerceIn(0,1)).apply()}
    @Volatile private var blockedUntil=0L
    fun cooldown(){blockedUntil=SystemClock.elapsedRealtime()+60000}
    fun canStart()=SystemClock.elapsedRealtime()>=blockedUntil
    fun resetCooldown(){blockedUntil=0L}
    fun isDefault(context:Context):Boolean=runCatching {
        if(Build.VERSION.SDK_INT>=35) context.getSystemService(RoleManager::class.java).isRoleHeld(RoleManager.ROLE_WALLET)
        else NfcAdapter.getDefaultAdapter(context)?.let{CardEmulation.getInstance(it).isDefaultServiceForCategory(ComponentName(context,AutographHceService::class.java),CardEmulation.CATEGORY_PAYMENT)}==true
    }.getOrDefault(false)
    fun requestDefault(activity:Activity){
        if(Build.VERSION.SDK_INT>=35){
            val roles=activity.getSystemService(RoleManager::class.java)
            if(roles.isRoleAvailable(RoleManager.ROLE_WALLET)){
                activity.startActivityForResult(roles.createRequestRoleIntent(RoleManager.ROLE_WALLET),910);return
            }
        }
        activity.startActivity(Intent(CardEmulation.ACTION_CHANGE_DEFAULT).putExtra(CardEmulation.EXTRA_CATEGORY,CardEmulation.CATEGORY_PAYMENT).putExtra(CardEmulation.EXTRA_SERVICE_COMPONENT,ComponentName(activity,AutographHceService::class.java)))
    }
}
