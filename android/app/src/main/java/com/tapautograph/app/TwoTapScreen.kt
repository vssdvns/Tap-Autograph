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
    var phase by remember{mutableIntStateOf(card.state())};var active by remember{mutableStateOf(card.active)}
    var total by remember{mutableLongStateOf(card.total())};var seconds by remember{mutableLongStateOf(0)}
    var message by remember{mutableStateOf("")}
    val automatic=WalletTap.enabled(context)
    val adapter=remember{NfcAdapter.getDefaultAdapter(context)}
    DisposableEffect(lifecycle){
        AutographSharing.card.disarm()
        fun preference(on:Boolean){try{if(adapter!=null&&context is Activity){val service=CardEmulation.getInstance(adapter);if(on)service.setPreferredService(context,ComponentName(context,AutographHceService::class.java))else service.unsetPreferredService(context)}}catch(_:Exception){}}
        preference(true)
        val observer=LifecycleEventObserver{_,event->if(event==Lifecycle.Event.ON_PAUSE){if(!automatic)card.stop();preference(false)}else if(event==Lifecycle.Event.ON_RESUME)preference(true)}
        lifecycle.addObserver(observer)
        onDispose{lifecycle.removeObserver(observer);if(!automatic)card.stop();preference(false)}
    }
    LaunchedEffect(Unit){
        while(true){
            phase=card.state();active=card.active;total=card.total();seconds=card.remainingSeconds()
            if(card.timedOut){WalletTap.cooldown();(context as? Activity)?.finish();break}
            delay(100)
        }
    }
    LaunchedEffect(phase){
        if(phase==5 && WalletTap.haptics(context))haptic.performHapticFeedback(HapticFeedbackType.LongPress)
        if(phase==5){delay(2200);card.stop();WalletTap.cooldown();back()}
    }
    val scale by animateFloatAsState(if(phase==2||phase==5)1f else .88f,tween(if(data.preferences.reducedMotion)0 else 450),label="tap pulse")
    Page("A tap. A little you.","TAP AUTOGRAPH",{card.stop();WalletTap.cooldown();back()}){
        Text("Harbor Coffee · Demo checkout",style=MaterialTheme.typography.titleMedium)
        Box(Modifier.fillMaxWidth().height(190.dp),contentAlignment=Alignment.Center){Box(Modifier.size(148.dp).scale(scale).background(Color(0xFF2764E7),CircleShape),contentAlignment=Alignment.Center){Text(if(phase==2||phase==5)"✓" else "↗",color=Color.White,fontSize=60.sp)}}
        Text(when(phase){1->"Confirming your tap…";2->"Demo payment accepted";3->"Review final total";4->"Sending your autograph";5->"Signed. Sealed. Shared.";6->"Autograph skipped";else->if(active)"Hold near the reader" else "Ready for your next tap"},style=MaterialTheme.typography.headlineMedium)
        if(phase>0)Text(java.text.NumberFormat.getCurrencyInstance(java.util.Locale.US).format(total/100.0),style=MaterialTheme.typography.headlineLarge)
        when(phase){
            2->{Text("Choose your tip on the merchant screen. Tap again for your autograph—or keep holding your phone nearby.");data.autograph?.let{InkPanel(it,95)}}
            3->{Text("Review the total, then share your autograph.");Primary("Share autograph"){card.approve()}}
            4->Text("Hold steady until the merchant confirms receipt.")
            5->Text("Your autograph is on the receipt.")
            6->Text("You can finish without an autograph on the merchant screen.")
        }
        if(seconds>0){LinearProgressIndicator(progress={seconds/45f},modifier=Modifier.fillMaxWidth());Text("Autograph window · ${seconds}s remaining",style=MaterialTheme.typography.labelLarge)}
        if(phase in 2..4)OutlinedButton(onClick={card.skip();WalletTap.cooldown();back()},modifier=Modifier.fillMaxWidth()){Text("Skip autograph")}
        if(!active)Primary("Ready to tap",enabled=data.autograph!=null){
            try{require(adapter?.isEnabled==true){"Turn on NFC in phone settings."};WalletTap.resetCooldown();card.start(CompactInkCodec.encode(requireNotNull(data.autograph)),autoShare=automatic,selectedCard=WalletTap.selectedCard(context));message=""}catch(e:Exception){message=e.message?:"Could not start."}
        }
        if(active)TextButton(onClick={card.stop();WalletTap.cooldown();back()}){Text("Cancel checkout")}
        if(message.isNotEmpty())Text(message)
        Text("Demo only · No money moves",style=MaterialTheme.typography.bodySmall)
    }
}
