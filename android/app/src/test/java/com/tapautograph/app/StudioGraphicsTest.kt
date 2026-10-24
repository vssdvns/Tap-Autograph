package com.tapautograph.app

import android.app.Application
import android.graphics.*
import androidx.core.content.FileProvider
import androidx.exifinterface.media.ExifInterface
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[35])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class StudioGraphicsTest {
    private fun fixture(name:String,ink:Autograph) {
        assertTrue(ink.valid());val packet=CompactInkCodec.encode(ink);assertTrue(CompactInkCodec.decode(packet).valid())
        val result=AutographReaderSimulator.run(packet);assertArrayEquals(packet,result.packet)
        val folder=File("build/studio-fixtures").apply { mkdirs() }
        File(folder,"$name.tai").writeBytes(packet);File(folder,"$name.reader.json").writeText(result.transcript)
        File(folder,"$name.png").outputStream().use { assertTrue(ReceiptArt.render(ink).compress(Bitmap.CompressFormat.PNG,100,it)) }
    }
    @Test fun allFourNameStylesProduceTransferablePaths() {
        nameStyles.indices.forEach { i->fixture("name-$i",NameArt.create("Subha Sai",i,.15f,true)) }
    }
    @Test fun allDoodlesProduceTransferablePaths() { doodleTitles.indices.forEach { i->fixture("doodle-$i",DoodleArt.create(i)) } }
    private fun photo():Bitmap {
        val b=Bitmap.createBitmap(800,300,Bitmap.Config.ARGB_8888);val c=Canvas(b);c.drawColor(Color.WHITE)
        val p=Paint(Paint.ANTI_ALIAS_FLAG).apply {color=Color.rgb(20,40,80);strokeWidth=9f;style=Paint.Style.STROKE;strokeCap=Paint.Cap.ROUND}
        val path=Path().apply {moveTo(80f,210f);cubicTo(100f,30f,240f,30f,180f,160f);cubicTo(140f,240f,320f,230f,300f,100f);cubicTo(280f,210f,450f,220f,430f,110f);cubicTo(480f,210f,550f,60f,620f,180f)}
        c.drawPath(path,p);c.drawLine(120f,250f,690f,220f,p);c.drawCircle(665f,100f,5f,p);return b
    }
    @Test fun paperImportExtractsInkAndSurvivesCropRotateAndNfc() {
        val source=photo();val result=PhotoArt.convert(source,PhotoControls(threshold=150,speckle=5))
        fixture("paper",result.ink)
        assertTrue(result.ink.strokes.sumOf { it.points.size }>30)
        val rotated=PhotoArt.convert(source,PhotoControls(left=.05f,right=.05f,turns=1,angle=3f,threshold=150,speckle=5))
        assertTrue(rotated.ink.valid());assertTrue(CompactInkCodec.decode(CompactInkCodec.encode(rotated.ink)).valid())
    }
    @Test fun receiptExportIsStrictlyBlackAndWhiteAtBothWidths() {
        for(w in listOf(384,576)){
            val b=ReceiptArt.render(DoodleArt.create(2),w,1.4f);assertEquals(w,b.width)
            val pixels=IntArray(b.width*b.height);b.getPixels(pixels,0,b.width,0,0,b.width,b.height)
            assertTrue(pixels.all { it==Color.BLACK||it==Color.WHITE });assertTrue(pixels.any { it==Color.BLACK });assertTrue(pixels.any { it==Color.WHITE })
        }
    }
    @Test fun extremeLegacyAspectCannotCreateAnUnboundedReceiptBitmap() {
        val base=DoodleArt.create(0)
        for(aspect in listOf(.00001f,19.99f)){val b=ReceiptArt.render(base.copy(aspect=aspect));assertTrue(b.height<=416)}
    }
    @Test fun cameraProviderIsLimitedToItsOwnCacheDirectoryAndExifRotationIsApplied() {
        val app=RuntimeEnvironment.getApplication();val dir=File(app.cacheDir,"studio-camera").apply { mkdirs() }
        val file=File(dir,"synthetic.jpg");file.outputStream().use { photo().compress(Bitmap.CompressFormat.JPEG,95,it) }
        ExifInterface(file).apply {setAttribute(ExifInterface.TAG_ORIENTATION,ExifInterface.ORIENTATION_ROTATE_90.toString());saveAttributes()}
        val uri=FileProvider.getUriForFile(app,"${app.packageName}.studio-files",file)
        val loaded=PhotoArt.load(app,uri);assertEquals(300,loaded.width);assertEquals(800,loaded.height)
        try {FileProvider.getUriForFile(app,"${app.packageName}.studio-files",File(app.filesDir,"private.txt"));fail()}catch(_:IllegalArgumentException){}
        file.delete()
    }
    @Test fun atomicRepositoryMigratesAndPersistsGalleryAndReceiptChoice() {
        val app=RuntimeEnvironment.getApplication();val repo=LocalRepository(app)
        val ink=DoodleArt.create(0);repo.write(SavedData(autograph=ink))
        val migrated=repo.read();assertEquals(ink.id,migrated.gallery.single().ink.id)
        val next=migrated.saveStudioDesign(DoodleArt.create(1),"Side eye","doodle").copy(receiptDesignId=ink.id,receiptEnabled=true)
        repo.write(next);assertEquals(next,repo.read())
    }
}
