package com.tapautograph.app

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@Composable fun LiveScreen(vm: WalletViewModel, data: SavedData, back: () -> Unit, createAutograph: () -> Unit) {
    val offer by vm.pairOffer.collectAsStateWithLifecycle()
    val remote by vm.liveCheckout.collectAsStateWithLifecycle()
    val busy by vm.liveBusy.collectAsStateWithLifecycle()
    val error by vm.liveError.collectAsStateWithLifecycle()
    val connected by vm.liveConnected.collectAsStateWithLifecycle()
    val selected by vm.selectedCard.collectAsStateWithLifecycle()
    var link by rememberSaveable { mutableStateOf("") }
    var leave by remember { mutableStateOf(false) }
    val checkout = remote
    Page(when(checkout?.stage) {
        "paired" -> "Ready to tap."
        "tip" -> "Over to the counter."
        "autograph" -> "Your signature moment."
        "animating" -> "Look at the terminal."
        "completed" -> "A little more you."
        "cancelled", "expired" -> "Checkout ended."
        else -> "Connect your wallet."
    }, "CONNECTED CHECKOUT", back) {
        if (error != null) Block {
            Text(error!!, color = MaterialTheme.colorScheme.error)
            TextButton(onClick = vm::clearLiveError) { Text("Dismiss") }
        }
        if (!connected && checkout != null && checkout.stage != "expired") Text("Connection interrupted. Reconnecting automatically…", color = MaterialTheme.colorScheme.error)
        if (checkout == null) {
            if (offer == null) {
                Text("On your laptop, start the merchant demo and create a checkout. Scan its QR code with your phone camera, then choose Open Tap Autograph.")
                Text("Or paste the pairing link below:")
                OutlinedTextField(value = link, onValueChange = { link = it }, label = { Text("Pairing link") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri), modifier = Modifier.fillMaxWidth(), maxLines = 5)
                Primary("Review connection", enabled = link.isNotBlank()) { vm.preparePairing(link) }
            } else {
                Block {
                    Text("Local merchant connection", style = MaterialTheme.typography.titleLarge)
                    Text(offer!!.base)
                    Text("Connect to the checkout displayed on your laptop. The app will show the merchant and purchase before you simulate a card tap.")
                }
                Primary(if (busy) "Connecting…" else "Connect to this checkout", enabled = !busy, onClick = vm::connectLive)
                TextButton(onClick = vm::disconnectLive, enabled = !busy) { Text("Use a different pairing link") }
            }
            Text("Same Wi-Fi required. This prototype uses unencrypted local HTTP. Use a playful autograph, not an official signature. Payment remains simulated.", style = MaterialTheme.typography.bodySmall)
        } else {
            Block {
                Text(checkout.merchant, style = MaterialTheme.typography.titleLarge)
                checkout.items.forEach { item -> Detail("${item.quantity} × ${item.name}", money(item.price * item.quantity)) }
                HorizontalDivider()
                Detail("Subtotal", money(checkout.subtotal)); Detail("Tax", money(checkout.tax))
                if (checkout.stage !in listOf("paired","tip")) Detail("Tip", money(checkout.tip))
                Detail(if (checkout.stage in listOf("paired","tip")) "Total before tip" else "Final total", money(checkout.total), strong = true)
            }
            when(checkout.stage) {
                "paired" -> {
                    Text("Choose your demo card. This button simulates the first card tap; no money moves.")
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        demoCards.forEachIndexed { i, card -> FilterChip(selected = selected == i, onClick = { vm.selectedCard.value = i }, label = { Text("•••• ${card.digits}") }) }
                    }
                    Primary(if (busy) "Sending…" else "Simulate card tap", enabled = !busy) { vm.liveAction("pay") }
                }
                "tip" -> Text("Select a tip or no tip on the merchant screen, then confirm the total there. Your final amount will appear here automatically.")
                "autograph" -> {
                    Text("Review the final amount above. Sharing is optional personalization, not identity verification or payment authorization.")
                    data.autograph?.let { autograph ->
                        InkPanel(autograph)
                        Primary(if (busy) "Sending…" else "Share autograph for this purchase", enabled = !busy) { vm.liveAction("share") }
                    } ?: Primary("Create autograph", enabled = !busy, onClick = createAutograph)
                    OutlinedButton(onClick = { vm.liveAction("skip") }, enabled = !busy, modifier = Modifier.fillMaxWidth()) { Text("Finish without autograph") }
                }
                "animating" -> {
                    Text("Your handwriting is appearing on the laptop. Keep the merchant page open to finish the animation and receipt.")
                    data.autograph?.let { InkPanel(it) }
                }
                "completed" -> {
                    Text("Demo payment complete", style = MaterialTheme.typography.titleLarge)
                    Text(if (checkout.autographShared) "Your autograph was shared. The relay's handwriting copy has been removed." else "Completed without an autograph.")
                    Primary("Back to wallet") { vm.disconnectLive(); back() }
                }
                else -> {
                    Text("This checkout is no longer active. Start a fresh one on your laptop and scan the new QR code.")
                    Primary("Use a new pairing link", onClick = vm::disconnectLive)
                }
            }
            if (checkout.stage !in listOf("completed","cancelled","expired")) TextButton(onClick = { leave = true }) { Text("Disconnect this phone") }
            Text("Local Wi-Fi demo • No NFC • No real payment", style = MaterialTheme.typography.bodySmall)
        }
    }
    if (leave) AlertDialog(onDismissRequest = { leave=false }, title = { Text("Disconnect phone?") }, text = { Text("The merchant checkout will remain open until cancelled or expired. Start a new checkout to pair again.") }, confirmButton = { TextButton(onClick = { vm.disconnectLive(); leave=false }) { Text("Disconnect") } }, dismissButton = { TextButton(onClick = { leave=false }) { Text("Stay connected") } })
}
