package com.tapautograph.app

import java.io.*
import java.util.zip.*

/** TAG3 reorders the same TAG1 quantized values into columns; no extra visual loss. */
object ColumnInkCodec {
    private fun DataInputStream.variable(max:Int):Int {
        var value=0L
        for(i in 0..4){val b=readUnsignedByte();value=value or ((b and 127).toLong() shl (7*i))
            if(b and 128==0){require((i==0 || b!=0)&&value<=max){"Invalid column integer."};return value.toInt()}}
        error("Long column integer.")
    }
    private fun DataOutputStream.variable(value:Int){var n=value;while(n>=128){writeByte((n and 127) or 128);n=n ushr 7};writeByte(n)}
    private fun zig(n:Int)=(n shl 1) xor (n shr 31)
    private fun unzig(n:Int)=(n ushr 1) xor -(n and 1)
    private fun envelope(magic:String,body:ByteArray,compressed:Boolean):ByteArray {
        var payload=body
        if(compressed)for(strategy in listOf(Deflater.DEFAULT_STRATEGY,Deflater.FILTERED)){
            val d=Deflater(9);d.setStrategy(strategy)
            val candidate=try{ByteArrayOutputStream().also{out->DeflaterOutputStream(out,d).use{it.write(body)}}.toByteArray()}finally{d.end()}
            if(candidate.size<payload.size)payload=candidate
        }
        return ByteArrayOutputStream().also{out->DataOutputStream(out).use{
            it.writeBytes(magic);it.writeByte(if(payload===body)0 else 1);it.writeInt(body.size)
            it.writeInt(CRC32().also{c->c.update(body)}.value.toInt());it.write(payload)
        }}.toByteArray()
    }
    fun encode(ink:Autograph,predictive:Boolean=false):ByteArray {
        val raw=CompactInkCodec.encodeBody(ink)
        val input=DataInputStream(ByteArrayInputStream(raw));val properties=ByteArray(8);input.readFully(properties)
        val count=input.variable(1000);val counts=IntArray(count)
        val streams=List(5){ByteArrayOutputStream()};val columns=streams.map{DataOutputStream(it)}
        for(s in 0 until count){counts[s]=input.variable(30000)
            val previous=IntArray(2)
            repeat(counts[s]){
                for(c in 0..1){val delta=unzig(input.variable(8190));columns[c].variable(zig(if(predictive)delta-previous[c] else delta));previous[c]=delta}
                columns[2].variable(input.variable(86400000));columns[3].writeByte(input.readUnsignedByte());columns[4].writeByte(input.readUnsignedByte())}}
        val body=ByteArrayOutputStream().also{out->DataOutputStream(out).use{it.write(properties);it.variable(count);counts.forEach{n->it.variable(n)};streams.forEach{stream->it.write(stream.toByteArray())}}}.toByteArray()
        return envelope(if(predictive)"TAG4" else "TAG3",body,true).also{require(it.size<=CompactInkCodec.MAX_PACKET)}
    }
    fun decode(packet:ByteArray):Autograph {
        require(packet.size in 14..65536 && String(packet,0,4,Charsets.US_ASCII) in listOf("TAG3","TAG4") && packet[4].toInt() in 0..1){"Invalid column header."}
        val predictive=packet[3]=='4'.code.toByte()
        val header=DataInputStream(ByteArrayInputStream(packet,5,8));val length=header.readInt();val crc=header.readInt().toLong() and 0xffffffffL
        require(length in 1..CompactInkCodec.MAX_BODY){"Invalid column bounds."}
        val payload=packet.copyOfRange(13,packet.size)
        val body=if(packet[4].toInt()==1)CompactInkCodec.inflate(payload,length)else payload.also{require(it.size==length)}
        require(CRC32().also{it.update(body)}.value==crc){"Column checksum mismatch."}
        val input=DataInputStream(ByteArrayInputStream(body));val properties=ByteArray(8);input.readFully(properties)
        val count=input.variable(1000);require(count>0)
        var total=0;val counts=IntArray(count){input.variable(30000).also{require(it>0 && total+it<=30000);total+=it}}
        val fields=List(5){column->IntArray(total){if(column<3)input.variable(if(column<2)(if(predictive)16380 else 8190) else 86400000)else input.readUnsignedByte()}}
        require(input.available()==0){"Trailing column data."}
        var point=0
        val raw=ByteArrayOutputStream().also{out->DataOutputStream(out).use{it.write(properties);it.variable(count)
            counts.forEach{n->it.variable(n);val previous=IntArray(2);repeat(n){_->
                for(c in 0..1){val delta=unzig(fields[c][point])+(if(predictive)previous[c] else 0);require(delta in -4095..4095){"Invalid predicted delta."};previous[c]=delta;it.variable(zig(delta))}
                it.variable(fields[2][point]);it.writeByte(fields[3][point]);it.writeByte(fields[4][point]);point++}}}}.toByteArray()
        return CompactInkCodec.decodeBody(raw,packet)
    }
}
