package com.tapautograph.app

import kotlinx.serialization.Serializable

@Serializable data class StudioDesign(val ink:Autograph,val title:String,val source:String="handwritten",val createdAt:Long=0)
const val MAX_DESIGNS=24
private val designSources=setOf("handwritten","name","photo","doodle")
fun SavedData.normalizedStudio():SavedData {
    val valid=gallery.filter { it.ink.valid() && it.title.isNotBlank() && it.title.length<=40 && it.source in designSources }.distinctBy { it.ink.id }.take(MAX_DESIGNS).toMutableList()
    val active=autograph?.takeIf { it.valid() }
    if(active!=null) {
        val index=valid.indexOfFirst { it.ink.id==active.id }
        if(index>=0)valid[index]=valid[index].copy(ink=active)
        else { if(valid.size==MAX_DESIGNS)valid.removeAt(valid.lastIndex);valid.add(0,StudioDesign(active,"My autograph",createdAt=System.currentTimeMillis())) }
    }
    return copy(autograph=active,gallery=valid,receiptDesignId=receiptDesignId?.takeIf { id->valid.any { it.ink.id==id } })
}
fun SavedData.saveStudioDesign(ink:Autograph,title:String,source:String):SavedData {
    require(title.trim().length in 1..40) { "Give this design a name (up to 40 characters)." }
    require(source in designSources && ink.valid()) { "This design is invalid." }
    CompactInkCodec.encode(ink) // Every new saved design must fit the unchanged NFC format.
    val current=normalizedStudio();val old=current.gallery.find { it.ink.id==ink.id }
    require(old!=null || current.gallery.size<MAX_DESIGNS) { "Your gallery is full. Delete a design before adding another." }
    val item=StudioDesign(ink,title.trim(),source,old?.createdAt ?: System.currentTimeMillis())
    val next=if(old==null)listOf(item)+current.gallery else current.gallery.map { if(it.ink.id==ink.id)item else it }
    require(next.sumOf { design->design.ink.strokes.sumOf { it.points.size } }<=120_000) { "Your gallery has too much detail. Delete an unused design first." }
    return current.copy(autograph=ink,gallery=next)
}
fun SavedData.removeStudioDesign(id:String):SavedData {
    val next=normalizedStudio().gallery.filterNot { it.ink.id==id }
    return copy(gallery=next,autograph=if(autograph?.id==id)next.firstOrNull()?.ink else autograph,
        receiptDesignId=receiptDesignId?.takeUnless { it==id },receiptEnabled=receiptEnabled && receiptDesignId!=id)
}
fun SavedData.receiptDesign():Autograph? = if(receiptEnabled)gallery.find { it.ink.id==receiptDesignId }?.ink ?: autograph else null
fun SavedData.designForReceiptHistory(id:String?):Autograph?=gallery.find { it.ink.id==id }?.ink ?: autograph?.takeIf { it.id==id }
