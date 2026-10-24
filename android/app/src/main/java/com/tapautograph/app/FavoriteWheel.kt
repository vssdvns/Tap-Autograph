package com.tapautograph.app

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import kotlin.math.*

@Composable fun AutographDial(data:SavedData,current:Autograph?,enabled:Boolean=true,initiallyExpanded:Boolean=false,remainingFraction:Float?=null,onChoose:(Autograph)->Unit){
    val context=LocalContext.current;val haptic=LocalHapticFeedback.current;val density=LocalDensity.current
    val ids=WalletTap.favorites(context)
    val favorites=ids.mapNotNull{id->data.gallery.find{it.ink.id==id}}
    var expanded by remember{mutableStateOf(initiallyExpanded)}
    var dragging by remember{mutableStateOf(false)}
    var offset by remember{mutableStateOf(Offset.Zero)}
    var hover by remember{mutableStateOf<Int?>(null)}
    val choose by rememberUpdatedState(onChoose)
    val reduced=data.preferences.reducedMotion
    val blue=MaterialTheme.colorScheme.primary
    val dialHeight by animateDpAsState(if(expanded && enabled)300.dp else 82.dp,tween(if(reduced)0 else 180),label="dial bloom")
    LaunchedEffect(enabled){if(!enabled){expanded=false;hover=null;dragging=false;offset=Offset.Zero}}
    fun select(ink:Autograph){choose(ink);expanded=false;hover=null;offset=Offset.Zero}
    fun feedback(){if(WalletTap.haptics(context))haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)}
    Column(horizontalAlignment=Alignment.CenterHorizontally){
        AnimatedVisibility(visible=expanded && enabled,enter=fadeIn(tween(if(reduced)0 else 160))+expandVertically(tween(if(reduced)0 else 200)),exit=fadeOut(tween(if(reduced)0 else 100))+shrinkVertically(tween(if(reduced)0 else 160))){
            Text(if(favorites.isEmpty())"Star designs in Studio → Gallery to fill your dial." else "Move around the circle · Release to choose",style=MaterialTheme.typography.labelMedium)
        }
        BoxWithConstraints(Modifier.fillMaxWidth().height(dialHeight),contentAlignment=Alignment.Center){
            val radius=minOf(maxWidth*.34f,102.dp)
            val radiusPx=with(density){radius.toPx()}
            if(expanded && enabled){
                Canvas(Modifier.fillMaxSize()){
                    val center=Offset(size.width/2,size.height/2)
                    drawCircle(blue.copy(alpha=.16f),radiusPx,center,style=Stroke(2.dp.toPx()))
                    val selected=hover
                    if(selected!=null && selected<favorites.size){
                        drawArc(blue.copy(alpha=.4f),-120f+selected*60,60f,false,center-Offset(radiusPx,radiusPx),androidx.compose.ui.geometry.Size(radiusPx*2,radiusPx*2),style=Stroke(9.dp.toPx()))
                    }
                    if(dragging){
                        val length=offset.getDistance();val cursor=if(length>radiusPx)offset*(radiusPx/length) else offset
                        drawLine(blue.copy(alpha=.65f),center,center+cursor,3.dp.toPx())
                        drawCircle(blue,7.dp.toPx(),center+cursor)
                    }
                }
                favorites.forEachIndexed{index,entry->
                    val angle=(-90+index*60)*Math.PI/180
                    val highlighted=hover==index
                    val scale by animateFloatAsState(if(highlighted)1.13f else 1f,tween(if(reduced)0 else 110),label="emote highlight")
                    Column(Modifier.offset(x=radius*cos(angle).toFloat(),y=radius*sin(angle).toFloat()).size(70.dp).scale(scale).clip(CircleShape).background(if(highlighted)blue else MaterialTheme.colorScheme.surfaceVariant).clickable(enabled=enabled){feedback();select(entry.ink)}.padding(7.dp),horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.Center){
                        InkPanel(entry.ink,32)
                        Text(entry.title,maxLines=1,style=MaterialTheme.typography.labelSmall,color=if(highlighted)MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            if(remainingFraction!=null && !expanded){
                Canvas(Modifier.size(76.dp)){
                    drawCircle(blue.copy(alpha=.16f),style=Stroke(3.dp.toPx()))
                    drawArc(if(remainingFraction<.18f)Color(0xFFFFC46B) else blue,-90f,360f*remainingFraction.coerceIn(0f,1f),false,style=Stroke(3.dp.toPx()))
                }
            }
            Box(Modifier.size(64.dp).clip(CircleShape).background(if(enabled)Color(0xFF2764E7) else MaterialTheme.colorScheme.surfaceVariant)
                .semantics{contentDescription="Autograph dial. Tap to open or hold and rotate to choose a favorite."}
                .pointerInput(enabled,ids.joinToString("|")){
                    if(enabled)detectDragGestures(
                        onDragStart={expanded=true;dragging=true;offset=Offset.Zero;hover=null},
                        onDragCancel={dragging=false;offset=Offset.Zero;hover=null;expanded=false},
                        onDragEnd={
                            val selected=hover?.let{favorites.getOrNull(it)}
                            if(selected!=null)select(selected.ink) else {expanded=false;hover=null;offset=Offset.Zero}
                            dragging=false
                        }
                    ){change,amount->
                        change.consume();offset+=amount
                        val next=DialGeometry.slot(offset.x,offset.y,28.dp.toPx(),210.dp.toPx())?.takeIf{it<favorites.size}
                        if(next!=hover){hover=next;if(next!=null)feedback()}
                    }
                }.clickable(enabled=enabled){expanded=!expanded;hover=null;offset=Offset.Zero},contentAlignment=Alignment.Center){
                if(current!=null)Box(Modifier.padding(10.dp)){InkPanel(current,42)} else Text("✦",color=Color.White)
            }
        }
        if(expanded && enabled){
            Text(hover?.let{favorites.getOrNull(it)?.title} ?: "Move back to the center to cancel",style=MaterialTheme.typography.labelSmall)
            Row{
                TextButton(onClick={data.autograph?.let(::select)},enabled=data.autograph!=null){Text("Use default")}
                TextButton(onClick={expanded=false;hover=null}){Text("Keep current")}
            }
        }else Text(if(enabled)"Hold & rotate · Or tap to browse" else "Selection locked while sending",style=MaterialTheme.typography.labelSmall)
    }
}

@Composable fun FavoriteWheel(data:SavedData,sessionOnly:Boolean=true,onDismiss:()->Unit,onChoose:(Autograph)->Unit){
    Dialog(onDismissRequest=onDismiss){
        Surface(shape=RoundedCornerShape(28.dp)){
            Column(Modifier.padding(16.dp),horizontalAlignment=Alignment.CenterHorizontally){
                Text("Pick your mood",style=MaterialTheme.typography.titleLarge)
                AutographDial(data,data.autograph,initiallyExpanded=true,onChoose=onChoose)
                Text(if(sessionOnly)"This checkout only · Your default stays saved" else "Choose your saved default",style=MaterialTheme.typography.bodySmall)
                TextButton(onClick=onDismiss){Text("Close")}
            }
        }
    }
}
