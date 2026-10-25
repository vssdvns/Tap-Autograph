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
    fun encode(ink:Autograph):ByteArray {
        val raw=CompactInkCodec.encodeBody(ink)
        val input=DataInputStream(ByteArrayInputStream(raw));val properties=ByteArray(8);input.readFully(properties)
        val count=input.variable(1000);val counts=IntArray(count)
        val streams=List(5){ByteArrayOutputStream()};val columns=streams.map{DataOutputStream(it)}
        for(s in 0 until count){counts[s]=input.variable(30000)
            repeat(counts[s]){columns[0].variable(input.variable(8190));columns[1].variable(input.variable(8190));columns[2].variable(input.variable(86400000));columns[3].writeByte(input.readUnsignedByte());columns[4].writeByte(input.readUnsignedByte())}}
        val body=ByteArrayOutputStream().also{out->DataOutputStream(out).use{it.write(properties);it.variable(count);counts.forEach{n->it.variable(n)};streams.forEach{stream->it.write(stream.toByteArray())}}}.toByteArray()
        return envelope("TAG3",body,true).also{require(it.size<=CompactInkCodec.MAX_PACKET)}
    }
    fun decode(packet:ByteArray):Autograph {
        require(packet.size in 14..65536 && String(packet,0,4,Charsets.US_ASCII)=="TAG3" && packet[4].toInt() in 0..1){"Invalid column header."}
        val header=DataInputStream(ByteArrayInputStream(packet,5,8));val length=header.readInt();val crc=header.readInt().toLong() and 0xffffffffL
        require(length in 1..CompactInkCodec.MAX_BODY){"Invalid column bounds."}
        val payload=packet.copyOfRange(13,packet.size)
        val body=if(packet[4].toInt()==1)CompactInkCodec.inflate(payload,length)else payload.also{require(it.size==length)}
        require(CRC32().also{it.update(body)}.value==crc){"Column checksum mismatch."}
        val input=DataInputStream(ByteArrayInputStream(body));val properties=ByteArray(8);input.readFully(properties)
        val count=input.variable(1000);require(count>0)
        var total=0;val counts=IntArray(count){input.variable(30000).also{require(it>0 && total+it<=30000);total+=it}}
        val fields=List(5){column->IntArray(total){if(column<3)input.variable(if(column<2)8190 else 86400000)else input.readUnsignedByte()}}
        require(input.available()==0){"Trailing column data."}
        var point=0
        val raw=ByteArrayOutputStream().also{out->DataOutputStream(out).use{it.write(properties);it.variable(count)
            counts.forEach{n->it.variable(n);repeat(n){_->for(c in 0..2)it.variable(fields[c][point]);it.writeByte(fields[3][point]);it.writeByte(fields[4][point]);point++}}}}.toByteArray()
        return CompactInkCodec.decodeBody(raw,packet)
    }
}
