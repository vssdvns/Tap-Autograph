package com.tapautograph.app

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URI
import java.net.Proxy
import java.util.UUID

@Serializable data class ReceiptItem(val id: String = "", val name: String, val quantity: Int = 1, val price: Int)
@Serializable data class RemoteCheckout(
    val id: String, val merchant: String, val stage: String, val revision: Int,
    val expiresAt: Long, val items: List<ReceiptItem>, val subtotal: Int, val tax: Int,
    val tip: Int, val total: Int, val card: Int = 0, val autographShared: Boolean = false,
    val completedAt: Long? = null
)
@Serializable data class ClaimResponse(val deviceToken: String, val checkout: RemoteCheckout)
data class PairOffer(val base: String, val id: String, val token: String, val claimId: String = UUID.randomUUID().toString())
/** Only local IPv4 HTTP endpoints are accepted in this LAN-only prototype. */
fun parsePairOffer(raw: String): PairOffer? = runCatching {
    val uri = URI(raw.trim())
    fun params(rawParams: String?): Map<String,String> = (rawParams ?: "").split('&').filter { it.contains('=') }.associate {
        val parts = it.split('=',limit=2)
        java.net.URLDecoder.decode(parts[0],"UTF-8") to java.net.URLDecoder.decode(parts[1],"UTF-8")
    }
    val p: Map<String,String>; val base: String
    if (uri.scheme in listOf("tapautograph", "tapautographlab") && uri.host == "pair") { p=params(uri.rawQuery); base=p["base"] ?: return null }
    else if (uri.scheme == "http" && uri.path == "/pair") { p=params(uri.rawFragment); base="http://${uri.rawAuthority}" }
    else return null
    val endpoint = URI(base)
    val host = endpoint.host ?: return null
    val octets = host.split('.').map { it.toIntOrNull() ?: return null }
    if (octets.size != 4 || octets.any { it !in 0..255 }) return null
    val privateIp = octets[0] == 10 || octets[0] == 127 || (octets[0] == 192 && octets[1] == 168) || (octets[0] == 172 && octets[1] in 16..31)
    if (!privateIp || endpoint.scheme != "http" || endpoint.userInfo != null || endpoint.query != null || endpoint.fragment != null || endpoint.path !in listOf("", "/") || endpoint.port !in 1..65535) return null
    val id=p["id"] ?: return null; val token=p["token"] ?: return null
    if (!Regex("[a-f0-9-]{36}").matches(id) || !Regex("[a-f0-9]{64}").matches(token)) return null
    PairOffer("http://$host:${endpoint.port}",id,token)
}.getOrNull()
class LiveApiException(val code: Int, message: String): Exception(message)
class LiveApi {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    suspend fun request(offer: PairOffer, token: String, suffix: String = "", body: JsonObject? = null): JsonObject = withContext(Dispatchers.IO) {
        val url = java.net.URL("${offer.base}/api/phone/${offer.id}$suffix")
        val connection = url.openConnection(Proxy.NO_PROXY) as HttpURLConnection
        try {
            connection.connectTimeout = 3500; connection.readTimeout = 4500
            connection.instanceFollowRedirects = false
            connection.setRequestProperty("Authorization", "Bearer $token")
            connection.setRequestProperty("Accept", "application/json")
            if (body != null) {
                connection.requestMethod="POST"; connection.doOutput=true
                connection.setRequestProperty("Content-Type","application/json")
                val bytes=body.toString().toByteArray()
                connection.setFixedLengthStreamingMode(bytes.size)
                connection.outputStream.use { it.write(bytes) }
            }
            val status=connection.responseCode
            val text=(if (status in 200..299) connection.inputStream else connection.errorStream)?.bufferedReader()?.use { it.readText() } ?: "{}"
            val result=json.parseToJsonElement(text).jsonObject
            if (status !in 200..299) throw LiveApiException(status,result["error"]?.jsonPrimitive?.content ?: "Could not update checkout.")
            result
        } finally { connection.disconnect() }
    }
    fun decode(value: JsonObject): RemoteCheckout = json.decodeFromJsonElement(value)
    fun autograph(value: Autograph): JsonElement = json.encodeToJsonElement(value)
    fun claim(value: JsonObject): ClaimResponse = json.decodeFromJsonElement(value)
}
