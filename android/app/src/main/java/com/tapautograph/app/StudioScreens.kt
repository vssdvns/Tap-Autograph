package com.tapautograph.app

import android.graphics.Bitmap
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.*
import java.io.File
import java.util.Locale

@Composable fun StudioGallery(vm:WalletViewModel,data:SavedData,back:()->Unit,navigate:(String)->Unit,draw:()->Unit) {
    var editing by remember { mutableStateOf<StudioDesign?>(null) };var name by rememberSaveable { mutableStateOf("") }
    var deleting by remember { mutableStateOf<StudioDesign?>(null) }
    Page("Your mark.\nEvery mood.","AUTOGRAPH GALLERY",back) {
        Text("Handwriting, name designs and little bits of personality. Choose what you share at checkout.",color=MaterialTheme.colorScheme.onSurfaceVariant)
        Block {
            Detail("Saved designs","${data.gallery.size} / $MAX_DESIGNS")
            Primary("Draw with S Pen or finger",enabled=data.gallery.size<MAX_DESIGNS,onClick=draw)
            OutlinedButton(onClick={navigate("studio-name")},Modifier.fillMaxWidth()) { Text("Create from a name") }
            OutlinedButton(onClick={vm.clearStudioPhoto();navigate("studio-photo")},Modifier.fillMaxWidth()) { Text("Import a paper signature") }
            OutlinedButton(onClick={navigate("studio-doodles")},Modifier.fillMaxWidth()) { Text("Pick a mood doodle") }
        }
        OutlinedButton(onClick={navigate("nfc-lab")},enabled=data.autograph!=null,modifier=Modifier.fillMaxWidth()) { Text("Share selected design with NFC") }
        OutlinedButton(onClick={navigate("receipt-art")},Modifier.fillMaxWidth()) { Text("Receipt preview & PNG export") }
        if(data.gallery.isEmpty())Text("Your first design starts here. Draw one, import one, or try a doodle.")
        data.gallery.forEach { entry ->
            key(entry.ink.id) { Block {
                Text(entry.title,style=MaterialTheme.typography.titleLarge)
                Text(when(entry.source){"name"->"Name design";"photo"->"Paper import · approximate playback";"doodle"->"Mood doodle";else->"Handwritten"},style=MaterialTheme.typography.bodySmall)
                InkPanel(entry.ink,height=115)
                Primary(if(data.autograph?.id==entry.ink.id) "Selected for screen & NFC" else "Use for screen & NFC",enabled=data.autograph?.id!=entry.ink.id) { vm.selectDesign(entry.ink.id) }
                OutlinedButton(onClick={vm.selectReceiptDesign(entry.ink.id)},Modifier.fillMaxWidth()) { Text(if(data.receiptEnabled&&data.receiptDesignId==entry.ink.id) "Selected for receipt preview" else "Use for receipt preview") }
                Row(horizontalArrangement=Arrangement.spacedBy(12.dp)) {
                    TextButton(onClick={editing=entry;name=entry.title}) { Text("Rename") }
                    TextButton(onClick={deleting=entry}) { Text("Delete") }
                }
            } }
        }
    }
    editing?.let { entry->AlertDialog(onDismissRequest={editing=null},title={Text("Name this design")},text={OutlinedTextField(value=name,onValueChange={name=it.take(40)},label={Text("Design name")},singleLine=true)},confirmButton={TextButton(enabled=name.isNotBlank(),onClick={vm.renameDesign(entry.ink.id,name);editing=null}){Text("Save")}},dismissButton={TextButton(onClick={editing=null}){Text("Cancel")}}) }
    deleting?.let { entry->AlertDialog(onDismissRequest={deleting=null},title={Text("Delete ${entry.title}?")},text={Text("This removes the saved design. Earlier receipts may no longer display it.")},confirmButton={TextButton(onClick={vm.deleteDesign(entry.ink.id);deleting=null}){Text("Delete")}},dismissButton={TextButton(onClick={deleting=null}){Text("Cancel")}}) }
}

@Composable private fun InkPalette(selected:Long,choose:(Long)->Unit) {
    val titles=listOf("Blue","Black","Violet","Green")
    Column {
        studioColors.indices.toList().chunked(2).forEach { row ->
            Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                row.forEach { i ->
                    FilterChip(selected=selected==studioColors[i],onClick={choose(studioColors[i])},label={Text(titles[i])})
                }
            }
        }
    }
}
@Composable private fun StudioSlider(label:String,value:Float,range:ClosedFloatingPointRange<Float>,change:(Float)->Unit) {
    Text(label,style=MaterialTheme.typography.labelLarge)
    Slider(value=value,onValueChange=change,valueRange=range)
}

@Composable fun NameStudio(vm:WalletViewModel,data:SavedData,back:()->Unit) {
    var name by rememberSaveable { mutableStateOf("") };var label by rememberSaveable { mutableStateOf("My name design") }
    var selected by rememberSaveable { mutableIntStateOf(0) };var slant by rememberSaveable { mutableFloatStateOf(0f) }
    var underline by rememberSaveable { mutableStateOf(true) };var color by rememberSaveable { mutableLongStateOf(studioColors[0]) }
    var designs by remember { mutableStateOf<List<Autograph?>>(emptyList()) };var error by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) };var replay by remember { mutableIntStateOf(0) }
    LaunchedEffect(name,slant,underline,color) {
        designs=emptyList();error=null;if(name.isBlank())return@LaunchedEffect
        delay(250);busy=true
        try {designs=withContext(Dispatchers.Default) { nameStyles.indices.map { runCatching { NameArt.create(name,it,slant,underline,color) }.getOrNull() } }
            if(designs.all { it==null })error="This name could not be drawn. Try a shorter name or another alphabet."
        } catch(e:CancellationException){throw e} finally{busy=false}
    }
    Page("A name.\nA few possibilities.","NAME STUDIO",back) {
        Text("Explore four styles made from on-device fonts. Save a favorite as your checkout design.")
        OutlinedTextField(value=name,onValueChange={name=it.take(32)},label={Text("Your name")},singleLine=true,modifier=Modifier.fillMaxWidth())
        if(busy)LinearProgressIndicator(Modifier.fillMaxWidth())
        error?.let { Text(it,color=MaterialTheme.colorScheme.error) }
        nameStyles.indices.toList().chunked(2).forEach { row->Row(horizontalArrangement=Arrangement.spacedBy(12.dp)) {
            row.forEach { i->Column(Modifier.weight(1f)) {
                FilterChip(selected=selected==i,onClick={selected=i;replay++},label={Text(nameStyles[i])})
                designs.getOrNull(i)?.let { InkPanel(it,height=100) }
            } }
        } }
        StudioSlider("Slant",slant,-.35f.. .35f){slant=it}
        Row { Checkbox(checked=underline,onCheckedChange={underline=it});Text("Add an underline",Modifier.padding(top=12.dp)) }
        InkPalette(color){color=it}
        designs.getOrNull(selected)?.let { ink ->
            AnimatedInk(ink,data.preferences,replay)
            OutlinedButton(onClick={replay++}) { Text("Replay selected design") }
            OutlinedTextField(value=label,onValueChange={label=it.take(40)},label={Text("Save as")},singleLine=true,modifier=Modifier.fillMaxWidth())
            Primary("Save & use this design",enabled=label.isNotBlank()&&!busy) { if(vm.saveDesign(ink,label,"name"))back() }
        }
        Text("Letter outlines and reveal timing are generated. These are style ideas, not predictions of your handwriting.",style=MaterialTheme.typography.bodySmall)
    }
}

@Composable fun DoodleStudio(vm:WalletViewModel,data:SavedData,back:()->Unit) {
    var selected by rememberSaveable { mutableIntStateOf(0) };var color by rememberSaveable { mutableLongStateOf(studioColors[0]) }
    var title by rememberSaveable { mutableStateOf(doodleTitles[0]) };var replay by remember { mutableIntStateOf(0) }
    val ink=remember(selected,color){DoodleArt.create(selected,color)}
    Page("Checkout,\nwith a little attitude.","MOOD DOODLES",back) {
        Text("Simple line art for the screen, NFC and a black-and-white receipt preview.")
        doodleTitles.indices.toList().chunked(2).forEach { row->Row(horizontalArrangement=Arrangement.spacedBy(12.dp)) { row.forEach { i->Column(Modifier.weight(1f)) {
            InkPanel(remember(i,color){DoodleArt.create(i,color)},height=105)
            FilterChip(selected=selected==i,onClick={selected=i;title=doodleTitles[i];replay++},label={Text(doodleTitles[i])})
        } } } }
        InkPalette(color){color=it}
        AnimatedInk(ink,data.preferences,replay)
        OutlinedButton(onClick={replay++}) { Text("Replay doodle") }
        OutlinedTextField(value=title,onValueChange={title=it.take(40)},label={Text("Save as")},singleLine=true,modifier=Modifier.fillMaxWidth())
        Primary("Save & use this doodle",enabled=title.isNotBlank()) { if(vm.saveDesign(ink,title,"doodle"))back() }
    }
}

@Composable fun PhotoStudio(vm:WalletViewModel,data:SavedData,back:()->Unit) {
    val context=LocalContext.current;val scope=rememberCoroutineScope();val photo by vm.studioPhoto.collectAsStateWithLifecycle()
    var cameraPath by rememberSaveable { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf<String?>(null) };var loading by remember { mutableStateOf(false) }
    var result by remember { mutableStateOf<PaperResult?>(null) };var processing by remember { mutableStateOf(false) }
    var label by rememberSaveable { mutableStateOf("Paper autograph") };var color by rememberSaveable { mutableLongStateOf(studioColors[0]) }
    var left by rememberSaveable { mutableFloatStateOf(0f) };var right by rememberSaveable { mutableFloatStateOf(0f) }
    var top by rememberSaveable { mutableFloatStateOf(0f) };var bottom by rememberSaveable { mutableFloatStateOf(0f) }
    var turns by rememberSaveable { mutableIntStateOf(0) };var angle by rememberSaveable { mutableFloatStateOf(0f) }
    var threshold by rememberSaveable { mutableFloatStateOf(145f) };var cleanup by rememberSaveable { mutableFloatStateOf(8f) }
    var width by rememberSaveable { mutableFloatStateOf(.008f) };var replay by remember { mutableIntStateOf(0) }
    fun load(uri:Uri,temporary:File?=null) { scope.launch {
        loading=true;error=null;result=null;vm.clearStudioPhoto()
        try {val image=withContext(Dispatchers.IO){PhotoArt.load(context,uri)};vm.studioPhoto.value=image;left=0f;right=0f;top=0f;bottom=0f;turns=0;angle=0f}
        catch(e:CancellationException){throw e}catch(_:Exception){error="Could not open that photo. Try a smaller JPEG or PNG."}
        finally {temporary?.delete();loading=false}
    } }
    val pick=rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri->if(uri!=null)load(uri) }
    val camera=rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { success->
        val file=cameraPath?.let { File(it) }
        if(success && file!=null)load(FileProvider.getUriForFile(context,"${context.packageName}.studio-files",file),file)else file?.delete()
        cameraPath=null
    }
    val controls=PhotoControls(left,top,right,bottom,turns,angle,threshold.toInt(),cleanup.toInt(),width)
    LaunchedEffect(photo,controls,color) {
        result=null;if(photo==null)return@LaunchedEffect
        delay(300);processing=true;error=null
        try {result=withContext(Dispatchers.Default){val job=currentCoroutineContext();PhotoArt.convert(photo!!,controls,color){job.ensureActive()} }}
        catch(e:CancellationException){throw e}catch(e:IllegalArgumentException){error=e.message ?: "Could not isolate the ink."}
        finally{processing=false}
    }
    Page("From paper\nto a little magic.","PAPER IMPORT",back) {
        Text("Use a close photo of dark ink on plain light paper. Crop away other text before saving.")
        Primary("Choose a photo",enabled=!loading) { pick.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }
        OutlinedButton(onClick={
            try {val folder=File(context.cacheDir,"studio-camera").apply { mkdirs() };val file=File.createTempFile("paper-",".jpg",folder);cameraPath=file.absolutePath;camera.launch(FileProvider.getUriForFile(context,"${context.packageName}.studio-files",file))}
            catch(_:Exception){cameraPath?.let { File(it).delete() };cameraPath=null;error="Could not open a camera. Take a photo with your Camera app, then choose it here."}
        },enabled=!loading,modifier=Modifier.fillMaxWidth()) { Text("Take a photo") }
        if(loading||processing)LinearProgressIndicator(Modifier.fillMaxWidth())
        photo?.let { original->
            Image((result?.photo ?: original).asImageBitmap(),"Selected paper photo",Modifier.fillMaxWidth().heightIn(max=280.dp),contentScale=ContentScale.Fit)
            OutlinedButton(onClick={turns=(turns+1)%4;left=0f;right=0f;top=0f;bottom=0f}) { Text("Rotate 90°") }
            StudioSlider("Straighten · ${angle.toInt()}°",angle,-15f..15f){angle=it}
            StudioSlider("Trim left · ${(left*100).toInt()}%",left,0f.. .45f){left=it}
            StudioSlider("Trim right · ${(right*100).toInt()}%",right,0f.. .45f){right=it}
            StudioSlider("Trim top · ${(top*100).toInt()}%",top,0f.. .45f){top=it}
            StudioSlider("Trim bottom · ${(bottom*100).toInt()}%",bottom,0f.. .45f){bottom=it}
            StudioSlider("Ink threshold · ${threshold.toInt()}",threshold,40f..230f){threshold=it}
            StudioSlider("Speckle cleanup · ${cleanup.toInt()}",cleanup,1f..40f){cleanup=it}
            Text("Lower cleanup if a dot disappears. Increase the threshold if faint ink is missing.",style=MaterialTheme.typography.bodySmall)
            StudioSlider("Line weight",width,.003f.. .025f){width=it}
            InkPalette(color){color=it}
        }
        error?.let { Text(it,color=MaterialTheme.colorScheme.error) }
        result?.let { processed ->
            Text("Extracted ink",style=MaterialTheme.typography.titleLarge)
            AnimatedInk(processed.ink,data.preferences,replay)
            OutlinedButton(onClick={replay++}) { Text("Replay extracted paths") }
            Text("A photo cannot preserve pen order or speed. This reveal follows extracted paths with generated timing.",style=MaterialTheme.typography.bodySmall)
            OutlinedTextField(value=label,onValueChange={label=it.take(40)},label={Text("Save as")},singleLine=true,modifier=Modifier.fillMaxWidth())
            Primary("Save & use paper autograph",enabled=!processing&&label.isNotBlank()) { if(vm.saveDesign(processed.ink,label,"photo")){vm.clearStudioPhoto();back()} }
        }
        Text("Processing happens on this device. The gallery stores extracted ink; selected source photos are not copied into the gallery.",style=MaterialTheme.typography.bodySmall)
    }
}

@Composable fun ReceiptStudio(vm:WalletViewModel,data:SavedData,back:()->Unit) {
    var dots by rememberSaveable { mutableIntStateOf(384) };var bold by rememberSaveable { mutableFloatStateOf(1.2f) }
    var bitmap by remember { mutableStateOf<Bitmap?>(null) };var message by remember { mutableStateOf<String?>(null) }
    val scope=rememberCoroutineScope();val context=LocalContext.current;val ink=data.receiptDesign()
    val export=rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("image/png")) { uri->
        val image=bitmap
        if(uri!=null&&image!=null)scope.launch {try {withContext(Dispatchers.IO){requireNotNull(context.contentResolver.openOutputStream(uri)).use { check(image.compress(Bitmap.CompressFormat.PNG,100,it)) }};message="Black-and-white artwork exported."}catch(_:Exception){message="Could not export the PNG."}}
    }
    LaunchedEffect(ink,dots,bold){bitmap=null;message=null;if(ink!=null){delay(100);bitmap=withContext(Dispatchers.Default){ReceiptArt.render(ink,dots,bold)}}}
    Page("A little personality.\nOn paper, too.","RECEIPT ART",back) {
        Row { Switch(checked=data.receiptEnabled,onCheckedChange=vm::setReceiptEnabled,enabled=data.gallery.isNotEmpty());Spacer(Modifier.width(12.dp));Text("Include design in receipt preview",Modifier.padding(top=10.dp)) }
        Text("Choose a receipt design independently from your screen/NFC design in the gallery.")
        Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){listOf(384,576).forEach { value->FilterChip(selected=dots==value,onClick={dots=value},label={Text("$value dots")}) }}
        StudioSlider("Print weight",bold,1f..2f){bold=it}
        if(ink==null)Text("Choose a design in the gallery and enable the receipt preview.")
        bitmap?.let { image->
            Text("HARBOR COFFEE · SAMPLE RECEIPT",style=MaterialTheme.typography.labelLarge)
            Detail("Demo total","$10.33")
            Image(image.asImageBitmap(),"Black-and-white receipt artwork",Modifier.fillMaxWidth().aspectRatio(image.width.toFloat()/image.height),contentScale=ContentScale.FillWidth)
            Text("${image.width} × ${image.height} pixels · black and white",style=MaterialTheme.typography.bodySmall)
        }
        Primary("Export artwork PNG",enabled=bitmap!=null) { export.launch("tap-autograph-${dots}.png") }
        message?.let { Text(it) }
        Text("PNG export contains the artwork only. Actual printing needs compatible printer/POS software; supported print widths vary.",style=MaterialTheme.typography.bodySmall)
    }
}
