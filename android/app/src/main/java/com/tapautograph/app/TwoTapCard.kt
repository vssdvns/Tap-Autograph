package com.tapautograph.app

/** TAP2 binds a synthetic wallet session to one order; it does not authenticate a merchant. */
class TwoTapCard(private val now:()->Long={System.nanoTime()/1_000_000}) {
    private val ink=AutographApdu(now)
    private var bytes=byteArrayOf()
    private var binding=byteArrayOf()
    private var deadline=0L
    private var amount=0L
    private var phase=0
    private var acceptedOffer=false
    private var selected=false
    private var automatic=false
    private var cardIndex=0
    private var autographDeadline=0L
    @Volatile var timedOut=false; private set
    @Volatile var active=false; private set
    @Synchronized fun start(packet:ByteArray, autoShare:Boolean=false, selectedCard:Int=0) { CompactInkCodec.decode(packet);stop();timedOut=false;automatic=autoShare;cardIndex=selectedCard.coerceIn(0,1);bytes=packet.copyOf();active=true;deadline=now()+180000 }
    @Synchronized fun stop(){ink.disarm();bytes.fill(0);bytes=byteArrayOf();binding=byteArrayOf();active=false;phase=0;deadline=0;autographDeadline=0;selected=false;acceptedOffer=false;amount=0}
    private fun expire(){if(active&&(now()>=deadline || (autographDeadline>0&&now()>=autographDeadline))){stop();timedOut=true}}
    @Synchronized fun remainingSeconds():Long {expire();return if(autographDeadline>0)((autographDeadline-now()+999)/1000).coerceAtLeast(0) else 0}
    private fun paymentAccepted(){if(automatic&&autographDeadline==0L)autographDeadline=now()+45000}
    @Synchronized fun state():Int {expire();if(phase==4){if(ink.status()=="Transfer acknowledged by reader"){phase=5;bytes.fill(0);bytes=byteArrayOf()}else if(ink.remainingSeconds()==0L)stop()};return phase}
    @Synchronized fun total():Long=amount
    @Synchronized fun approve(){expire();if(active&&phase==3){ink.arm(bytes);phase=4}}
    @Synchronized fun skip(){if(active&&phase in listOf(2,3,4)){ink.disarm();bytes.fill(0);bytes=byteArrayOf();phase=6}}
    @Synchronized fun deactivate(){selected=false;ink.deactivate()}
    private fun sw(n:Int)=byteArrayOf((n ushr 8).toByte(),n.toByte())
    @Synchronized fun process(cmd:ByteArray):ByteArray {
        expire();if(!active)return sw(0x6985)
        if(cmd.contentEquals(AutographApdu.SELECT)){
            selected=true
            return byteArrayOf(2,state().toByte())+sw(0x9000)
        }
        if(cmd.size<5||cmd.size!=5+(cmd[4].toInt()and 255))return sw(0x6700)
        if((cmd[0].toInt()and 255)!=0x80)return sw(0x6e00)
        if(!selected||cmd[2]!=0.toByte()||cmd[3]!=0.toByte())return sw(0x6985)
        val ins=cmd[1].toInt()and 255
        if(ins==0x30){
            if(cmd.size!=42)return sw(0x6700)
            val b=cmd.copyOfRange(5,37);val a=cmd.copyOfRange(37,41).fold(0L){v,x->(v shl 8)+(x.toInt()and 255)};val p=cmd[41].toInt()and 255
            if(a>1000000||p !in 1..2)return sw(0x6a80)
            if(p==1){
                if(phase==0){binding=b;amount=a;phase=1;acceptedOffer=true}
                else if(!binding.contentEquals(b)||amount!=a||phase !in 1..2)return sw(0x6985)
            }else{
                if(!binding.contentEquals(b)||phase !in 1..4)return sw(0x6985)
                if(phase in 1..2){paymentAccepted();amount=a;phase=3;acceptedOffer=true;if(automatic)approve()}
                else if(amount!=a)return sw(0x6985)
            }
            return (if(automatic)byteArrayOf(state().toByte(),cardIndex.toByte()) else byteArrayOf(state().toByte()))+sw(0x9000)
        }
        if(ins==0x33&&cmd.size==5&&phase==4)return ink.process(AutographApdu.SELECT)
        if(ins==0x31&&cmd.size==5)return byteArrayOf(state().toByte())+sw(0x9000)
        if(ins==0x32&&cmd.size==6&&cmd[5]==1.toByte()&&acceptedOffer&&phase in 1..2){phase=2;paymentAccepted();return sw(0x9000)}
        if(phase==4&&ins in listOf(0x10,0x20))return ink.process(cmd).also{state()}
        return sw(0x6985)
    }
}
