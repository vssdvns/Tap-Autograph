package com.tapautograph.app

import java.io.*
import java.security.MessageDigest
import java.util.zip.*
import kotlin.math.*

/** TAG2: lossless pixel mask for compatible scanline artwork; same original drawing grid. */
object RasterInkCodec {
    fun encodeOrNull(ink:Autograph):ByteArray? {
        if(ink.color ushr 24 != 255L)return null // Overlapping translucent strokes need vector blending.
        val h=(.95f/ink.width).roundToInt()
        val w=(ink.aspect*h).roundToInt()
        if(w !in 3..160 || h !in 24..160 || abs(ink.aspect-w.toFloat()/h)>.00001f)return null
        val bits=ByteArray((w*h+7)/8)
        for(stroke in ink.strokes){
            val p=stroke.points
            if(p.size!=2 || p.any{it.time!=0L || it.pressure!=1f || it.tool!=0} || p[0].y!=p[1].y)return null
            fun grid(v:Float,n:Int):Int? {val x=v*n-.5f;val i=x.roundToInt();return i.takeIf{it in 0 until n && abs(x-i)<.001f}}
            val y=grid(p[0].y,h) ?: return null;val first=grid(p[0].x,w) ?: return null;val last=grid(p[1].x,w) ?: return null
            if(first>last)return null
            for(x in first..last){val index=y*w+x;bits[index/8]=(bits[index/8].toInt() or (1 shl (7-index%8))).toByte()}
        }
        val body=ByteArrayOutputStream().also{b->DataOutputStream(b).use{it.writeShort(w);it.writeShort(h);it.writeInt(ink.color.toInt());it.writeShort((ink.width*65535).roundToInt());it.write(bits)}}.toByteArray()
        val zipped=ByteArrayOutputStream().also{b->val d=Deflater(9);try{DeflaterOutputStream(b,d).use{it.write(body)}}finally{d.end()}}.toByteArray()
        return ByteArrayOutputStream().also{b->DataOutputStream(b).use{it.writeBytes("TAG2");it.writeByte(1);it.writeInt(body.size);it.writeInt(CRC32().also{c->c.update(body)}.value.toInt());it.write(zipped)}}.toByteArray()
    }
    fun decode(packet:ByteArray):Autograph {
        require(packet.size in 14..65536 && String(packet,0,4,Charsets.US_ASCII)=="TAG2" && packet[4]==1.toByte()){ "Invalid raster header." }
        val header=DataInputStream(ByteArrayInputStream(packet,5,8));val length=header.readInt();val crc=header.readInt().toLong() and 0xffffffffL
        require(length in 12..3210){"Invalid raster bounds."}
        val inflater=Inflater();val body=ByteArray(length)
        try {
            inflater.setInput(packet,13,packet.size-13);var offset=0
            while(!inflater.finished() && offset<length){val n=inflater.inflate(body,offset,length-offset);require(n>0){"Incomplete raster."};offset+=n}
            require(inflater.finished()&&inflater.remaining==0&&offset==length){"Raster length mismatch."}
        }finally{inflater.end()}
        require(CRC32().also{it.update(body)}.value==crc){"Raster CRC mismatch."}
        val input=DataInputStream(ByteArrayInputStream(body));val w=input.readUnsignedShort();val h=input.readUnsignedShort();val color=input.readInt().toLong() and 0xffffffffL;val width=input.readUnsignedShort()/65535f
        require(w in 3..160 && h in 3..160 && w.toFloat()/h<20 && width in .001f..0.04f && body.size==10+(w*h+7)/8){"Invalid raster dimensions."}
        val bits=body.copyOfRange(10,body.size)
        if(w*h%8!=0)require(bits.last().toInt() and ((1 shl (8-w*h%8))-1)==0){"Noncanonical padding."}
        fun on(x:Int,y:Int):Boolean {val i=y*w+x;return bits[i/8].toInt() and (1 shl (7-i%8))!=0}
        val strokes=mutableListOf<InkStroke>()
        for(y in 0 until h){var x=0;while(x<w){if(!on(x,y)){x++;continue};val first=x;while(x+1<w&&on(x+1,y))x++;strokes.add(InkStroke(listOf(InkPoint((first+.5f)/w,(y+.5f)/h,0),InkPoint((x+.5f)/w,(y+.5f)/h,0))));require(strokes.size<=1000){"Too many raster runs."};x++}}
        require(strokes.isNotEmpty()){ "Empty raster." }
        val id=MessageDigest.getInstance("SHA-256").digest(packet).take(12).joinToString(""){"%02x".format(it.toInt() and 255)}
        return Autograph(id="compact-$id",aspect=w.toFloat()/h,width=width,color=color,strokes=strokes).also{require(it.valid())}
    }
}
