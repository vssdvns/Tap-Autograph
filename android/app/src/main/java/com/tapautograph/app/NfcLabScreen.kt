package com.tapautograph.app

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.util.Locale

private data class CompactResult(val packet: ByteArray, val decoded: Autograph, val jsonBytes: Int, val points: Int)

@Composable fun NfcLabScreen(data: SavedData, back: () -> Unit, create: () -> Unit) {
    var result by remember { mutableStateOf<CompactResult?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var loading by remember { mutableStateOf(false) }
    var decodedSelected by remember { mutableStateOf(true) }
    var replay by remember { mutableIntStateOf(0) }
    var exported by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val export = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) { uri ->
        val bytes = result?.packet
        if (uri != null && bytes != null) scope.launch {
            try {
                withContext(Dispatchers.IO) { requireNotNull(context.contentResolver.openOutputStream(uri)).use { it.write(bytes) } }
                exported = "Compact copy exported. It contains unencrypted handwriting; share it only intentionally."
            } catch (_: Exception) { error = "Could not export the compact copy." }
        }
    }
    LaunchedEffect(data.autograph) {
        result = null; error = null; exported = null
        val source = data.autograph ?: return@LaunchedEffect
        loading = true
        try {
            result = withContext(Dispatchers.Default) {
                val packet = CompactInkCodec.encode(source)
                CompactResult(packet, CompactInkCodec.decode(packet), Json { encodeDefaults = true }.encodeToString(source).toByteArray().size, source.strokes.sumOf { it.points.size })
            }
        } catch (e: IllegalArgumentException) { error = e.message ?: "Could not encode this autograph." }
        finally { loading = false }
    }
    Page("Small bytes.\nSame handwriting.", "NFC PAYLOAD LAB", back) {
        Text("Prepare and preview a compact copy of your saved autograph. Your original remains unchanged.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (data.autograph == null) Primary("Create an autograph first", onClick = create)
        if (loading) CircularProgressIndicator()
        error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        result?.let { encoded ->
            Block {
                Detail("Original JSON", "${encoded.jsonBytes} bytes")
                Detail("Compact TAG1", "${encoded.packet.size} bytes", strong = true)
                val reduction = (1.0 - encoded.packet.size.toDouble() / encoded.jsonBytes) * 100
                Detail("Size reduction", String.format(Locale.US, "%.1f%%", reduction))
                Detail("Captured points", encoded.points.toString())
                Detail("Strokes", encoded.decoded.strokes.size.toString())
                Detail("192-byte payload blocks", ((encoded.packet.size + 191) / 192).toString())
                Text("Block count is a planning estimate, not an implemented NFC protocol or measured transfer speed.", style = MaterialTheme.typography.bodySmall)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(selected = !decodedSelected, onClick = { decodedSelected = false; replay++ }, label = { Text("Original") })
                FilterChip(selected = decodedSelected, onClick = { decodedSelected = true; replay++ }, label = { Text("Decoded copy") })
            }
            val preview = if (decodedSelected) encoded.decoded else data.autograph!!
            AnimatedInk(preview, data.preferences, replay)
            OutlinedButton(onClick = { replay++ }, Modifier.fillMaxWidth()) { Text("Replay selected version") }
            Text("All captured points and relative millisecond timing are retained. Coordinates and pressure are quantized slightly; no strokes are removed.", style = MaterialTheme.typography.bodySmall)
            Primary("Export compact copy (.tai)") { export.launch("tap-autograph.tai") }
            exported?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
            HcePanel(encoded.packet)
            Block {
                Text(if (encoded.packet.size <= 1024) "Within our 1 KiB planning target" else "Will need a larger or multi-part exchange", style = MaterialTheme.typography.titleMedium)
                Text("Encoding, a simulated reader and an experimental NFC service are available. Real reader compatibility and speed still require hardware testing. CRC detects accidental corruption, not tampering.")
            }
        }
    }
}
