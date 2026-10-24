package com.tapautograph.app

import android.graphics.Bitmap
import kotlin.math.*

/** Local colour-key removal: only matching pixels connected to the crop border are erased. */
object MemeArt {
    fun removeBackground(source:Bitmap,tolerance:Int=45,corner:Int=0,checkCancelled:()->Unit={}):Bitmap {
        require(corner in 0..3)
        val w=source.width;val h=source.height
        require(w in 3..640&&h in 3..640)
        val pixels=IntArray(w*h);source.getPixels(pixels,0,w,0,0,w,h)
        val seed=pixels[when(corner){1->w-1;2->(h-1)*w;3->w*h-1;else->0}]
        val output=source.copy(Bitmap.Config.ARGB_8888,true)
        if(seed ushr 24<16)return output // Existing transparent PNG; don't erase the subject.
        val seen=BooleanArray(pixels.size);val queue=IntArray(pixels.size);var head=0;var tail=0
        val limit=tolerance.coerceIn(0,180).let{it*it*3}
        fun matches(p:Int):Boolean {
            if(p ushr 24<16)return true
            val dr=((p ushr 16)and 255)-((seed ushr 16)and 255);val dg=((p ushr 8)and 255)-((seed ushr 8)and 255);val db=(p and 255)-(seed and 255)
            return dr*dr+dg*dg+db*db<=limit
        }
        fun add(i:Int){if(!seen[i]){seen[i]=true;if(matches(pixels[i]))queue[tail++]=i}}
        for(x in 0 until w){add(x);add((h-1)*w+x)}
        for(y in 0 until h){add(y*w);add(y*w+w-1)}
        while(head<tail){if(head%1024==0)checkCancelled();val i=queue[head++];pixels[i]=0;val x=i%w;val y=i/w;if(x>0)add(i-1);if(x<w-1)add(i+1);if(y>0)add(i-w);if(y<h-1)add(i+w)}
        checkCancelled();output.setPixels(pixels,0,w,0,0,w,h);return output
    }

    /** Scanline artwork stays inside the existing vector NFC/receipt protocol. */
    fun artwork(photo:Bitmap,style:String,threshold:Int,invert:Boolean,color:Long,checkCancelled:()->Unit={}):Autograph {
        require(style in listOf("sticker","outline","dots"))
        var edge=if(style=="dots")96 else 160
        while(edge>=24){
            checkCancelled()
            val scale=min(1f,edge.toFloat()/max(photo.width,photo.height))
            val w=max(3,(photo.width*scale).roundToInt());val h=max(3,(photo.height*scale).roundToInt())
            val small=Bitmap.createScaledBitmap(photo,w,h,true);val pixels=IntArray(w*h);small.getPixels(pixels,0,w,0,0,w,h)
            val present=BooleanArray(pixels.size){pixels[it] ushr 24>=16}
            val gray=FloatArray(pixels.size){i->val p=pixels[i];val alpha=p ushr 24;val luma=(77*((p ushr 16)and 255)+150*((p ushr 8)and 255)+29*(p and 255))/256;val value=if(invert)255-luma else luma;((value*alpha+255*(255-alpha))/255f)}
            val mask=BooleanArray(pixels.size)
            if(style=="outline"){
                val cutoff=((255-threshold.coerceIn(40,230))/3).coerceAtLeast(8)
                for(y in 0 until h){if(y%16==0)checkCancelled();for(x in 0 until w){val i=y*w+x;if(!present[i])continue;mask[i]=listOf((x-1).coerceAtLeast(0)+y*w,(x+1).coerceAtMost(w-1)+y*w,x+(y-1).coerceAtLeast(0)*w,x+(y+1).coerceAtMost(h-1)*w).any{!present[it]||abs(gray[i]-gray[it])>cutoff}}}
            }else{
                val values=gray.copyOf()
                for(y in 0 until h){if(y%16==0)checkCancelled();for(x in 0 until w){val i=y*w+x;if(!present[i])continue
                    val value=(values[i]+128-threshold).coerceIn(0f,255f);mask[i]=value<128
                    if(style=="dots"){
                        val error=value-(if(mask[i])0 else 255)
                        fun diffuse(nx:Int,ny:Int,weight:Float){if(nx in 0 until w&&ny<h&&present[ny*w+nx])values[ny*w+nx]+=error*weight}
                        diffuse(x+1,y,7/16f);diffuse(x-1,y+1,3/16f);diffuse(x,y+1,5/16f);diffuse(x+1,y+1,1/16f)
                    }
                }}
            }
            val strokes=mutableListOf<InkStroke>()
            for(y in 0 until h){var x=0;while(x<w){if(!mask[y*w+x]){x++;continue};val start=x;while(x+1<w&&mask[y*w+x+1])x++;val end=x
                strokes.add(InkStroke(listOf(InkPoint((start+.5f)/w,(y+.5f)/h,0),InkPoint((end+.5f)/w,(y+.5f)/h,0))));x++}}
            require(strokes.isNotEmpty()){ "No artwork found. Adjust the threshold, invert colours, or reduce background removal." }
            if(strokes.size<=850){
                val ink=Autograph(aspect=w.toFloat()/h,color=color,width=(.95f/h).coerceIn(.001f,.04f),strokes=strokes)
                require(ink.valid()){ "Try a less narrow crop." };CompactInkCodec.encode(ink);return ink
            }
            edge=(edge*.75f).toInt()
        }
        throw IllegalArgumentException("Too much image detail. Crop closer or choose Sticker style.")
    }
}
