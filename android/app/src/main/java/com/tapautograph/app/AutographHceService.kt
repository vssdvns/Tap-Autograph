package com.tapautograph.app

import android.app.KeyguardManager
import android.content.Context
import android.content.Intent
import android.nfc.cardemulation.HostApduService
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.PowerManager

object AutographSharing { val card = AutographApdu(); val checkout = TwoTapCard() }
class AutographHceService : HostApduService() {
    private val handler=Handler(Looper.getMainLooper())
    @Volatile private var packet:ByteArray?=null
    private var loading=false
    private var packetRevision=-1L
    private var destroyed=false
    private var launched=false
    private var timeoutHandled=false
    private val expiry=object:Runnable {
        override fun run(){
            val card=AutographSharing.checkout
            val phase=card.state()
            if(card.timedOut){WalletTap.cooldown();timeoutHandled=true;return}
            if(phase in listOf(5,6)){
                WalletTap.cooldown()
                handler.postDelayed({if(card.state() in listOf(5,6))card.stop()},3000)
                return
            }
            if(card.active)handler.postDelayed(this,200)
        }
    }
    private fun loadPacket(){
        if(loading)return
        loading=true
        val revision=WalletTap.designRevision
        Thread {
            val encoded=runCatching{LocalRepository(application).read().autograph?.let{CompactInkCodec.encode(it)}}.getOrNull()
            handler.post { if(!destroyed){packet?.fill(0);packet=encoded;packetRevision=revision;loading=false}else encoded?.fill(0) }
        }.start()
    }
    override fun onCreate(){super.onCreate();if(WalletTap.enabled(this))loadPacket()}
    override fun processCommandApdu(commandApdu:ByteArray,extras:Bundle?):ByteArray {
        val locked=(getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager).isDeviceLocked
        val awake=(getSystemService(Context.POWER_SERVICE) as PowerManager).isInteractive
        if(locked || !awake) {AutographSharing.card.disarm();if(AutographSharing.checkout.active){AutographSharing.checkout.stop();WalletTap.cooldown()};return byteArrayOf(0x69,0x85.toByte())}
        val card=AutographSharing.checkout
        card.state() // Enforce timeout before considering any new session.
        if(card.timedOut && !timeoutHandled){WalletTap.cooldown();timeoutHandled=true}
        if(!card.active && commandApdu.contentEquals(AutographApdu.SELECT) && WalletTap.enabled(this) && WalletTap.canStart() && AutographSharing.card.remainingSeconds()==0L){
            if(packetRevision!=WalletTap.designRevision){packet?.fill(0);packet=null}
            val saved=packet
            if(saved==null){loadPacket();return byteArrayOf(0x69,0x85.toByte())}
            card.start(saved,autoShare=true,selectedCard=WalletTap.selectedCard(this));packet=null;launched=false;timeoutHandled=false;handler.removeCallbacks(expiry);handler.post(expiry)
        }
        val response=if(card.active)card.process(commandApdu) else AutographSharing.card.process(commandApdu)
        if(card.active && card.state() in 1..4 && !launched){
            launched=true
            // System NFC binding may permit this launch; Samsung/Android can still suppress it.
            runCatching{startActivity(Intent(this,MainActivity::class.java).setAction(WalletTap.ACTION).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP))}
        }
        return response
    }
    override fun onDeactivated(reason:Int) {AutographSharing.card.deactivate();AutographSharing.checkout.deactivate()}
    override fun onDestroy(){destroyed=true;handler.removeCallbacksAndMessages(null);packet?.fill(0);packet=null;super.onDestroy()}
}
