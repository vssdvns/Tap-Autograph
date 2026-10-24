package com.tapautograph.app

import android.graphics.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[35])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class MemeArtTest {
 private fun meme():Bitmap=Bitmap.createBitmap(180,140,Bitmap.Config.ARGB_8888).also{b->Canvas(b).apply{
  drawColor(Color.WHITE);val p=Paint().apply{color=Color.BLACK};drawRect(35f,20f,145f,120f,p);p.color=Color.WHITE;drawCircle(65f,55f,10f,p);drawCircle(115f,55f,10f,p);drawRect(60f,85f,120f,95f,p)
 }}
 @Test fun removalErasesOnlyEdgeConnectedBackgroundAndPreservesSource(){val b=meme();val removed=MemeArt.removeBackground(b,5);assertEquals(0,removed.getPixel(0,0) ushr 24);assertEquals(Color.BLACK,removed.getPixel(90,70));assertEquals(Color.WHITE,removed.getPixel(65,55));assertEquals(Color.WHITE,b.getPixel(0,0))}
 @Test fun toleranceAndCornerAreRealControls(){
  val b=Bitmap.createBitmap(40,40,Bitmap.Config.ARGB_8888);b.eraseColor(Color.rgb(230,230,230));b.setPixel(0,0,Color.WHITE)
  assertTrue(MemeArt.removeBackground(b,0).getPixel(1,0) ushr 24>0);assertEquals(0,MemeArt.removeBackground(b,30).getPixel(1,0) ushr 24)
  b.eraseColor(Color.WHITE);Canvas(b).drawRect(0f,0f,15f,40f,Paint().apply{color=Color.BLACK})
  assertEquals(0,MemeArt.removeBackground(b,0,0).getPixel(0,20) ushr 24);assertEquals(Color.WHITE,MemeArt.removeBackground(b,0,0).getPixel(39,20))
  assertEquals(0,MemeArt.removeBackground(b,0,1).getPixel(39,20) ushr 24);assertEquals(Color.BLACK,MemeArt.removeBackground(b,0,1).getPixel(0,20))
 }
 @Test fun transparentPngDoesNotLoseItsSubject(){val b=meme();b.setPixel(0,0,Color.TRANSPARENT);val removed=MemeArt.removeBackground(b,140);assertEquals(Color.BLACK,removed.getPixel(90,70));assertEquals(Color.WHITE,removed.getPixel(1,0))}
 @Test fun allMemeStylesSurviveNfcAndReceiptWithoutInventedPenTiming(){
  val dir=File("build/meme-fixtures").apply{mkdirs()}
  for(style in listOf("sticker","outline","dots")){
   val result=PhotoArt.convert(meme(),PhotoControls(style=style,removeBackground=true,backgroundTolerance=10))
   val ink=result.ink;assertTrue(ink.valid());assertTrue(ink.strokes.size<=850);assertTrue(ink.strokes.flatMap{it.points}.all{it.time==0L});assertEquals(0,result.photo.getPixel(0,0) ushr 24)
   val packet=CompactInkCodec.encode(ink);assertTrue(packet.size<=65536);assertArrayEquals(packet,AutographReaderSimulator.run(packet).packet)
   File(dir,"$style.png").outputStream().use{ReceiptArt.render(ink).compress(Bitmap.CompressFormat.PNG,100,it)}
   File(dir,"$style.tai").writeBytes(packet)
  }
 }
 @Test fun detailedPhotosAreSimplifiedInsteadOfTruncated(){
  val b=Bitmap.createBitmap(320,240,Bitmap.Config.ARGB_8888);val pixels=IntArray(320*240){i->val v=(i*37+i/320*19)%256;Color.rgb(v,v,v)};b.setPixels(pixels,0,320,0,0,320,240)
  val ink=MemeArt.artwork(b,"dots",145,false,Color.BLACK.toLong()and 0xffffffffL)
  assertTrue(ink.strokes.size<=850);assertTrue(CompactInkCodec.decode(CompactInkCodec.encode(ink)).valid())
 }
 @Test fun invertAndCropChangeArtworkAndBlankImagesFailClearly(){
  val normal=PhotoArt.convert(meme(),PhotoControls(style="sticker")).ink
  val inverted=PhotoArt.convert(meme(),PhotoControls(style="sticker",invert=true,left=.1f,turns=1)).ink
  assertNotEquals(normal.strokes,inverted.strokes)
  val blank=Bitmap.createBitmap(30,30,Bitmap.Config.ARGB_8888).apply{eraseColor(Color.WHITE)}
  try{PhotoArt.convert(blank,PhotoControls(style="sticker",removeBackground=true));fail("Blank image must not save")}catch(e:IllegalArgumentException){assertTrue(e.message!!.contains("No artwork"))}
 }
}
