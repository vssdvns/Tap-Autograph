package com.tapautograph.app

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.background
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import kotlin.math.*

/** Drawing-only crop. The saved design and transmitted packet retain their original canvas. */
fun thumbnailInk(ink:Autograph):Autograph {
    val points=ink.strokes.flatMap{it.points}
    if(points.isEmpty())return ink
    val pad=max(ink.width*1.5f,.006f)
    val x0=points.minOf{it.x*ink.aspect}-pad;val y0=points.minOf{it.y}-pad
    val w=max(.015f,points.maxOf{it.x*ink.aspect}-x0+pad);val h=max(.015f,points.maxOf{it.y}-y0+pad)
    return ink.copy(aspect=w/h,width=ink.width/h,strokes=ink.strokes.map{stroke->InkStroke(stroke.points.map{it.copy(x=(it.x*ink.aspect-x0)/w,y=(it.y-y0)/h)})})
}
@Composable fun InkThumbnail(ink:Autograph){
    val cropped=remember(ink){thumbnailInk(ink)}
    AndroidView(factory={InkView(it)},modifier=Modifier.fillMaxWidth().height(38.dp).clip(RoundedCornerShape(6.dp)).background(Color.White),update={it.thumbnail=true;it.editable=false;it.autograph=cropped;it.elapsed=Long.MAX_VALUE;it.contentDescription="Cropped autograph preview"})
}
