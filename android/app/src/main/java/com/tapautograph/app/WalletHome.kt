package com.tapautograph.app

import android.app.Activity
import android.content.Intent
import android.nfc.NfcAdapter
import android.provider.Settings
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.delay

@Composable fun WalletHome(vm:WalletViewModel,data:SavedData,navigate:(String)->Unit){
    val context=LocalContext.current
    val selected by vm.selectedCard.collectAsStateWithLifecycle()
    var tab by rememberSaveable{mutableIntStateOf(0)}
    var enabled by remember{mutableStateOf(WalletTap.enabled(context))}
    var defaultWallet by remember{mutableStateOf(WalletTap.isDefault(context))}
    var consent by remember{mutableStateOf(false)}
    var wheel by remember{mutableStateOf(false)}
    var notificationsAllowed by remember{mutableStateOf(android.os.Build.VERSION.SDK_INT<33 || androidx.core.content.ContextCompat.checkSelfPermission(context,android.Manifest.permission.POST_NOTIFICATIONS)==android.content.pm.PackageManager.PERMISSION_GRANTED)}
    val notifications=androidx.activity.compose.rememberLauncherForActivityResult(androidx.activity.result.contract.ActivityResultContracts.RequestPermission()){notificationsAllowed=it}
    var message by remember{mutableStateOf("")}
    LaunchedEffect(Unit){vm.selectedCard.value=WalletTap.selectedCard(context);while(true){defaultWallet=WalletTap.isDefault(context);delay(1000)}}
    Column(Modifier.fillMaxSize().safeDrawingPadding()){
        Column(Modifier.weight(1f)){
            if(tab==0)Page("Tap Autograph","YOUR WALLET",action={IconButton(onClick={navigate("settings")}){Icon(Icons.Rounded.Settings,"Settings")}}){
                Text("A little personality with every tap.",color=MaterialTheme.colorScheme.onSurfaceVariant)
                WalletCardStack(selected,data.preferences.reducedMotion){vm.selectedCard.value=it;WalletTap.selectCard(context,it)}
                Text(demoCards[selected].title+"  •••• "+demoCards[selected].digits,style=MaterialTheme.typography.titleMedium)
                Text("Swipe to choose · Fictional demo cards",style=MaterialTheme.typography.bodySmall)
                Block{
                    Row(verticalAlignment=Alignment.CenterVertically){Icon(Icons.Rounded.Contactless,null);Spacer(Modifier.width(12.dp));Text(if(enabled)"Ready when you tap" else "Make your next tap yours",style=MaterialTheme.typography.titleLarge)}
                    Text("First tap: demo payment. Then tap again to share your saved autograph automatically. You have 45 seconds, or simply walk away.")
                    Row(verticalAlignment=Alignment.CenterVertically){Text("Automatic autograph",Modifier.weight(1f));Switch(checked=enabled,onCheckedChange={if(it)consent=true else{enabled=false;WalletTap.enable(context,false)}})}
                    if(!enabled)Primary("Enable tap-ready wallet",enabled=data.autograph!=null){consent=true}
                    if(enabled && !notificationsAllowed)TextButton(onClick={notifications.launch(android.Manifest.permission.POST_NOTIFICATIONS)}){Text("Allow tap notifications")}
                    if(enabled)Primary("Preview tap card"){
                        try{require(NfcAdapter.getDefaultAdapter(context)?.isEnabled==true){"Turn on NFC first."};WalletTap.resetCooldown();AutographSharing.checkout.start(TapPacket.encode(requireNotNull(data.autograph)),autoShare=true,selectedCard=selected);context.startActivity(Intent(context,TapActivity::class.java))}catch(e:Exception){message=e.message?:"Choose a saved autograph first."}
                    }
                }
                Block{
                    Text(if(defaultWallet)"Default wallet selected" else "Tap without opening the wallet",style=MaterialTheme.typography.titleMedium)
                    Text("Android handles unlocking. Your Samsung may ask you to unlock and tap again. Keep the screen awake for testing.")
                    if(!defaultWallet)OutlinedButton(onClick={runCatching{WalletTap.requestDefault(context as Activity)}.onFailure{message="Open Settings → Apps → Default apps → Wallet."}},modifier=Modifier.fillMaxWidth()){Text("Choose default wallet")}
                    Text("Selecting this demo replaces your current default wallet. Switch back to Google Wallet or Samsung Wallet for real purchases.",style=MaterialTheme.typography.bodySmall)
                    TextButton(onClick={context.startActivity(Intent(Settings.ACTION_NFC_SETTINGS))}){Text("NFC settings")}
                }
                if(message.isNotEmpty())Text(message)
                Text("Your autograph",style=MaterialTheme.typography.titleLarge)
                data.autograph?.let{InkPanel(it,110)}?:Text("Create or select an autograph in the Studio tab.")
                OutlinedButton(onClick={wheel=true},modifier=Modifier.fillMaxWidth()){Text("✦  Favorite emote wheel")}
                OutlinedButton(onClick={navigate("gallery")},modifier=Modifier.fillMaxWidth()){Text("Choose autograph")}
                Text("Independent prototype · Demo payments only",style=MaterialTheme.typography.bodySmall)
            }else Page("Make it yours","AUTOGRAPH STUDIO"){
                Text("Write it, scan it, or give your receipt a mood.")
                Primary("Your gallery"){navigate("gallery")}
                OutlinedButton(onClick={navigate("studio-name")},modifier=Modifier.fillMaxWidth()){Text("Name styles")}
                OutlinedButton(onClick={navigate("studio-doodles")},modifier=Modifier.fillMaxWidth()){Text("Mood doodles")}
                OutlinedButton(onClick={navigate("studio-photo")},modifier=Modifier.fillMaxWidth()){Text("Photo & meme importer")}
                OutlinedButton(onClick={navigate("receipt-art")},modifier=Modifier.fillMaxWidth()){Text("Receipt artwork")}
                Text("Developer tools",style=MaterialTheme.typography.titleMedium)
                TextButton(onClick={navigate("live")}){Text("Wi-Fi merchant checkout")}
                TextButton(onClick={navigate("nfc-lab")}){Text("NFC payload lab")}
            }
        }
        NavigationBar{
            NavigationBarItem(selected=tab==0,onClick={tab=0},icon={Icon(Icons.Rounded.AccountBalanceWallet,null)},label={Text("Wallet")})
            NavigationBarItem(selected=tab==1,onClick={tab=1},icon={Icon(Icons.Rounded.Draw,null)},label={Text("Studio")})
        }
    }
    if(wheel)FavoriteWheel(data,sessionOnly=false,onDismiss={wheel=false}){ink->vm.selectDesign(ink.id);wheel=false}
    if(consent)AlertDialog(onDismissRequest={consent=false},title={Text("Let your next tap share it")},text={Text("After a demo payment is accepted, presenting your phone again sends the selected autograph without another confirmation. The window lasts 45 seconds. This works only with our prototype reader; no real money moves. You can turn it off here anytime.")},confirmButton={TextButton(onClick={if(android.os.Build.VERSION.SDK_INT>=33)notifications.launch(android.Manifest.permission.POST_NOTIFICATIONS);enabled=true;WalletTap.enable(context,true);WalletTap.resetCooldown();consent=false}){Text("Enable")}},dismissButton={TextButton(onClick={consent=false}){Text("Not now")}})
}

@Composable private fun WalletCardStack(selected:Int,reducedMotion:Boolean,onSelect:(Int)->Unit){
    var drag by remember{mutableFloatStateOf(0f)}
    Box(Modifier.fillMaxWidth().height(260.dp).pointerInput(selected){detectHorizontalDragGestures(onDragStart={drag=0f},onDragEnd={if(kotlin.math.abs(drag)>45)onSelect((selected+1)%demoCards.size);drag=0f},onDragCancel={drag=0f}){change,amount->change.consume();drag+=amount}},contentAlignment=Alignment.Center){
        demoCards.indices.forEach{index->
            val front=index==selected
            val progress by animateFloatAsState(if(front)1f else 0f,tween(if(reducedMotion)0 else 420),label="card position")
            val colors=if(index==0)listOf(Color(0xFF3478EC),Color(0xFF193464))else listOf(Color(0xFF8465C2),Color(0xFF33294E))
            Column(Modifier.fillMaxWidth(.94f).height(198.dp).zIndex(if(front)2f else 1f).graphicsLayer{translationY=(1-progress)*(-27.dp.toPx());scaleX=.92f+.08f*progress;scaleY=.92f+.08f*progress;rotationZ=(1-progress)*-3;translationX=if(front)drag*.25f else 0f}.clip(RoundedCornerShape(28.dp)).background(Brush.linearGradient(colors)).clickable{onSelect(index)}.padding(24.dp),verticalArrangement=Arrangement.SpaceBetween){
                Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween){Text(demoCards[index].title,color=Color.White,fontSize=23.sp,fontWeight=FontWeight.Medium);Icon(Icons.Rounded.Contactless,null,tint=Color.White)}
                Text("DEMO CARD",color=Color(0xFFE1E8FF),letterSpacing=2.sp,fontSize=11.sp)
                Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween){Text("••••  "+demoCards[index].digits,color=Color.White,fontSize=21.sp);Text("tap /",color=Color.White,fontSize=23.sp,fontWeight=FontWeight.Bold)}
            }
        }
    }
}
