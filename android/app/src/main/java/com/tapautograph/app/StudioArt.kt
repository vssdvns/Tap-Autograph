package com.tapautograph.app

import android.content.Context
import android.graphics.*
import android.net.Uri
import androidx.exifinterface.media.ExifInterface
import kotlin.math.*

val studioColors=listOf(0xFF18365FL,0xFF171F29L,0xFF58458AL,0xFF225D50L)
val nameStyles=listOf("Flow","Classic","Clean","Monogram")
object NameArt {
    fun create(name:String,style:Int,slant:Float=0f,underline:Boolean=true,color:Long=studioColors[0]):Autograph {
        val text=name.trim().replace(Regex("\\s+")," ")
        require(text.isNotEmpty()&&text.length<=32&&text.none { it.isISOControl() }) { "Enter a name of up to 32 characters." }
        require(style in 0..3)
        val display=if(style==3)text.split(" ").filter { it.isNotEmpty() }.take(3).joinToString("") { it.take(1).uppercase() } else text
        val paint=Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize=120f;typeface=when(style){0->Typeface.create("cursive",Typeface.NORMAL);1->Typeface.create("serif",Typeface.ITALIC);2->Typeface.create("sans-serif-medium",Typeface.NORMAL);else->Typeface.create("serif",Typeface.BOLD_ITALIC)} }
        val path=Path();paint.getTextPath(display,0,display.length,0f,0f,path)
        val measure=PathMeasure(path,false);val lines=mutableListOf<List<StudioPoint>>()
        do {
            val length=measure.length
            if(length>0){val count=max(2,ceil(length/2f).toInt());require(count<=12000 && lines.size<250) { "Use a shorter name." }
                val pos=FloatArray(2);val points=(0..count).map { i->measure.getPosTan(length*i/count,pos,null);StudioPoint(pos[0]-pos[1]*slant.coerceIn(-.4f,.4f),pos[1]) }
                lines.add(points)
            }
        }while(measure.nextContour())
        require(lines.isNotEmpty()) { "This name cannot be drawn with the selected font. Try another style." }
        val flat=lines.flatten();val x0=flat.minOf { it.x };val x1=flat.maxOf { it.x };val bottom=flat.maxOf { it.y }
        if(underline){val span=max(10f,x1-x0);lines.add((0..40).map { i->StudioPoint(x0+span*i/40,bottom+14f-6f*sin(i/40f*PI).toFloat()) })}
        return studioInk(lines,width=.005f,color=color)
    }
}

val doodleTitles=listOf("Deal with it","Side eye","Cat energy","Tiny ghost","Approved","Happy bean")
object DoodleArt {
    fun create(index:Int,color:Long=studioColors[0]):Autograph {
        require(index in doodleTitles.indices)
        val lines=mutableListOf<List<StudioPoint>>()
        fun line(vararg xy:Float){lines.add(xy.toList().chunked(2).map { StudioPoint(it[0],it[1]) })}
        fun arc(cx:Float,cy:Float,rx:Float,ry:Float,start:Float=0f,end:Float=360f){lines.add((0..48).map { i->val a=(start+(end-start)*i/48)*PI/180;StudioPoint(cx+rx*cos(a).toFloat(),cy+ry*sin(a).toFloat()) })}
        when(index){
            0->{arc(50f,50f,38f,38f);line(14f,36f,86f,36f);line(20f,36f,22f,51f,40f,51f,44f,36f);line(56f,36f,60f,51f,78f,51f,80f,36f);line(44f,39f,56f,39f);arc(51f,55f,19f,14f,20f,150f)}
            1->{arc(50f,50f,39f,37f);arc(34f,43f,11f,14f);arc(66f,43f,11f,14f);line(39f,40f,39f,48f);line(71f,40f,71f,48f);line(37f,71f,64f,68f)}
            2->{line(16f,45f,13f,8f,35f,26f,66f,26f,87f,8f,84f,45f);arc(50f,49f,35f,33f,0f,180f);line(31f,45f,35f,48f);line(65f,48f,69f,45f);line(46f,56f,54f,56f,50f,61f,46f,56f);line(50f,61f,43f,68f,38f,64f);line(50f,61f,57f,68f,62f,64f);line(7f,54f,30f,58f);line(5f,67f,29f,64f);line(71f,58f,94f,54f);line(72f,64f,96f,67f)}
            3->{arc(50f,43f,30f,32f,180f,360f);line(20f,43f,20f,87f,33f,77f,44f,88f,56f,77f,68f,88f,80f,78f,80f,43f);arc(39f,44f,3f,6f);arc(61f,44f,3f,6f);arc(50f,61f,9f,7f,0f,180f)}
            4->{arc(50f,50f,39f,39f);arc(50f,50f,32f,32f);line(29f,50f,44f,65f,73f,33f);line(4f,22f,12f,27f);line(88f,73f,98f,78f)}
            5->{arc(50f,50f,42f,31f);arc(35f,43f,3f,5f);arc(65f,43f,3f,5f);arc(50f,52f,20f,15f,10f,170f);line(23f,58f,27f,60f);line(73f,60f,77f,58f)}
        }
        return studioInk(lines,width=.012f,color=color)
    }
}

data class PhotoControls(val left:Float=0f,val top:Float=0f,val right:Float=0f,val bottom:Float=0f,val turns:Int=0,val angle:Float=0f,val threshold:Int=145,val speckle:Int=8,val width:Float=.008f,val style:String="paper",val removeBackground:Boolean=false,val backgroundTolerance:Int=45,val backgroundCorner:Int=0,val invert:Boolean=false)
data class PaperResult(val photo:Bitmap,val ink:Autograph)
object PhotoArt {
    fun load(context:Context,uri:Uri):Bitmap {
        val resolver=context.contentResolver
        val options=BitmapFactory.Options().apply { inJustDecodeBounds=true }
        resolver.openInputStream(uri).use { requireNotNull(it);BitmapFactory.decodeStream(it,null,options) }
        require(options.outWidth>0 && options.outHeight>0 && options.outWidth<=30000 && options.outHeight<=30000 && options.outWidth.toLong()*options.outHeight<=200_000_000) { "Choose a smaller supported photo." }
        var sample=1;while(max(options.outWidth,options.outHeight)/sample>1400)sample*=2
        val decode=BitmapFactory.Options().apply { inSampleSize=sample;inPreferredConfig=Bitmap.Config.ARGB_8888 }
        val bitmap=resolver.openInputStream(uri).use { BitmapFactory.decodeStream(requireNotNull(it),null,decode) } ?: throw IllegalArgumentException("Could not read this image.")
        val orientation=resolver.openInputStream(uri).use { input->if(input!=null)runCatching { ExifInterface(input).getAttributeInt(ExifInterface.TAG_ORIENTATION,1) }.getOrDefault(1)else 1 }
        val matrix=Matrix()
        when(orientation){2->matrix.setScale(-1f,1f);3->matrix.setRotate(180f);4->matrix.setScale(1f,-1f);5->{matrix.setRotate(90f);matrix.postScale(-1f,1f)};6->matrix.setRotate(90f);7->{matrix.setRotate(-90f);matrix.postScale(-1f,1f)};8->matrix.setRotate(-90f)}
        return if(matrix.isIdentity)bitmap else Bitmap.createBitmap(bitmap,0,0,bitmap.width,bitmap.height,matrix,true)
    }
    fun convert(source:Bitmap,c:PhotoControls,color:Long=studioColors[0],checkCancelled:()->Unit={}):PaperResult {
        val rotation=Matrix().apply { postRotate(((c.turns%4)*90).toFloat()) }
        val rotated=if(c.turns%4==0)source else Bitmap.createBitmap(source,0,0,source.width,source.height,rotation,true)
        val straight=Bitmap.createBitmap(rotated.width,rotated.height,Bitmap.Config.ARGB_8888)
        Canvas(straight).apply { drawColor(if(c.style=="paper"&&!c.removeBackground)Color.WHITE else Color.TRANSPARENT);rotate(c.angle.coerceIn(-15f,15f),rotated.width/2f,rotated.height/2f);drawBitmap(rotated,0f,0f,Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)) }
        val left=(c.left.coerceIn(0f,.45f)*straight.width).toInt();val top=(c.top.coerceIn(0f,.45f)*straight.height).toInt()
        val w=max(3,((1-c.left.coerceIn(0f,.45f)-c.right.coerceIn(0f,.45f))*straight.width).toInt()).coerceAtMost(straight.width-left)
        val h=max(3,((1-c.top.coerceIn(0f,.45f)-c.bottom.coerceIn(0f,.45f))*straight.height).toInt()).coerceAtMost(straight.height-top)
        val cropped=Bitmap.createBitmap(straight,left,top,w,h)
        val scale=min(1f,640f/max(w,h));val scaled=Bitmap.createScaledBitmap(cropped,max(3,(w*scale).toInt()),max(3,(h*scale).toInt()),true)
        val photo=if(c.removeBackground)MemeArt.removeBackground(scaled,c.backgroundTolerance,c.backgroundCorner,checkCancelled)else scaled
        if(c.style!="paper")return PaperResult(photo,MemeArt.artwork(photo,c.style,c.threshold,c.invert,color,checkCancelled))
        val pixels=IntArray(photo.width*photo.height);photo.getPixels(pixels,0,photo.width,0,0,photo.width,photo.height)
        val mask=BooleanArray(pixels.size){i->val p=pixels[i];val alpha=(p ushr 24) and 255;val luminance=(77*((p ushr 16) and 255)+150*((p ushr 8) and 255)+29*(p and 255))/256
            (luminance*alpha+255*(255-alpha))/255<c.threshold.coerceIn(1,254) }
        checkCancelled()
        val paths=PaperTrace.trace(mask,photo.width,photo.height,c.speckle.coerceIn(1,50),checkCancelled)
        return PaperResult(photo,studioInk(paths,width=c.width.coerceIn(.003f,.025f),color=color))
    }
}

object ReceiptArt {
    fun render(ink:Autograph,width:Int=384,bold:Float=1f):Bitmap {
        require(width in listOf(384,576))
        require(ink.valid())
        val height=(width/ink.aspect).coerceIn(96f,384f).roundToInt()+32
        val bitmap=Bitmap.createBitmap(width,height,Bitmap.Config.ARGB_8888);val canvas=Canvas(bitmap);canvas.drawColor(Color.WHITE)
        val scale=min((width-32)/ink.aspect,(height-32).toFloat());val ox=(width-ink.aspect*scale)/2;val oy=(height-scale)/2;val paint=Paint(Paint.ANTI_ALIAS_FLAG).apply { color=Color.BLACK;strokeCap=Paint.Cap.ROUND;strokeJoin=Paint.Join.ROUND }
        for(stroke in ink.strokes){val ps=stroke.points
            if(ps.size==1){paint.style=Paint.Style.FILL;canvas.drawCircle(ox+ps[0].x*ink.aspect*scale,oy+ps[0].y*scale,max(.7f,ink.width*scale*bold/2),paint)}
            for(i in 1 until ps.size){paint.style=Paint.Style.STROKE;paint.strokeWidth=max(1f,ink.width*scale*bold.coerceIn(1f,2f));canvas.drawLine(ox+ps[i-1].x*ink.aspect*scale,oy+ps[i-1].y*scale,ox+ps[i].x*ink.aspect*scale,oy+ps[i].y*scale,paint)}
        }
        val pixels=IntArray(width*height);bitmap.getPixels(pixels,0,width,0,0,width,height)
        for(i in pixels.indices)pixels[i]=if((pixels[i] and 255)<190)Color.BLACK else Color.WHITE
        bitmap.setPixels(pixels,0,width,0,0,width,height);return bitmap
    }
}
