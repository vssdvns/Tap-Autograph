package com.tapautograph.app

import org.junit.Assert.*
import org.junit.Test
import kotlinx.serialization.json.Json
import kotlinx.serialization.encodeToString

class StudioModelsTest {
    private fun ink(id:String="legacy")=Autograph(id=id,aspect=1.8f,strokes=listOf(InkStroke(listOf(InkPoint(.2f,.3f,0),InkPoint(.8f,.6f,100)))))
    @Test fun legacyJsonMigratesWithoutChangingInkOrHistory() {
        val before=SavedData(autograph=ink(),transactions=listOf(Transaction("old",100,0,0,"legacy")))
        val oldJson=Json.encodeToString(before)
        assertFalse(oldJson.contains("gallery"))
        val after=Json.decodeFromString<SavedData>(oldJson).normalizedStudio()
        assertEquals(before.autograph,after.autograph);assertEquals(before.transactions,after.transactions)
        assertEquals("legacy",after.gallery.single().ink.id);assertFalse(after.receiptEnabled)
        assertEquals(after,Json.decodeFromString<SavedData>(Json.encodeToString(after)))
    }
    @Test fun replacingDesignPreservesIdAndDoesNotDuplicateGallery() {
        val a=SavedData().saveStudioDesign(ink(),"One","handwritten")
        val b=a.saveStudioDesign(ink().copy(color=0xff000000),"Updated","handwritten")
        assertEquals(1,b.gallery.size);assertEquals("legacy",b.autograph!!.id);assertEquals("Updated",b.gallery.single().title)
    }
    @Test fun receiptChoiceIsIndependentOfScreenChoiceAndDeletionIsConsistent() {
        val a=SavedData().saveStudioDesign(ink("one"),"One","name").saveStudioDesign(ink("two"),"Two","doodle")
            .copy(receiptDesignId="one",receiptEnabled=true)
        assertEquals("two",a.autograph!!.id);assertEquals("one",a.receiptDesign()!!.id)
        val b=a.removeStudioDesign("one");assertFalse(b.receiptEnabled);assertNull(b.receiptDesignId);assertEquals("two",b.autograph!!.id)
        assertNull(b.removeStudioDesign("two").autograph)
    }
    @Test fun galleryLimitRejectsNewDesignButAllowsReplacement() {
        var data=SavedData();repeat(MAX_DESIGNS){data=data.saveStudioDesign(ink("$it"),"Design $it","name")}
        try {data.saveStudioDesign(ink("overflow"),"Extra","name");fail()}catch(_:IllegalArgumentException){}
        assertEquals(MAX_DESIGNS,data.saveStudioDesign(ink("0"),"Replacement","name").gallery.size)
    }
    @Test fun oldReceiptCanResolveAnInactiveGalleryDesign() {
        val data=SavedData().saveStudioDesign(ink("one"),"One","name").saveStudioDesign(ink("two"),"Two","name")
        assertEquals("one",data.designForReceiptHistory("one")!!.id)
    }
    @Test fun geometryFitKeepsProportionsAndMonotonicTimes() {
        val a=studioInk(listOf(listOf(StudioPoint(0f,0f),StudioPoint(100f,50f)),listOf(StudioPoint(30f,20f))))
        assertTrue(a.valid());val ps=a.strokes.flattenPoints();assertTrue(ps.zipWithNext().all { it.second.time>=it.first.time })
        assertEquals(2f,((ps[1].x-ps[0].x)*a.aspect)/(ps[1].y-ps[0].y),.0001f)
    }
    @Test fun traceRemovesSpecklesAndPreservesConnectedLine() {
        val w=50;val mask=BooleanArray(w*w)
        for(x in 8..40)for(y in 20..24)mask[y*w+x]=true
        mask[2*w+2]=true
        val paths=PaperTrace.trace(mask,w,w,8)
        assertTrue(paths.isNotEmpty());assertTrue(paths.flatten().none { it.x<5&&it.y<5 })
        val encoded=CompactInkCodec.encode(studioInk(paths));assertTrue(CompactInkCodec.decode(encoded).valid())
    }
    @Test fun blankPhotoIsRejectedRatherThanSavedAsEmptyInk() {
        try {PaperTrace.trace(BooleanArray(100),10,10);fail()}catch(_:IllegalArgumentException){}
    }
    private fun List<InkStroke>.flattenPoints()=flatMap { it.points }
}
