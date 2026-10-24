package com.tapautograph.app
import org.junit.Assert.*
import org.junit.Test
import java.nio.ByteBuffer
import java.io.File
import kotlinx.serialization.json.*

class TwoTapCardTest {
 private val packet get()=CompactInkCodec.encode(Autograph(aspect=1.8f,strokes=listOf(InkStroke(listOf(InkPoint(.1f,.2f,0),InkPoint(.8f,.7f,200))))))
 private val binding=ByteArray(32){it.toByte()}
 private fun offer(phase:Int,amount:Int=1000,b:ByteArray=binding)=AutographApdu.command(0x30,b+ByteBuffer.allocate(5).putInt(amount).put(phase.toByte()).array())
 private fun sw(b:ByteArray)=AutographApdu.hex(b.takeLast(2).toByteArray())
 private fun ready():TwoTapCard=TwoTapCard().also{it.start(packet);it.process(AutographApdu.SELECT);it.process(offer(1));it.process(AutographApdu.command(0x32,byteArrayOf(1)))}
 @Test fun inkRequiresFinalReviewAndMatchingBinding(){
  val c=ready();assertEquals(2,c.state());assertEquals("6985",sw(c.process(offer(2,b=ByteArray(32)))));assertEquals("9000",sw(c.process(offer(2,1200))));assertEquals(1200L,c.total());assertEquals(3,c.state());assertEquals("6985",sw(c.process(AutographApdu.command(0x33,byteArrayOf()))));c.approve();assertEquals(4,c.state());assertEquals("6985",sw(c.process(offer(2,1300))));assertEquals("9000",sw(c.process(AutographApdu.command(0x33,byteArrayOf()))))
 }
 @Test fun expiryStopAndSkipReleaseNoInk(){
  var t=0L;val c=TwoTapCard{t};c.start(packet);t=180001;assertEquals(0,c.state());assertFalse(c.active);assertEquals("6985",sw(c.process(AutographApdu.SELECT)))
  val other=ready();other.skip();assertEquals(6,other.state());assertEquals("6985",sw(other.process(AutographApdu.command(0x33,byteArrayOf()))));other.stop();assertFalse(other.active)
 }
 @Test fun firstAckCanRetryAndFinalOfferRecoversLostAck(){
  val c=TwoTapCard();c.start(packet);c.process(AutographApdu.SELECT);c.process(offer(1));assertEquals(1,c.state());assertEquals("9000",sw(c.process(offer(1))));assertEquals("9000",sw(c.process(offer(2,1100))));assertEquals(3,c.state())
 }
 @Test fun fullTranscriptIsAcknowledgedOnlyAfterAllInk(){
  val c=TwoTapCard();c.start(packet);val exchanges=mutableListOf<JsonObject>()
  fun send(cmd:ByteArray):ByteArray{val out=c.process(cmd);exchanges.add(buildJsonObject{put("command",AutographApdu.hex(cmd));put("response",AutographApdu.hex(out))});assertEquals("9000",sw(out));return out.dropLast(2).toByteArray()}
  send(AutographApdu.SELECT);send(offer(1));send(AutographApdu.command(0x32,byteArrayOf(1)));c.deactivate();send(AutographApdu.SELECT);send(offer(2,1200));c.approve();send(AutographApdu.command(0x31,byteArrayOf()));val meta=send(AutographApdu.command(0x33,byteArrayOf()));val session=meta.copyOfRange(1,9)
  val count=((meta[15].toInt()and 255)shl 8)+(meta[16].toInt()and 255)
  for(i in 0 until count)send(AutographApdu.command(0x10,session+byteArrayOf((i ushr 8).toByte(),i.toByte())))
  send(AutographApdu.command(0x20,session+AutographApdu.digest(packet)));assertEquals(5,c.state())
  val fixture=buildJsonObject{put("protocol","TAP2");put("simulated",true);put("packet",java.util.Base64.getEncoder().encodeToString(packet));put("exchanges",JsonArray(exchanges))}
  File("build/two-tap-fixture.json").apply{parentFile.mkdirs();writeText(fixture.toString())}
 }
}
