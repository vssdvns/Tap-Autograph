package com.tapautograph.app

import android.content.pm.PackageManager
import android.nfc.NfcAdapter
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext

@Composable fun HcePanel(packet:ByteArray) {
    val context=LocalContext.current;val lifecycle=LocalLifecycleOwner.current.lifecycle
    val scope=rememberCoroutineScope();var status by remember { mutableStateOf("Sharing is off") }
    var remaining by remember { mutableLongStateOf(0) };var confirm by remember { mutableStateOf(false) }
    var simulation by remember { mutableStateOf<ReaderSimulation?>(null) };var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) };var nfcOn by remember { mutableStateOf(false) }
    val supported=remember { context.packageManager.hasSystemFeature(PackageManager.FEATURE_NFC_HOST_CARD_EMULATION) }
    val export=rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        val text=simulation?.transcript
        if(uri!=null && text!=null)scope.launch {
            try { withContext(Dispatchers.IO){requireNotNull(context.contentResolver.openOutputStream(uri)).use { it.write(text.toByteArray()) }};message="Reader transcript exported. It contains unencrypted handwriting." }
            catch(_:Exception){message="Could not export the reader transcript."}
        }
    }
    DisposableEffect(lifecycle,packet) {
        AutographSharing.card.disarm()
        val observer=LifecycleEventObserver { _,event -> if(event==Lifecycle.Event.ON_STOP)AutographSharing.card.disarm() }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer);AutographSharing.card.disarm() }
    }
    LaunchedEffect(Unit) { while(true) {
        status=AutographSharing.card.status();remaining=AutographSharing.card.remainingSeconds()
        nfcOn=NfcAdapter.getDefaultAdapter(context)?.isEnabled==true
        delay(500)
    }}
    Block {
        Text("Reader sharing",style=MaterialTheme.typography.titleLarge)
        Text(status + if(remaining>0) " · ${remaining}s" else "")
        Text(if(!supported) "This device does not report NFC card emulation support. The simulator still works." else if(!nfcOn) "Turn on NFC in phone settings to try a reader." else "NFC is on. Keep this screen open and your phone unlocked while sharing.")
        Primary("Enable one transfer for 60 seconds",enabled=supported && nfcOn && !busy) { confirm=true }
        OutlinedButton(enabled=!busy,onClick={AutographSharing.card.disarm();status=AutographSharing.card.status()}) { Text("Stop sharing") }
        Text("Experimental reader only. A reader can copy the autograph during this window. This version has no encryption, merchant authentication or payment authorization.",style=MaterialTheme.typography.bodySmall)
    }
    Block {
        Text("Test without hardware",style=MaterialTheme.typography.titleLarge)
        Text("Run the same command handler with an isolated simulated reader. It retries a chunk, interrupts the link, reconnects and verifies the reconstructed packet.")
        Primary(if(busy) "Testing…" else "Run simulated reader",enabled=!busy) {
            scope.launch { busy=true;message=null;try {
                simulation=withContext(Dispatchers.Default){AutographReaderSimulator.run(packet)}
                message="Verified ${packet.size} bytes in ${simulation!!.chunks} chunks. Simulated only; no NFC radio transfer."
            } catch(_:Exception){message="Simulation failed. Stop sharing and retry."} finally {busy=false} }
        }
        simulation?.let { OutlinedButton(onClick={export.launch("tap-autograph-reader.json")}) { Text("Export reader test for laptop") } }
        message?.let { Text(it) }
    }
    if(confirm)AlertDialog(onDismissRequest={confirm=false},title={Text("Share with a nearby reader?")},text={Text("Any compatible reader in range can request this saved autograph while enabled. Keep this screen open. Sharing stops when you leave, after 60 seconds, or when a reader acknowledges completion.")},confirmButton={TextButton(onClick={
        confirm=false;scope.launch {busy=true;try {withContext(Dispatchers.Default){AutographSharing.card.arm(packet)};status=AutographSharing.card.status()}catch(_:Exception){message="Could not enable sharing."}finally{if(!currentCoroutineContext().isActive || !lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED))AutographSharing.card.disarm();busy=false}}
    }){Text("Enable sharing")}},dismissButton={TextButton(onClick={confirm=false}){Text("Cancel")}})
}
