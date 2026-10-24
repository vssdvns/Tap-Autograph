package com.tapautograph.app

import android.app.Activity
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

@Composable fun CompactTap(data:SavedData,onClose:()->Unit){
    val context=LocalContext.current;val haptic=LocalHapticFeedback.current;val card=AutographSharing.checkout
    var phase by remember{mutableIntStateOf(card.state())};var seconds by remember{mutableLongStateOf(0)}
    var total by remember{mutableLongStateOf(card.total())};var progress by remember{mutableFloatStateOf(0f)}
    var chosen by remember{mutableStateOf(data.autograph)}
    var choosing by remember{mutableStateOf(false)};var pendingInk by remember{mutableStateOf<Autograph?>(null)}
    var message by remember{mutableStateOf("")};var tick by remember{mutableStateOf(false)}
    val reduced=data.preferences.reducedMotion
    LaunchedEffect(Unit){
        while(true){
            phase=card.state();seconds=card.remainingSeconds();total=card.total();progress=card.progress()
            if(card.timedOut){WalletTap.cooldown();TapNotice.clear(context);onClose();break}
            if(!card.active){onClose();break}
            delay(60)
        }
    }
    LaunchedEffect(pendingInk){
        val ink=pendingInk ?: return@LaunchedEffect
        choosing=true
        runCatching{withContext(Dispatchers.Default){TapPacket.encode(ink)}}.onSuccess{
            if(card.choose(it)){chosen=ink;message="";if(WalletTap.haptics(context))haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)}
            else message="Transfer has started. Your previous selection is being sent."
        }.onFailure{message="Could not prepare this design."}
        choosing=false;pendingInk=null
    }
    LaunchedEffect(phase){
        if(phase==5 && WalletTap.haptics(context)){haptic.performHapticFeedback(HapticFeedbackType.LongPress)}
        if(phase in 2..5 && !tick){tick=true}
        if(phase==5 || phase==6){TapNotice.clear(context);WalletTap.cooldown();delay(if(reduced)700 else 1800);onClose()}
    }
    // Payment ACK drives the check, never an optimistic animation before acceptance.
    var showCheck by remember{mutableStateOf(false)}
    LaunchedEffect(tick){if(tick){
        showCheck=true
        delay(if(reduced)200 else 850);showCheck=false
    }}
    val accent by animateColorAsState(if(phase==5)Color(0xFF75E1B7) else Color(0xFF84B4FF),tween(if(reduced)0 else 350),label="checkout colour")
    val pulse=rememberInfiniteTransition(label="tap halo")
    val halo by pulse.animateFloat(.92f,1.08f,infiniteRepeatable(tween(1200),RepeatMode.Reverse),label="breathing")
    Column(Modifier.fillMaxWidth().heightIn(max=680.dp).verticalScroll(rememberScrollState()).padding(24.dp).navigationBarsPadding(),verticalArrangement=Arrangement.spacedBy(14.dp),horizontalAlignment=Alignment.CenterHorizontally){
        Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically){Text("TAP AUTOGRAPH",Modifier.weight(1f),style=MaterialTheme.typography.labelMedium,letterSpacing=2.sp);TextButton(onClick=onClose){Text("Hide")}}
        AnimatedContent(targetState=if(showCheck)"check" else if(phase<2)"card" else if(phase==5)"done" else "ink",transitionSpec={fadeIn(tween(if(reduced)0 else 220)) togetherWith fadeOut(tween(if(reduced)0 else 150))},label="tap stage"){stage->
            Box(Modifier.fillMaxWidth().height(if(stage=="ink")44.dp else 168.dp),contentAlignment=Alignment.Center){
                when(stage){
                    "card"->Column(Modifier.fillMaxWidth(.92f).height(158.dp).clip(RoundedCornerShape(24.dp)).background(Brush.linearGradient(listOf(Color(0xFF387BF0),Color(0xFF203B73)))).padding(22.dp),verticalArrangement=Arrangement.SpaceBetween){
                        Text(demoCards[WalletTap.selectedCard(context)].title,color=Color.White,fontSize=23.sp)
                        Text("••••  "+demoCards[WalletTap.selectedCard(context)].digits,color=Color.White,fontSize=21.sp)
                    }
                    "check","done"->Box(Modifier.size(130.dp).scale(if(reduced)1f else halo).background(if(stage=="done")Color(0xFF18795C) else Color(0xFF2764E7),CircleShape),contentAlignment=Alignment.Center){Text("✓",color=Color.White,fontSize=70.sp)}
                    else->Text(if(phase==4)"↗" else "✦",color=accent,fontSize=32.sp)
                }
            }
        }
        Text(when {showCheck->"Demo payment accepted";phase==5->"A little you. Delivered.";phase==6->"Finished without autograph";phase==4->"Sending your autograph…";phase>=2->"Tap again. Make it yours.";else->"Hold near the reader"},style=MaterialTheme.typography.headlineSmall,color=accent)
        if(total>0)Text(money(total.toInt()),style=MaterialTheme.typography.titleLarge)
        if(phase==4){LinearProgressIndicator(progress={progress},modifier=Modifier.fillMaxWidth());Text("${(progress*100).toInt()}% · Hold steady until confirmed",style=MaterialTheme.typography.bodySmall)}
        else if(phase in 2..3){
            Text("Choose your tip on the customer screen. Tap again—or keep holding—to send the selected autograph.",style=MaterialTheme.typography.bodyMedium)
            LinearProgressIndicator(progress={seconds/45f},modifier=Modifier.fillMaxWidth())
            Text("${seconds}s left · Sharing is optional",style=MaterialTheme.typography.labelMedium)
        }
        if(phase in 2..4 && !showCheck){
            AutographDial(data,chosen,enabled=phase==2 && !choosing,remainingFraction=if(phase==2)seconds/45f else null){ink->pendingInk=ink}
            val title=data.gallery.firstOrNull{it.ink.id==chosen?.id}?.title ?: "Default autograph"
            Text(if(choosing)"Preparing your selection…" else title,style=MaterialTheme.typography.labelMedium)
        }
        if(message.isNotBlank())Text(message,style=MaterialTheme.typography.bodySmall)
        if(phase in 2..4)TextButton(onClick={card.skip();WalletTap.cooldown();TapNotice.clear(context);onClose()}){Text("Skip autograph")}
        Text("Demo only · No money moves",style=MaterialTheme.typography.labelSmall)
    }
}
