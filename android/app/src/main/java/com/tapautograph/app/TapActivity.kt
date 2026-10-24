package com.tapautograph.app

import android.os.Bundle
import android.view.Gravity
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle

/** Compact, dismissible checkout surface; NFC works without this activity being visible. */
class TapActivity:ComponentActivity(){
    companion object {@Volatile var visible=false; @Volatile var hidden=false}
    override fun onCreate(savedInstanceState:Bundle?){
        super.onCreate(savedInstanceState)
        window.setGravity(Gravity.BOTTOM)
        window.setLayout(WindowManager.LayoutParams.MATCH_PARENT,WindowManager.LayoutParams.WRAP_CONTENT)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        val vm=ViewModelProvider(this)[WalletViewModel::class.java]
        setContent {
            val data by vm.data.collectAsStateWithLifecycle()
            val ready by vm.ready.collectAsStateWithLifecycle()
            MaterialTheme(colorScheme=darkColorScheme(primary=Color(0xFF94BBFF),background=Color(0xFF101A30),surface=Color(0xFF101A30))){
                Surface(Modifier.fillMaxWidth(),shape=androidx.compose.foundation.shape.RoundedCornerShape(topStart=32.dp,topEnd=32.dp)){
                    if(ready)CompactTap(data,onClose={hidden=true;finish()}) else Box(Modifier.padding(40.dp)){CircularProgressIndicator()}
                }
            }
        }
    }
    override fun onResume(){
        super.onResume();visible=true;hidden=false;TapNotice.clear(this)
        runCatching{android.nfc.NfcAdapter.getDefaultAdapter(this)?.let{android.nfc.cardemulation.CardEmulation.getInstance(it).setPreferredService(this,android.content.ComponentName(this,AutographHceService::class.java))}}
    }
    override fun onStop(){if(isFinishing)hidden=true;super.onStop()}
    override fun onPause(){
        visible=false
        runCatching{android.nfc.NfcAdapter.getDefaultAdapter(this)?.let{android.nfc.cardemulation.CardEmulation.getInstance(it).unsetPreferredService(this)}}
        super.onPause()
    }
}
