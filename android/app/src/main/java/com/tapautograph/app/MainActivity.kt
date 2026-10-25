package com.tapautograph.app

import android.os.Bundle
import android.content.Intent
import androidx.lifecycle.ViewModelProvider
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.Redo
import androidx.compose.material.icons.automirrored.rounded.Undo
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.*
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val Blue = Color(0xFFAAC7FF)
private val Navy = Color(0xFF0B1020)
private val Panel = Color(0xFF151E31)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        CheckoutFeedback.warmUp(this)
        val wallet = ViewModelProvider(this)[WalletViewModel::class.java]
        if (savedInstanceState == null) intent?.dataString?.let(wallet::preparePairing)
        setContent { WalletApp(wallet) }
    }
    override fun onResume(){
        super.onResume()
        if(WalletTap.enabled(this))runCatching {
            android.nfc.NfcAdapter.getDefaultAdapter(this)?.let { adapter ->
                android.nfc.cardemulation.CardEmulation.getInstance(adapter).setPreferredService(this,android.content.ComponentName(this,AutographHceService::class.java))
            }
        }
    }
    override fun onPause(){
        runCatching{android.nfc.NfcAdapter.getDefaultAdapter(this)?.let{android.nfc.cardemulation.CardEmulation.getInstance(it).unsetPreferredService(this)}}
        super.onPause()
    }
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        intent.dataString?.let { ViewModelProvider(this)[WalletViewModel::class.java].preparePairing(it) }
    }
}

@Composable fun WalletApp(vm: WalletViewModel = viewModel()) {
    val data by vm.data.collectAsStateWithLifecycle()
    val ready by vm.ready.collectAsStateWithLifecycle()
    val error by vm.error.collectAsStateWithLifecycle()
    val dark = when(data.preferences.theme) { "light" -> false; "system" -> androidx.compose.foundation.isSystemInDarkTheme(); else -> true }
    val colors = if (dark) darkColorScheme(primary = Blue, background = Navy, surface = Navy, surfaceVariant = Panel)
        else lightColorScheme(primary = Color(0xFF295BB2), background = Color(0xFFF3F6FC), surface = Color(0xFFF3F6FC), surfaceVariant = Color(0xFFE3EAF5))
    MaterialTheme(colorScheme = colors) {
        Surface(Modifier.fillMaxSize()) {
            if (!ready) Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            else {
                val nav = rememberNavController()
                val offer by vm.pairOffer.collectAsStateWithLifecycle()
                LaunchedEffect(Unit) {
                    var shown=false
                    while(true){
                        val phase=AutographSharing.checkout.state()
                        if(phase==0)shown=false
                        if(nav.currentDestination?.route!="two-tap" && phase in 1..4 && !shown && !TapActivity.visible && !TapActivity.hidden){
                            shown=true
                            vm.getApplication<android.app.Application>().let { app ->
                                app.startActivity(Intent(app,TapActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP))
                            }
                        }
                        kotlinx.coroutines.delay(150)
                    }
                }
                LaunchedEffect(offer?.id) { if (offer != null) nav.navigate("live") { launchSingleTop = true } }
                NavHost(nav, startDestination = "wallet") {
                    composable("wallet") { WalletHome(vm, data, { nav.navigate(it) }) }
                    composable("autograph") { AutographScreen(vm, data, { nav.popBackStack() }, { vm.startEditor("autograph"); nav.navigate("editor") }) }
                    composable("editor") { Editor(vm, data.preferences, {
                        if(vm.saveAutograph())nav.popBackStack()
                    }, { nav.popBackStack() }) }
                    composable("checkout") { CheckoutScreen(vm, data, { nav.popBackStack() }, {
                        vm.startEditor("checkout"); nav.navigate("editor")
                    }) }
                    composable("history/{id}") { entry ->
                        val transaction = data.transactions.find { it.id == entry.arguments?.getString("id") }
                        Page("Receipt", "DEMO TRANSACTION", { nav.popBackStack() }) {
                            if (transaction == null) Text("This transaction is no longer stored.")
                            else Receipt(transaction, data.designForReceiptHistory(transaction.autographId))
                        }
                    }
                    composable("live") { LiveScreen(vm, data, { nav.popBackStack() }, {
                        vm.startEditor("live"); nav.navigate("editor")
                    }) }
                    composable("two-tap") { TwoTapScreen(data){nav.popBackStack()} }
                    composable("nfc-lab") { NfcLabScreen(data, { nav.popBackStack() }, { vm.startEditor("nfc-lab"); nav.navigate("editor") }) }
                    composable("gallery") { StudioGallery(vm,data,{nav.popBackStack()},{nav.navigate(it)},{vm.startEditor("gallery",fresh=true);nav.navigate("editor")}) }
                    composable("studio-name") { NameStudio(vm,data){nav.popBackStack()} }
                    composable("studio-doodles") { DoodleStudio(vm,data){nav.popBackStack()} }
                    composable("studio-photo") { PhotoStudio(vm,data){vm.clearStudioPhoto();nav.popBackStack()} }
                    composable("receipt-art") { ReceiptStudio(vm,data){nav.popBackStack()} }
                    composable("settings") { Settings(vm, data, { nav.popBackStack() }) }
                }
            }
            if (error != null) AlertDialog(onDismissRequest = vm::clearError, title = { Text("Could not save changes") }, text = { Text(error!!) }, confirmButton = { TextButton(onClick = vm::clearError) { Text("OK") } })
        }
    }
}

@Composable internal fun Page(title: String, eyebrow: String, back: (() -> Unit)? = null, action: (@Composable () -> Unit)? = null, content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxSize().safeDrawingPadding().imePadding().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp).padding(bottom = 28.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
        Row(Modifier.fillMaxWidth().padding(top = 14.dp), verticalAlignment = Alignment.CenterVertically) {
            if (back != null) IconButton(onClick = back) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back") }
            Text(eyebrow, Modifier.weight(1f), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary, letterSpacing = 2.sp)
            action?.invoke()
        }
        Text(title, style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.SemiBold)
        content()
    }
}
@Composable internal fun Block(content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(MaterialTheme.colorScheme.surfaceVariant).padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp), content = content)
}
@Composable internal fun Primary(text: String, enabled: Boolean = true, onClick: () -> Unit) {
    Button(onClick, Modifier.fillMaxWidth().heightIn(min = 56.dp), enabled = enabled, shape = RoundedCornerShape(18.dp)) { Text(text, fontWeight = FontWeight.SemiBold) }
}
@Composable internal fun Detail(label: String, value: String, strong: Boolean = false) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, Modifier.weight(1f), color = if (strong) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, fontWeight = if (strong) FontWeight.Bold else FontWeight.Normal)
    }
}
@Composable private fun CardArt(index: Int, selected: Boolean, onClick: () -> Unit) {
    val colors = if (index == 0) listOf(Color(0xFF315DB0), Color(0xFF172E62)) else listOf(Color(0xFF6A527E), Color(0xFF302540))
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(26.dp)).background(Brush.linearGradient(colors)).border(if (selected) 2.dp else 0.dp, if (selected) Blue else Color.Transparent, RoundedCornerShape(26.dp)).clickable(onClick = onClick).padding(24.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(demoCards[index].title, color = Color.White, fontSize = 21.sp, fontWeight = FontWeight.Medium)
            Icon(if (selected) Icons.Rounded.CheckCircle else Icons.Rounded.CreditCard, if (selected) "Selected card" else "Select card", tint = Color.White)
        }
        Icon(Icons.Rounded.Contactless, null, tint = Color(0xFFD6E5FF))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Bottom) {
            Column { Text("••••  ${demoCards[index].digits}", color = Color.White, fontSize = 19.sp); Text("FICTIONAL DEMO CARD", color = Color(0xFFD6E5FF), fontSize = 10.sp, letterSpacing = 1.sp) }
            Text("tap /", color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Bold)
        }
    }
}
@Composable private fun Home(vm: WalletViewModel, data: SavedData, navigate: (String) -> Unit) {
    val selected by vm.selectedCard.collectAsStateWithLifecycle()
    Page("Your everyday,\nwith a little you.", "TAP AUTOGRAPH", action = { IconButton(onClick = { navigate("settings") }) { Icon(Icons.Rounded.Settings, "Settings") } }) {
        Text("Independent wallet concept", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        demoCards.indices.forEach { index -> CardArt(index, index == selected) { vm.selectedCard.value = index } }
        Block {
            Row(verticalAlignment = Alignment.CenterVertically) { Icon(Icons.Rounded.QrCodeScanner, null, tint = MaterialTheme.colorScheme.primary); Spacer(Modifier.width(10.dp)); Text("Connect to merchant", style = MaterialTheme.typography.titleLarge) }
            Text("Scan the laptop's QR code with your phone camera, or paste its pairing link here.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            OutlinedButton(onClick = { navigate("live") }, Modifier.fillMaxWidth()) { Text("Open connected checkout") }
        }
        Block {
            Text("Your autograph studio",style=MaterialTheme.typography.titleLarge)
            Text("Draw, import from paper, explore name styles, or pick a mood doodle.")
            Primary("Open your gallery") { navigate("gallery") }
        }
        Primary("Two-tap NFC checkout") { navigate("two-tap") }
        Primary("Try demo checkout") { vm.beginCheckout(); navigate("checkout") }
        Block {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.Draw, null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(10.dp)); Text("Your autograph", style = MaterialTheme.typography.titleLarge)
            }
            Text(if (data.autograph == null) "Write it once. Make checkout yours." else "Your handwriting. Ready for its next appearance.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            data.autograph?.let { InkPanel(it, height = 100) }
            OutlinedButton(onClick = { navigate("autograph") }, Modifier.fillMaxWidth()) { Text(if (data.autograph == null) "Create autograph" else "Preview & manage") }
        }
        Block {
            Text("NFC payload lab", style = MaterialTheme.typography.titleLarge)
            Text("Encode a compact copy and compare handwriting playback.")
            OutlinedButton(onClick = { navigate("nfc-lab") }, Modifier.fillMaxWidth()) { Text("Open payload lab") }
        }
        Text("Recent activity", style = MaterialTheme.typography.titleLarge)
        if (data.transactions.isEmpty()) Text("Your first demo purchase will appear here.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        data.transactions.take(5).forEach { transaction ->
            Surface(onClick = { navigate("history/${transaction.id}") }, shape = RoundedCornerShape(18.dp), color = MaterialTheme.colorScheme.surfaceVariant) {
                Row(Modifier.fillMaxWidth().padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.LocalCafe, null); Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) { Text(transaction.merchant); Text("Demo • ${date(transaction.time)}", style = MaterialTheme.typography.bodySmall) }
                    Text(money(transaction.subtotal + transaction.tax + transaction.tip))
                }
            }
        }
    }
}

@Composable fun InkPanel(autograph: Autograph, height: Int = 180, elapsed: Long = Long.MAX_VALUE, editable: Boolean = false, onStroke: (InkStroke) -> Unit = {}) {
    AndroidView(factory = { InkView(it) }, modifier = Modifier.fillMaxWidth().height(height.dp).clip(RoundedCornerShape(20.dp)).background(Color(0xFFF5F8FF)), update = {
        it.autograph = autograph; it.elapsed = elapsed; it.editable = editable; it.onStroke = onStroke
        it.contentDescription = if (editable) "Draw your autograph here with S Pen or finger" else "Autograph preview"
    })
}
@Composable internal fun AnimatedInk(autograph: Autograph, preferences: Preferences, replay: Int = 0, onFinished: () -> Unit = {}) {
    val strokes = remember(autograph) { playbackStrokes(autograph.strokes) }
    val normalized = remember(autograph) { autograph.copy(strokes = strokes) }
    val duration = strokes.lastOrNull()?.points?.lastOrNull()?.time ?: 0L
    var elapsed by remember(autograph.id, replay) { mutableLongStateOf(0L) }
    val finish by rememberUpdatedState(onFinished)
    LaunchedEffect(autograph.id, preferences.speed, preferences.reducedMotion, replay) {
        if (preferences.reducedMotion || duration == 0L) elapsed = Long.MAX_VALUE
        else {
            val speed = preferences.speed.coerceIn(.5f, 2f)
            val start = withFrameNanos { it }
            do {
                elapsed = withFrameNanos { playbackPosition(it - start, speed) }
            } while (elapsed < duration)
            elapsed = Long.MAX_VALUE
        }
        if (!preferences.reducedMotion) kotlinx.coroutines.delay(700)
        finish()
    }
    InkPanel(normalized, elapsed = elapsed)
}
@Composable private fun AutographScreen(vm: WalletViewModel, data: SavedData, back: () -> Unit, edit: () -> Unit) {
    var replay by remember { mutableIntStateOf(0) }
    var delete by remember { mutableStateOf(false) }
    Page("A signature\nmove.", "YOUR AUTOGRAPH", back) {
        Text("Capture the way you write, not just how it looks.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        val autograph = data.autograph
        if (autograph == null) {
            Block { Icon(Icons.Rounded.Gesture, null, Modifier.size(48.dp), tint = MaterialTheme.colorScheme.primary); Text("Made for your S Pen.\nFinger friendly, too.", style = MaterialTheme.typography.titleLarge); Text("Create a playful checkout autograph. It does not need to be your official signature.") }
            Primary("Create autograph", onClick = edit)
        } else {
            AnimatedInk(autograph, data.preferences, replay)
            OutlinedButton(onClick = { replay++ }, Modifier.fillMaxWidth()) { Icon(Icons.Rounded.Replay, null); Spacer(Modifier.width(8.dp)); Text("Replay handwriting") }
            Text("Playback speed", style = MaterialTheme.typography.titleMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(.75f, 1f, 1.5f).forEach { speed -> FilterChip(selected = data.preferences.speed == speed, onClick = { vm.preferences(data.preferences.copy(speed = speed)) }, label = { Text("${speed}×") }) }
            }
            Primary("Edit autograph", onClick = edit)
            TextButton(onClick = { delete = true }, Modifier.fillMaxWidth()) { Text("Delete autograph") }
        }
        Text("Saved on this device. Connected checkout shares it only when you choose. Sharing is always optional.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    if (delete) Confirm("Delete autograph?", "This removes the saved handwriting from this app.", { delete = false }) { vm.deleteAutograph(); delete = false }
}

@Composable private fun Editor(vm: WalletViewModel, preferences: Preferences, save: () -> Unit, back: () -> Unit) {
    val strokes by vm.draft.collectAsStateWithLifecycle()
    val redo by vm.redo.collectAsStateWithLifecycle()
    var color by remember { mutableLongStateOf(vm.draftColor) }
    var width by remember { mutableFloatStateOf(vm.draftWidth) }
    var preview by remember { mutableStateOf(false) }
    var discard by remember { mutableStateOf(false) }
    var clear by remember { mutableStateOf(false) }
    val a = Autograph(aspect = vm.draftAspect, strokes = strokes, color = color, width = width)
    BackHandler { discard = true }
    Page("Make your mark.", "AUTOGRAPH STUDIO", { discard = true }) {
        Text("Write with S Pen or your finger. Your strokes stay editable until you save.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        InkPanel(a, height = 250, editable = true, onStroke = vm::addStroke)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            IconButton(onClick = vm::undo, enabled = strokes.isNotEmpty()) { Icon(Icons.AutoMirrored.Rounded.Undo, "Undo last stroke") }
            IconButton(onClick = vm::redoStroke, enabled = redo.isNotEmpty()) { Icon(Icons.AutoMirrored.Rounded.Redo, "Redo stroke") }
            IconButton(onClick = { clear = true }, enabled = strokes.isNotEmpty()) { Icon(Icons.Rounded.DeleteOutline, "Clear drawing") }
            IconButton(onClick = { preview = true }, enabled = strokes.isNotEmpty()) { Icon(Icons.Rounded.PlayArrow, "Preview animation") }
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Text("Ink", Modifier.weight(1f))
            listOf(0xFF18365FL, 0xFF255BD5L, 0xFF7249A6L).forEachIndexed { i, value ->
                IconToggleButton(modifier = Modifier.semantics { contentDescription = listOf("Navy ink", "Blue ink", "Purple ink")[i] }, checked = color == value, onCheckedChange = { color = value; vm.draftColor = value }) {
                    Box(Modifier.size(32.dp).clip(CircleShape).background(Color(value)), contentAlignment = Alignment.Center) { if (color == value) Icon(Icons.Rounded.Check, listOf("Navy ink selected", "Blue ink selected", "Purple ink selected")[i], tint = Color.White, modifier = Modifier.size(20.dp)) else Text("", modifier = Modifier.size(20.dp)) }
                }
            }
        }
        Text("Pen thickness")
        Slider(value = width, onValueChange = { width = it; vm.draftWidth = it }, valueRange = .003f..0.012f, steps = 2)
        Primary("Save autograph", enabled = strokes.isNotEmpty(), onClick = save)
        Text("Tip: a short, playful autograph is perfect. No official signature needed.", style = MaterialTheme.typography.bodySmall)
    }
    if (preview) AlertDialog(onDismissRequest = { preview = false }, title = { Text("Your handwriting, replayed") }, text = { AnimatedInk(a, preferences) }, confirmButton = { TextButton(onClick = { preview = false }) { Text("Keep drawing") } })
    if (discard) Confirm("Leave the editor?", "Unsaved edits will not replace your saved autograph.", { discard = false }, back)
    if (clear) Confirm("Clear this drawing?", "Your saved autograph remains unchanged until you save.", { clear = false }) { vm.clearDraft(); clear = false }
}

@Composable private fun Order(tip: Int = 0, showTip: Boolean = true) {
    Block {
        Row(verticalAlignment = Alignment.CenterVertically) { Icon(Icons.Rounded.LocalCafe, null, tint = MaterialTheme.colorScheme.primary); Spacer(Modifier.width(10.dp)); Column { Text("Harbor Coffee", style = MaterialTheme.typography.titleLarge); Text("Fictional merchant • Demo checkout", style = MaterialTheme.typography.bodySmall) } }
        HorizontalDivider()
        Detail("Iced latte", money(550)); Detail("Croissant", money(400))
        HorizontalDivider()
        Detail("Subtotal", money(950)); Detail("Tax", money(83))
        if (showTip) Detail("Tip", money(tip))
        Detail("Total", money(1033 + tip), strong = true)
    }
}
@Composable private fun CheckoutScreen(vm: WalletViewModel, data: SavedData, back: () -> Unit, create: () -> Unit) {
    val checkout by vm.checkout.collectAsStateWithLifecycle()
    val c = checkout
    var custom by rememberSaveable { mutableStateOf("") }
    var customMode by rememberSaveable { mutableStateOf(false) }
    if (c == null) { LaunchedEffect(Unit) { back() }; return }
    val stage = c.stage
    if (stage == CheckoutStage.RECEIPT) {
        val t = data.transactions.find { it.id == c.id }
        Page("A little more you.", "CHECKOUT COMPLETE", back) {
            Icon(Icons.Rounded.CheckCircle, null, Modifier.size(52.dp), tint = MaterialTheme.colorScheme.primary)
            if (t != null) Receipt(t, data.designForReceiptHistory(t.autographId))
            Primary("Back to wallet", onClick = back)
        }
        return
    }
    Page(when(stage) {
        CheckoutStage.REVIEW -> "Coffee, then go."
        CheckoutStage.PROCESSING -> "Just a moment…"
        CheckoutStage.TIP -> "Leave a little extra?"
        CheckoutStage.AUTOGRAPH -> "Make it yours."
        CheckoutStage.PLAYBACK -> "Your signature moment."
        else -> "Checkout"
    }, "SIMULATED CHECKOUT", back) {
        when(stage) {
            CheckoutStage.REVIEW -> {
                Order(showTip = false)
                Text("Demo card •••• ${demoCards[c.card].digits}", style = MaterialTheme.typography.titleMedium)
                Primary("Simulate payment", onClick = vm::simulatePayment)
                Text("No charge. No NFC. This is an interactive concept.", style = MaterialTheme.typography.bodySmall)
            }
            CheckoutStage.PROCESSING -> {
                Block { CircularProgressIndicator(); Text("Simulating card acceptance…"); Text("No real payment is being processed.") }
            }
            CheckoutStage.TIP -> {
                Text("Tips are calculated on the $9.50 subtotal.")
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(0, 15, 20).forEach { percent ->
                        FilterChip(selected = !customMode && c.tip == percentageTip(percent), onClick = { customMode = false; vm.setTip(percentageTip(percent)) }, label = { Text(if (percent == 0) "No tip" else "$percent%") })
                    }
                }
                FilterChip(selected = customMode, onClick = { customMode = true; vm.setTip(customTip(custom) ?: 0) }, label = { Text("Custom amount") })
                if (customMode) OutlinedTextField(value = custom, onValueChange = { custom = it; vm.setTip(customTip(it) ?: 0) }, label = { Text("Tip in USD") }, supportingText = { Text("Enter $0–$100, up to 2 decimal places.") }, isError = customTip(custom) == null, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.fillMaxWidth())
                Order(c.tip)
                Primary("Continue", enabled = !customMode || customTip(custom) != null, onClick = vm::reviewAutograph)
            }
            CheckoutStage.AUTOGRAPH -> {
                Block { Text("Harbor Coffee", style = MaterialTheme.typography.titleLarge); Detail("Final total", money(1033 + c.tip), strong = true); Text("Payment is simulated. An autograph is optional personalization; it does not verify your identity or authorize payment.", style = MaterialTheme.typography.bodySmall) }
                if (data.autograph == null) {
                    Text("You haven't created an autograph yet. Make one now, or finish right away.")
                    Primary("Create autograph", onClick = create)
                } else {
                    InkPanel(data.autograph)
                    Primary("Share autograph for this purchase", onClick = vm::share)
                    OutlinedButton(onClick = vm::share, modifier = Modifier.fillMaxWidth()) { Icon(Icons.Rounded.Contactless, null); Spacer(Modifier.width(8.dp)); Text("Simulate second tap") }
                    Text("The second-tap button illustrates the gesture. No NFC is used.", style = MaterialTheme.typography.bodySmall)
                }
                TextButton(onClick = { vm.finish() }, modifier = Modifier.fillMaxWidth()) { Text("Finish without autograph") }
            }
            CheckoutStage.PLAYBACK -> {
                Block {
                    Text("HARBOR COFFEE / TERMINAL DEMO", style = MaterialTheme.typography.labelSmall, letterSpacing = 1.sp)
                    Text(money(1033 + c.tip), style = MaterialTheme.typography.displaySmall)
                    data.autograph?.let { autograph ->
                        AnimatedInk(autograph, data.preferences, onFinished = { vm.finish(shared = true) })
                    } ?: LaunchedEffect(Unit) { vm.finish() }
                    Text("A personal touch, shared by you.", style = MaterialTheme.typography.bodySmall)
                }
            }
            else -> Unit
        }
    }
}
private fun date(time: Long): String = SimpleDateFormat("MMM d, h:mm a", Locale.US).format(Date(time))
@Composable private fun Receipt(transaction: Transaction, autograph: Autograph?) {
    Block {
        Text(transaction.merchant, style = MaterialTheme.typography.titleLarge)
        transaction.items.forEach { Detail("${it.quantity} × ${it.name}", money(it.price * it.quantity)) }
        HorizontalDivider()
        Detail("Subtotal", money(transaction.subtotal)); Detail("Tax", money(transaction.tax)); Detail("Tip", money(transaction.tip))
        Detail("Total", money(transaction.subtotal + transaction.tax + transaction.tip), strong = true)
    }
    Block {
        Detail("Status", "Demo payment complete")
        Detail("Card", "•••• ${demoCards.getOrElse(transaction.card) { demoCards[0] }.digits}")
        Text(date(transaction.time), style = MaterialTheme.typography.bodySmall)
        if (transaction.autographId != null) {
            Text("Autograph shared", style = MaterialTheme.typography.titleMedium)
            if (autograph?.id == transaction.autographId) InkPanel(autograph, height = 110)
            else Text("Autograph no longer stored", style = MaterialTheme.typography.bodySmall)
        } else Text("Completed without an autograph", style = MaterialTheme.typography.bodySmall)
    }
}
@Composable private fun Settings(vm: WalletViewModel, data: SavedData, back: () -> Unit) {
    var confirmation by remember { mutableStateOf<String?>(null) }
    Page("Your preferences.", "SETTINGS", back) {
        Block {
            Text("Appearance", style = MaterialTheme.typography.titleLarge)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("dark", "light", "system").forEach { theme -> FilterChip(selected = data.preferences.theme == theme, onClick = { vm.preferences(data.preferences.copy(theme = theme)) }, label = { Text(theme.replaceFirstChar { it.uppercase() }) }) }
            }
            Row(verticalAlignment = Alignment.CenterVertically) { Column(Modifier.weight(1f)) { Text("Reduced motion"); Text("Show completed handwriting instantly", style = MaterialTheme.typography.bodySmall) }; Switch(checked = data.preferences.reducedMotion, onCheckedChange = { vm.preferences(data.preferences.copy(reducedMotion = it)) }) }
        }
        Block {
            val feedbackContext=androidx.compose.ui.platform.LocalContext.current
            var chime by remember{mutableStateOf(WalletTap.sound(feedbackContext))}
            var tapHaptics by remember{mutableStateOf(WalletTap.haptics(feedbackContext))}
            Text("Tap feedback",style=MaterialTheme.typography.titleLarge)
            Row(verticalAlignment=Alignment.CenterVertically){Text("Payment chime",Modifier.weight(1f));Switch(checked=chime,onCheckedChange={chime=it;WalletTap.sound(feedbackContext,it)})}
            Row(verticalAlignment=Alignment.CenterVertically){Text("Tap & dial haptics",Modifier.weight(1f));Switch(checked=tapHaptics,onCheckedChange={tapHaptics=it;WalletTap.haptics(feedbackContext,it)})}
            Text("The chime follows your phone's notification volume and Do Not Disturb settings.",style=MaterialTheme.typography.bodySmall)
        }
        Block {
            Text("Your data", style = MaterialTheme.typography.titleLarge)
            Text("Handwriting is stored in this app's private storage on this device and excluded from app backup. Connected checkout sends handwriting only after you choose to share it, to your laptop over local Wi-Fi HTTP. The relay removes its copy on completion, cancellation, or expiry. Local storage is not separately encrypted by the app.", style = MaterialTheme.typography.bodyMedium)
            OutlinedButton(onClick = { confirmation = "autograph" }, Modifier.fillMaxWidth(), enabled = data.autograph != null) { Text("Delete selected design") }
            OutlinedButton(onClick = { confirmation = "history" }, Modifier.fillMaxWidth(), enabled = data.transactions.isNotEmpty()) { Text("Clear demo history") }
            TextButton(onClick = { confirmation = "reset" }, Modifier.fillMaxWidth()) { Text("Reset all demo data") }
        }
        Block {
            Text("Tap Autograph / 0.16", style = MaterialTheme.typography.titleLarge)
            Text("An independent Samsung Wallet-inspired interaction concept. Not affiliated with Samsung or Google. All cards, purchases, and payment interactions are fictional. Payments are simulated. The custom NFC reader transfers compact autograph artwork; it does not connect to bank cards or commercial payment terminals. Optional QR pairing connects to your local merchant relay.")
            Text("Built around your handwriting ✦", color = MaterialTheme.colorScheme.primary)
        }
    }
    confirmation?.let { kind -> Confirm("Delete ${if (kind == "reset") "all demo data" else kind}?", "This cannot be undone.", { confirmation = null }) {
        when(kind) { "autograph" -> vm.deleteAutograph(); "history" -> vm.clearHistory(); else -> vm.reset() }; confirmation = null
    } }
}
@Composable private fun Confirm(title: String, message: String, cancel: () -> Unit, confirm: () -> Unit) {
    AlertDialog(onDismissRequest = cancel, title = { Text(title) }, text = { Text(message) }, confirmButton = { TextButton(onClick = confirm) { Text("Confirm") } }, dismissButton = { TextButton(onClick = cancel) { Text("Cancel") } })
}
