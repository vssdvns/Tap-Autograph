package com.tapautograph.app

import android.app.Activity
import android.content.ComponentName
import android.nfc.NfcAdapter
import android.nfc.cardemulation.CardEmulation
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlinx.coroutines.delay

@Composable fun TwoTapScreen(data:SavedData,back:()->Unit){
    val context=LocalContext.current;val lifecycle=LocalLifecycleOwner.current.lifecycle
    val haptic=LocalHapticFeedback.current;val card=AutographSharing.checkout
    var phase by remember{mutableIntStateOf(0)};var active by remember{mutableStateOf(false)}
    var total by remember{mutableLongStateOf(0)};var message by remember{mutableStateOf("")}
    val adapter=remember{NfcAdapter.getDefaultAdapter(context)}
    DisposableEffect(lifecycle){
        AutographSharing.card.disarm();card.stop()
        fun preference(on:Boolean){try{if(adapter!=null&&context is Activity){val service=CardEmulation.getInstance(adapter);if(on)service.setPreferredService(context,ComponentName(context,AutographHceService::class.java)) else service.unsetPreferredService(context)}}catch(_:Exception){}}
        preference(true)
        val observer=LifecycleEventObserver{_,event->if(event==Lifecycle.Event.ON_PAUSE){card.stop();preference(false)}else if(event==Lifecycle.Event.ON_RESUME)preference(true)}
        lifecycle.addObserver(observer)
        onDispose{lifecycle.removeObserver(observer);card.stop();preference(false)}
    }
    LaunchedEffect(Unit){while(true){phase=card.state();active=card.active;total=card.total();delay(100)}}
    LaunchedEffect(phase){if(phase==2||phase==5)haptic.performHapticFeedback(HapticFeedbackType.LongPress)}
    val scale by animateFloatAsState(if(phase==2||phase==5)1f else .85f,tween(450),label="tap pulse")
    Page("Tap. Then make it yours.","TWO-TAP DEMO",back){
        Text("Harbor Coffee · USD demo")
        Text("Starting permits fictional card acceptance on the first tap. Your autograph still needs your approval after the second tap.")
        Text("Demo only · No money moves · Keep phone unlocked and this screen open")
        Box(Modifier.fillMaxWidth().height(170.dp),contentAlignment=Alignment.Center){
            Box(Modifier.size(135.dp).scale(scale).background(Color(0xFF2764E7),CircleShape),contentAlignment=Alignment.Center){Text(if(phase==2||phase==5)"✓" else "↗",color=Color.White,fontSize=56.sp)}
        }
        Text(when(phase){1->"Confirming demo tap…";2->"Demo card accepted";3->"Review final total";4->"Sharing autograph…";5->"Autograph received by merchant";6->"Autograph skipped";else->if(active)"Ready for your first tap" else "Start a demo checkout"},style=MaterialTheme.typography.headlineSmall)
        if(phase>0)Text(java.text.NumberFormat.getCurrencyInstance(java.util.Locale.US).format(total/100.0),style=MaterialTheme.typography.headlineMedium)
        when(phase){
            2->Text("Move your phone away. Choose a tip on the merchant screen, then tap again for your autograph.")
            3->{Text("Keep holding near the reader. Confirm this final total to share your saved autograph with this demo order.");Primary("Share autograph") {card.approve()};OutlinedButton(onClick={card.skip()}){Text("Skip autograph")}}
            4->Text("Hold steady until the merchant acknowledges receipt.")
            5->Text("All done. Remove your phone from the reader.")
            6->Text("Move your phone away. If needed, choose Finish without autograph on the merchant.")
        }
        if(!active||phase in listOf(5,6))Primary("Start two-tap demo",enabled=data.autograph!=null){
            try{require(adapter?.isEnabled==true){"Turn on NFC in phone settings."};card.start(CompactInkCodec.encode(requireNotNull(data.autograph)));message=""}catch(e:Exception){message=e.message?:"Could not start."}
        }
        if(active)OutlinedButton(onClick={card.stop()}){Text("Stop demo")}
        if(data.autograph==null)Text("Save an autograph in your gallery first.")
        if(message.isNotEmpty())Text(message)
        Text("The demo lasts 3 minutes. Returning to another app cancels it. NFC is unencrypted and the merchant is not authenticated.",style=MaterialTheme.typography.bodySmall)
    }
}
