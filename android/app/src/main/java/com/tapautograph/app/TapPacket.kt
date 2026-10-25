package com.tapautograph.app

import kotlin.math.abs

/** NFC copy only. Keep the gallery original and every stroke endpoint. */
object TapPacket {
    fun encode(ink:Autograph):ByteArray {
        val quick=prepare(ink)
        val vector=CompactInkCodec.encode(quick)
        val raster=RasterInkCodec.encodeOrNull(quick)
        val column=runCatching{ColumnInkCodec.encode(quick)}.getOrNull()
        val predictive=runCatching{ColumnInkCodec.encode(quick,true)}.getOrNull()
        return listOfNotNull(vector,raster,column,predictive).minBy{it.size}
    }
    internal fun prepare(ink:Autograph):Autograph {
        val compact=ink.copy(strokes=ink.strokes.map { stroke ->
            val p=stroke.points
            if(p.size<3)stroke else {
                val keep=BooleanArray(p.size);keep[0]=true;keep[p.lastIndex]=true
                val stack=java.util.ArrayDeque<Pair<Int,Int>>()
                // Bound worst-case work for highly jagged 30k-point drawings.
                val segment=if(p.size>2048)256 else p.lastIndex
                var start=0
                while(start<p.lastIndex){val end=minOf(start+segment,p.lastIndex);keep[start]=true;keep[end]=true;stack.add(start to end);start=end}
                while(stack.isNotEmpty()){
                    val (lo,hi)=stack.removeLast();val a=p[lo];val b=p[hi]
                    val dx=(b.x-a.x)*ink.aspect;val dy=b.y-a.y;val denom=dx*dx+dy*dy
                    var best=0f;var index=-1
                    for(i in lo+1 until hi){
                        val x=(p[i].x-a.x)*ink.aspect;val y=p[i].y-a.y
                        val t=if(denom==0f)0f else ((x*dx+y*dy)/denom).coerceIn(0f,1f)
                        val distance=(x-t*dx)*(x-t*dx)+(y-t*dy)*(y-t*dy)
                        // Retain pressure/tool transitions as well as visible bends.
                        val pressure=abs(p[i].pressure-(a.pressure+(b.pressure-a.pressure)*t))
                        val score=if(pressure>.12f || p[i].tool!=a.tool)1f else distance
                        if(score>best){best=score;index=i}
                    }
                    if(index>=0 && best>.000001f){keep[index]=true;stack.add(lo to index);stack.add(index to hi)}
                }
                InkStroke(p.filterIndexed { i,_->keep[i] })
            }
        })
        val first=ink.strokes.first().points.first().time
        val duration=ink.strokes.last().points.last().time-first
        return if(duration>1200)compact.copy(strokes=compact.strokes.map { stroke->
            InkStroke(stroke.points.map{it.copy(time=(it.time-first)*1200/duration)})
        }) else compact
    }

}
