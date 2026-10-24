package com.tapautograph.app

import org.junit.Assert.*
import org.junit.Test
import java.nio.ByteBuffer
import kotlin.math.*

class DialAndFeedbackTest {
    @Test fun clockwiseSlotsWrapAndCenterAndFarOutsideCancel(){
        repeat(6){i->val a=(-90+i*60)*Math.PI/180;assertEquals(i,DialGeometry.slot((cos(a)*100).toFloat(),(sin(a)*100).toFloat()))}
        assertNull(DialGeometry.slot(0f,0f));assertNull(DialGeometry.slot(27f,0f))
        assertNull(DialGeometry.slot(211f,0f));assertNull(DialGeometry.slot(Float.NaN,40f))
        assertEquals(0,DialGeometry.slot(-1f,-100f));assertEquals(0,DialGeometry.slot(1f,-100f))
    }
    private val packet get()=CompactInkCodec.encode(Autograph(aspect=1.8f,strokes=listOf(InkStroke(listOf(InkPoint(.1f,.2f,0),InkPoint(.8f,.7f,200))))))
    private fun offer(phase:Int)=AutographApdu.command(0x30,ByteArray(32){it.toByte()}+ByteBuffer.allocate(5).putInt(1000).put(phase.toByte()).array())
    @Test fun chimeIsClaimedOnlyAfterAcceptanceAndDoesNotRepeatOnAckRetapOrFinalOffer(){
        val c=TwoTapCard();c.start(packet,autoShare=true)
        assertFalse(c.claimPaymentFeedback());c.process(AutographApdu.SELECT);c.process(offer(1));assertFalse(c.claimPaymentFeedback())
        c.process(AutographApdu.command(0x32,byteArrayOf(1)));assertTrue(c.claimPaymentFeedback());assertFalse(c.claimPaymentFeedback())
        c.process(AutographApdu.command(0x32,byteArrayOf(1)));c.deactivate();c.process(AutographApdu.SELECT);c.process(offer(2))
        assertFalse(c.claimPaymentFeedback());c.stop();assertFalse(c.claimPaymentFeedback())
        c.start(packet,autoShare=true);c.process(AutographApdu.SELECT);c.process(offer(1));c.process(offer(2));assertTrue(c.claimPaymentFeedback());assertFalse(c.claimPaymentFeedback())
    }
    @Test fun expiredOrUnacceptedOrderCannotChime(){
        var now=0L;val c=TwoTapCard{now};c.start(packet,autoShare=true);c.process(AutographApdu.SELECT);c.process(offer(1))
        now=180001;assertFalse(c.claimPaymentFeedback())
        c.start(packet,autoShare=true);c.process(AutographApdu.SELECT);c.process(offer(1));c.process(AutographApdu.command(0x32,byteArrayOf(1)))
        now+=45000;assertFalse(c.claimPaymentFeedback())
    }
}
