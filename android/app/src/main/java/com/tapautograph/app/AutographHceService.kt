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
    private var packet:ByteArray?=null
    private var loading=false
    private var packetRevision=-1L
    private var destroyed=false
    private var generation=0L
    private var pending:Pair<Long,ByteArray>?=null
    private var lastLaunch=0L
    private var notifiedPhase=-1
    private var timeoutHandled=false
    private val expiry=object:Runnable {
        override fun run(){
            val card=AutographSharing.checkout
            val phase=card.state()
            if(card.timedOut){WalletTap.cooldown();timeoutHandled=true;TapNotice.clear(this@AutographHceService);return}
            if(phase in listOf(5,6)){
                WalletTap.cooldown();TapNotice.clear(this@AutographHceService)
                handler.postDelayed({if(card.state() in listOf(5,6))card.stop()},3000)
                return
            }
            if(card.active)handler.postDelayed(this,200)
        }
    }
    private fun unlocked()=!(getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager).isDeviceLocked && (getSystemService(Context.POWER_SERVICE) as PowerManager).isInteractive
    private fun loadPacket(){
        if(loading)return
        loading=true
        val revision=WalletTap.designRevision
        Thread {
            val encoded=runCatching{LocalRepository(application).read().autograph?.let{TapPacket.encode(it)}}.getOrNull()
            handler.post {
                if(destroyed){encoded?.fill(0);return@post}
                packet?.fill(0);packet=encoded;packetRevision=revision;loading=false
                val waiting=pending;pending=null
                if(waiting!=null && waiting.first==generation){
                    // A cold-start SELECT waits for disk/encoding instead of being declined.
                    val reply=if(revision==WalletTap.designRevision && unlocked())handle(waiting.second) else declined()
                    sendResponseApdu(reply)
                }
            }
        }.start()
    }
    override fun onCreate(){super.onCreate();CheckoutFeedback.warmUp(this);if(WalletTap.enabled(this))loadPacket()}
    private fun declined()=byteArrayOf(0x69,0x85.toByte())
    override fun processCommandApdu(commandApdu:ByteArray,extras:Bundle?):ByteArray? {
        if(!unlocked()){
            AutographSharing.card.disarm()
            if(AutographSharing.checkout.active){AutographSharing.checkout.stop();WalletTap.cooldown()}
            return declined()
        }
        val card=AutographSharing.checkout
        card.state()
        if(card.timedOut && !timeoutHandled){WalletTap.cooldown();timeoutHandled=true}
        if(canAutoStart(commandApdu)){
            if(packetRevision!=WalletTap.designRevision){packet?.fill(0);packet=null}
            if(packet==null){pending=generation to commandApdu.copyOf();loadPacket();return null}
        }
        return handle(commandApdu)
    }
    private fun canAutoStart(cmd:ByteArray)=!AutographSharing.checkout.active && cmd.contentEquals(AutographApdu.SELECT) && WalletTap.enabled(this) && WalletTap.canStart() && AutographSharing.card.remainingSeconds()==0L
    private fun handle(cmd:ByteArray):ByteArray {
        val card=AutographSharing.checkout
        if(canAutoStart(cmd)){
            val saved=packet ?: return declined()
            card.start(saved,autoShare=true,selectedCard=WalletTap.selectedCard(this));packet=null
            TapActivity.hidden=false;lastLaunch=0;notifiedPhase=-1;timeoutHandled=false
            handler.removeCallbacks(expiry);handler.post(expiry)
        }
        val response=if(card.active)card.process(cmd) else AutographSharing.card.process(cmd)
        val phase=card.state()
        if(card.claimPaymentFeedback())CheckoutFeedback.payment(this)
        // Wait for a valid order offer, rather than showing UI for a mere AID probe.
        if(card.active && phase in 1..4){
            if(phase!=notifiedPhase){if(!TapActivity.visible)TapNotice.show(this,phase);notifiedPhase=phase}
            val now=android.os.SystemClock.elapsedRealtime()
            if(!TapActivity.visible && !TapActivity.hidden && now-lastLaunch>1500){
                lastLaunch=now
                runCatching{startActivity(Intent(this,TapActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP))}
            }
        }
        return response
    }
    override fun onDeactivated(reason:Int) {generation++;pending=null;AutographSharing.card.deactivate();AutographSharing.checkout.deactivate()}
    override fun onDestroy(){destroyed=true;generation++;pending=null;handler.removeCallbacksAndMessages(null);packet?.fill(0);packet=null;super.onDestroy()}
}
