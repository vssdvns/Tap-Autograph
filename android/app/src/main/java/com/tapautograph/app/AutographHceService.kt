package com.tapautograph.app

import android.app.KeyguardManager
import android.content.Context
import android.nfc.cardemulation.HostApduService
import android.os.Bundle
import android.os.PowerManager

object AutographSharing { val card = AutographApdu(); val checkout = TwoTapCard() }
class AutographHceService : HostApduService() {
    override fun processCommandApdu(commandApdu:ByteArray,extras:Bundle?):ByteArray {
        val locked=(getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager).isDeviceLocked
        val awake=(getSystemService(Context.POWER_SERVICE) as PowerManager).isInteractive
        if(locked || !awake) { AutographSharing.card.disarm();AutographSharing.checkout.stop();return byteArrayOf(0x69,0x85.toByte()) }
        return if(AutographSharing.checkout.active) AutographSharing.checkout.process(commandApdu) else AutographSharing.card.process(commandApdu)
    }
    override fun onDeactivated(reason:Int) { AutographSharing.card.deactivate();AutographSharing.checkout.deactivate() }
}
