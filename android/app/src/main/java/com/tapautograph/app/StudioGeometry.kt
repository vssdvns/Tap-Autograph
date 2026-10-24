package com.tapautograph.app

import kotlin.math.*

data class StudioPoint(val x:Float,val y:Float)
fun studioInk(lines:List<List<StudioPoint>>,width:Float=.008f,color:Long=0xFF18365FL):Autograph {
    val paths=lines.filter { it.isNotEmpty() }
    require(paths.isNotEmpty()) { "No ink found. Try a closer crop or adjust the ink threshold." }
    require(paths.size<=900 && paths.sumOf { it.size }<=28000) { "Too much detail. Crop closer or increase speckle cleanup." }
    val points=paths.flatten();require(points.all { it.x.isFinite()&&it.y.isFinite() })
    val minX=points.minOf { it.x };val minY=points.minOf { it.y };val dx=max(.001f,points.maxOf { it.x }-minX);val dy=max(.001f,points.maxOf { it.y }-minY)
    val scale=min(1.56f/dx,.76f/dy);val left=(1.8f-dx*scale)/2;val top=(1-dy*scale)/2
    var clock=0.0;val step=2000.0/points.size;val gap=min(35.0,600.0/paths.size)
    return Autograph(aspect=1.8f,color=color,width=width,strokes=paths.map { line->
        val stroke=InkStroke(line.map { p->InkPoint(((left+(p.x-minX)*scale)/1.8f).coerceIn(0f,1f),(top+(p.y-minY)*scale).coerceIn(0f,1f),clock.toLong().also { clock+=step },1f,0) })
        clock+=gap;stroke
    })
}
/** Bounded binary-image skeleton tracing. Keeps geometry; playback order is synthetic. */
object PaperTrace {
    fun trace(mask:BooleanArray,width:Int,height:Int,minSpeckle:Int=8,checkCancelled:()->Unit={}):List<List<StudioPoint>> {
        require(width in 3..640 && height in 3..640 && mask.size==width*height)
        val ink=mask.copyOf();val visited=BooleanArray(ink.size);val queue=IntArray(ink.size)
        val offsets=intArrayOf(-width,1-width,1,1+width,width,width-1,-1,-1-width)
        val deltaX=intArrayOf(0,1,1,1,0,-1,-1,-1);val deltaY=intArrayOf(-1,-1,0,1,1,1,0,-1)
        fun neighbors(p:Int):List<Int> { val x=p%width;val y=p/width;return offsets.indices.filter { d->
            val nx=x+deltaX[d];val ny=y+deltaY[d]
            nx in 0 until width && ny in 0 until height && ink[p+offsets[d]]
        }.map { p+offsets[it] } }
        for(start in ink.indices)if(ink[start]&&!visited[start]) {
            var head=0;var tail=1;queue[0]=start;visited[start]=true
            while(head<tail){if(head%4096==0)checkCancelled();val p=queue[head++];for(n in neighbors(p))if(!visited[n]){visited[n]=true;queue[tail++]=n}}
            if(tail<minSpeckle)for(i in 0 until tail)ink[queue[i]]=false
            if(start%width==0)checkCancelled()
        }
        for(iteration in 0 until 48) {
            var changed=false
            for(pass in 0..1) {
                val remove=ArrayList<Int>()
                for(y in 1 until height-1)for(x in 1 until width-1) {
                    val p=y*width+x;if(!ink[p])continue
                    var bits=0;for(d in 0..7)if(ink[p+offsets[d]])bits=bits or (1 shl d)
                    val count=Integer.bitCount(bits);if(count !in 2..6)continue
                    var transitions=0;for(d in 0..7)if(bits and (1 shl d)==0 && bits and (1 shl ((d+1)%8))!=0)transitions++
                    if(transitions!=1)continue
                    fun all(a:Int,b:Int,c:Int)=bits and (1 shl a)!=0 && bits and (1 shl b)!=0 && bits and (1 shl c)!=0
                    val blocked=if(pass==0)all(0,2,4)||all(2,4,6)else all(0,2,6)||all(0,4,6)
                    if(!blocked)remove.add(p)
                }
                if(remove.isNotEmpty())changed=true
                remove.forEach { ink[it]=false };checkCancelled()
            }
            if(!changed)break
        }
        fun linked(p:Int):List<Int> = neighbors(p).filter { n->
            val dx=n%width-p%width;val dy=n/width-p/width
            dx==0 || dy==0 || (!ink[p+dx]&&!ink[p+dy*width])
        }
        val edges=IntArray(ink.size);val result=mutableListOf<List<StudioPoint>>()
        fun direction(a:Int,b:Int)=offsets.indexOf(b-a)
        fun seen(a:Int,b:Int)=edges[a] and (1 shl direction(a,b))!=0
        fun mark(a:Int,b:Int){edges[a]=edges[a] or (1 shl direction(a,b));edges[b]=edges[b] or (1 shl direction(b,a))}
        fun walk(start:Int,next:Int) {
            val line=mutableListOf(start,next);mark(start,next);var p=next
            while(true){val adjacent=linked(p);if(adjacent.size!=2)break;val n=adjacent.firstOrNull { !seen(p,it) } ?: break
                mark(p,n);line.add(n);p=n;if(p==start)break
            }
            // Keep endpoints/turns, drop collinear interior pixels.
            val simplified=line.filterIndexed { i,v->i==0||i==line.lastIndex||
                (v%width-line[i-1]%width)!=(line[i+1]%width-v%width)||(v/width-line[i-1]/width)!=(line[i+1]/width-v/width) }
            result.add(simplified.map { StudioPoint((it%width).toFloat(),(it/width).toFloat()) })
            require(result.size<=900 && result.sumOf { it.size }<=28000) { "Too much detail. Crop closer or increase speckle cleanup." }
        }
        for(p in ink.indices)if(ink[p]) {val adjacent=linked(p);if(adjacent.isEmpty())result.add(listOf(StudioPoint((p%width).toFloat(),(p/width).toFloat())))
            else if(adjacent.size!=2)for(n in adjacent)if(!seen(p,n))walk(p,n)
        }
        for(p in ink.indices)if(ink[p])for(n in linked(p))if(!seen(p,n))walk(p,n)
        require(result.isNotEmpty()) { "No ink found. Try a closer crop or adjust the ink threshold." }
        return result
    }
}
