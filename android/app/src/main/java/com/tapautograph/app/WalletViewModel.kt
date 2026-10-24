package com.tapautograph.app

import android.app.Application
import android.util.AtomicFile
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.serialization.json.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File

class LocalRepository(application: Application) {
    private val file = AtomicFile(File(application.noBackupFilesDir, "wallet.json"))
    private val json = Json { ignoreUnknownKeys = true }
    fun read(): SavedData = runCatching {
        json.decodeFromString<SavedData>(file.openRead().bufferedReader().use { it.readText() }).let {
            it.normalizedStudio()
        }
    }.getOrDefault(SavedData())
    fun write(data: SavedData) {
        val stream = file.startWrite()
        try { stream.write(json.encodeToString(data).toByteArray()); file.finishWrite(stream);WalletTap.designRevision++ }
        catch (e: Exception) { file.failWrite(stream); throw e }
    }
}

class WalletViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = LocalRepository(application)
    private val _data = MutableStateFlow(SavedData())
    val data = _data.asStateFlow()
    private val _ready = MutableStateFlow(false)
    val ready = _ready.asStateFlow()
    private val _error = MutableStateFlow<String?>(null)
    val error = _error.asStateFlow()
    val checkout = MutableStateFlow<Checkout?>(null)
    var selectedCard = MutableStateFlow(0)
    // Kept in ViewModel so rotation does not erase an in-progress drawing.
    val draft = MutableStateFlow<List<InkStroke>>(emptyList())
    val redo = MutableStateFlow<List<InkStroke>>(emptyList())
    var draftAspect = 1.8f
    var draftColor = 0xFF18365FL
    var draftWidth = .006f
    var draftStarted = false
    var draftId = java.util.UUID.randomUUID().toString()
    val studioPhoto = MutableStateFlow<android.graphics.Bitmap?>(null)
    var editorReturn = "autograph"
    val pairOffer = MutableStateFlow<PairOffer?>(null)
    val liveCheckout = MutableStateFlow<RemoteCheckout?>(null)
    val liveBusy = MutableStateFlow(false)
    val liveError = MutableStateFlow<String?>(null)
    val liveConnected = MutableStateFlow(true)
    private val liveApi = LiveApi()
    private var liveToken: String? = null
    private var liveJob: Job? = null
    private var liveGeneration = 0
    private var sharedAutographId: String? = null
    init { viewModelScope.launch(Dispatchers.IO) { _data.value = repository.read(); _ready.value = true } }
    private fun update(transform: (SavedData) -> SavedData) {
        val next = transform(_data.value)
        _data.value = next
        // Jobs run sequentially on Main; persistence stays ordered while IO is off Main.
        viewModelScope.launch { persist(next) }
    }
    private val writeMutex = kotlinx.coroutines.sync.Mutex()
    private suspend fun persist(value: SavedData) {
        writeMutex.lock()
        try { kotlinx.coroutines.withContext(Dispatchers.IO) { repository.write(value) } }
        catch (_: Exception) { _error.value = "Could not save changes. Check available storage and try again." }
        finally { writeMutex.unlock() }
    }
    fun clearError() { _error.value = null }
    fun startEditor(returnTo: String, fresh: Boolean = false) {
        editorReturn = returnTo
        val saved = if(fresh)null else _data.value.autograph
        draftId = saved?.id ?: java.util.UUID.randomUUID().toString()
        draft.value = saved?.strokes ?: emptyList(); redo.value = emptyList()
        draftAspect = saved?.aspect ?: 1.8f
        draftColor = saved?.color ?: 0xFF18365FL
        draftWidth = saved?.width ?: .006f
        draftStarted = true
    }
    fun addStroke(stroke: InkStroke) { draft.value += stroke; redo.value = emptyList() }
    fun undo() { draft.value.lastOrNull()?.let { redo.value += it; draft.value = draft.value.dropLast(1) } }
    fun redoStroke() { redo.value.lastOrNull()?.let { draft.value += it; redo.value = redo.value.dropLast(1) } }
    fun clearDraft() { draft.value = emptyList(); redo.value = emptyList() }
    fun draftAutograph() = Autograph(id = draftId, aspect = draftAspect, strokes = draft.value, color = draftColor, width = draftWidth)
    fun saveAutograph():Boolean {
        val old=_data.value.gallery.find { it.ink.id==draftId }
        return saveDesign(draftAutograph(),old?.title ?: "Handwritten autograph","handwritten")
    }
    fun saveDesign(ink:Autograph,title:String,source:String):Boolean = try {
        val next=_data.value.saveStudioDesign(ink,title,source)
        AutographSharing.card.disarm();update { next };true
    } catch(e:IllegalArgumentException){_error.value=e.message ?: "Could not save this design.";false}
    fun selectDesign(id:String) { _data.value.gallery.find { it.ink.id==id }?.let { entry->AutographSharing.card.disarm();update { it.copy(autograph=entry.ink) } } }
    fun selectReceiptDesign(id:String) { if(_data.value.gallery.any { it.ink.id==id })update { it.copy(receiptDesignId=id,receiptEnabled=true) } }
    fun setReceiptEnabled(enabled:Boolean) = update { it.copy(receiptEnabled=enabled,receiptDesignId=it.receiptDesignId ?: it.autograph?.id) }
    fun renameDesign(id:String,title:String) {
        if(title.trim().length !in 1..40)return
        update { it.copy(gallery=it.gallery.map { entry->if(entry.ink.id==id)entry.copy(title=title.trim())else entry }) }
    }
    fun deleteDesign(id:String) { WalletTap.forgetFavorite(getApplication(),id);AutographSharing.card.disarm();update { it.removeStudioDesign(id) } }
    fun deleteAutograph() { _data.value.autograph?.id?.let(::deleteDesign) }
    fun clearStudioPhoto() { studioPhoto.value=null }

    fun preferences(value: Preferences) = update { it.copy(preferences = value) }
    fun clearHistory() = update { it.copy(transactions = emptyList()) }
    fun reset() { WalletTap.clearFavorites(getApplication());WalletTap.enable(getApplication(),false);WalletTap.selectCard(getApplication(),0);AutographSharing.card.disarm();studioPhoto.value=null;disconnectLive(); checkout.value = null; clearDraft(); selectedCard.value = 0; update { SavedData() } }
    fun beginCheckout() { checkout.value = Checkout(card = selectedCard.value) }
    fun simulatePayment() {
        val session = checkout.value ?: return
        if (session.stage != CheckoutStage.REVIEW) return
        checkout.value = session.copy(stage = CheckoutStage.PROCESSING)
        viewModelScope.launch {
            delay(if (_data.value.preferences.reducedMotion) 0 else 800)
            if (checkout.value?.id == session.id && checkout.value?.stage == CheckoutStage.PROCESSING)
                checkout.value = checkout.value?.copy(stage = CheckoutStage.TIP)
        }
    }
    fun setTip(cents: Int) { checkout.value = checkout.value?.copy(tip = cents.coerceIn(0, 10000)) }
    fun reviewAutograph() { checkout.value = checkout.value?.copy(stage = CheckoutStage.AUTOGRAPH) }
    fun share() {
        if (checkout.value?.stage != CheckoutStage.AUTOGRAPH) return
        val autograph = _data.value.autograph ?: return
        checkout.value = checkout.value?.copy(stage = CheckoutStage.PLAYBACK, sharedId = autograph.id)
    }
    fun finish(shared: Boolean = false) {
        val session = checkout.value ?: return
        if (session.stage == CheckoutStage.RECEIPT) return
        val transaction = Transaction(session.id, System.currentTimeMillis(), session.tip, session.card, if (shared) session.sharedId else null)
        update { it.copy(transactions = appendTransaction(it.transactions, transaction)) }
        checkout.value = session.copy(stage = CheckoutStage.RECEIPT, sharedId = transaction.autographId)
    }
    fun preparePairing(raw: String) {
        val parsed = parsePairOffer(raw)
        if (parsed == null) { liveError.value = "Use the full pairing link from the merchant page on your local Wi-Fi."; return }
        if (pairOffer.value?.id == parsed.id && liveCheckout.value != null) return
        disconnectLive()
        pairOffer.value = parsed
    }
    fun clearLiveError() { liveError.value = null }
    fun disconnectLive() {
        liveGeneration++; liveJob?.cancel(); liveJob = null
        pairOffer.value = null; liveCheckout.value = null; liveToken = null
        sharedAutographId = null; liveBusy.value = false; liveError.value = null; liveConnected.value = true
    }
    private fun acceptRemote(value: RemoteCheckout) {
        liveCheckout.value = value
        if (value.stage == "completed") {
            val t = Transaction(value.id, value.completedAt ?: System.currentTimeMillis(), value.tip, value.card,
                if (value.autographShared) sharedAutographId else null, value.subtotal, value.tax, value.merchant, value.items)
            update { it.copy(transactions = appendTransaction(it.transactions, t)) }
        }
    }
    fun connectLive() {
        if (liveBusy.value || liveCheckout.value != null) return
        val offer = pairOffer.value ?: return
        val generation = liveGeneration
        liveBusy.value = true; liveError.value = null
        liveJob = viewModelScope.launch {
            try {
                val result = liveApi.claim(liveApi.request(offer, offer.token, "/claim", buildJsonObject { put("claimId", offer.claimId) }))
                if (generation != liveGeneration) return@launch
                liveToken = result.deviceToken; acceptRemote(result.checkout); liveConnected.value = true
            } catch (e: Exception) {
                if (generation == liveGeneration) liveError.value = if (e is LiveApiException) e.message else "Could not connect. Check that the laptop server is running, both devices share Wi-Fi, and the selected IP address is correct."
                return@launch
            } finally { if (generation == liveGeneration) liveBusy.value = false }
            while (isActive && generation == liveGeneration) {
                delay(900)
                if (liveCheckout.value?.stage in listOf("completed", "cancelled", "expired")) break
                try {
                    val value = liveApi.decode(liveApi.request(offer, liveToken ?: break))
                    if (generation != liveGeneration) break
                    liveConnected.value = true
                    if ((liveCheckout.value?.revision ?: -1) < value.revision) acceptRemote(value)
                } catch (e: Exception) {
                    if (generation != liveGeneration) break
                    liveConnected.value = false
                    if (e is LiveApiException && e.code in listOf(403,404,410)) {
                        liveError.value = e.message
                        liveCheckout.value = liveCheckout.value?.copy(stage = "expired")
                        break
                    }
                }
            }
        }
    }
    fun liveAction(action: String) {
        if (liveBusy.value) return
        val offer = pairOffer.value ?: return
        val token = liveToken ?: return
        val generation = liveGeneration
        val autograph = _data.value.autograph
        if (action == "share" && autograph == null) return
        val value = liveCheckout.value ?: return
        if (action == "pay" && value.stage != "paired") return
        if (action in listOf("share","skip") && value.stage != "autograph") return
        val body = buildJsonObject {
            put("action",action); put("card",selectedCard.value)
            if (action == "share" && autograph != null) {
                put("autograph",liveApi.autograph(autograph)); put("speed",_data.value.preferences.speed)
                put("reducedMotion",_data.value.preferences.reducedMotion)
                sharedAutographId = autograph.id
            }
        }
        liveBusy.value = true; liveError.value = null
        viewModelScope.launch {
            try {
                val next = liveApi.decode(liveApi.request(offer,token,"/action",body))
                if (generation == liveGeneration && next.revision >= (liveCheckout.value?.revision ?: -1)) { acceptRemote(next); liveConnected.value = true }
            } catch (e: Exception) {
                if (generation == liveGeneration) liveError.value = if (e is LiveApiException) e.message else "The update could not be confirmed. The app will reconnect; retry if the checkout still needs your choice."
            } finally { if (generation == liveGeneration) liveBusy.value = false }
        }
    }

}
