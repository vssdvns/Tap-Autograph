package com.tapautograph.app

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import kotlin.math.cos
import kotlin.math.sin

@Composable fun FavoriteWheel(data:SavedData,sessionOnly:Boolean=true,onDismiss:()->Unit,onChoose:(Autograph)->Unit){
    val context=LocalContext.current
    val ids=WalletTap.favorites(context)
    val favorites=ids.mapNotNull{id->data.gallery.find{it.ink.id==id}}
    Dialog(onDismissRequest=onDismiss){
        Surface(shape=androidx.compose.foundation.shape.RoundedCornerShape(28.dp)){
            Column(Modifier.padding(12.dp),horizontalAlignment=Alignment.CenterHorizontally){
                Text("Pick your mood",style=MaterialTheme.typography.titleLarge)
                BoxWithConstraints(Modifier.fillMaxWidth().aspectRatio(1f),contentAlignment=Alignment.Center){
                    val radius=maxWidth*.34f
                    favorites.forEachIndexed{index,entry->
                        val angle=(-90+index*60)*Math.PI/180
                        Column(Modifier.offset(x=radius*cos(angle).toFloat(),y=radius*sin(angle).toFloat()).size(78.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceVariant).clickable{onChoose(entry.ink)}.padding(8.dp),horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.Center){
                            InkPanel(entry.ink,36)
                            Text(entry.title,maxLines=1,style=MaterialTheme.typography.labelSmall)
                        }
                    }
                    Surface(onClick={data.autograph?.let(onChoose)},shape=CircleShape,color=Color(0xFF2764E7),modifier=Modifier.size(80.dp)){
                        Box(contentAlignment=Alignment.Center){Text("Default",color=Color.White)}
                    }
                }
                Text(if(favorites.isEmpty())"Star up to six designs in Studio → Gallery to fill your wheel." else if(sessionOnly)"This tap only · Your default stays saved" else "Choose your saved default autograph",style=MaterialTheme.typography.bodySmall)
                TextButton(onClick=onDismiss){Text("Keep current")}
            }
        }
    }
}
