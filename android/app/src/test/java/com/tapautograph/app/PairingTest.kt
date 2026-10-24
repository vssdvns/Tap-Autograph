package com.tapautograph.app

import org.junit.Assert.*
import org.junit.Test
import kotlinx.serialization.json.Json

class PairingTest {
    private val id="01234567-0123-0123-0123-0123456789ab"
    private val token="a".repeat(64)
    @Test fun acceptsLocalQrAndAppLinks() {
        val qr=parsePairOffer("http://192.168.1.42:8787/pair#id=$id&token=$token")!!
        val app=parsePairOffer("tapautograph://pair?base=http%3A%2F%2F192.168.1.42%3A8787&id=$id&token=$token")!!
        assertEquals(qr.base,app.base); assertEquals(qr.id,app.id); assertEquals(qr.token,app.token)
    }
    @Test fun rejectsPublicHostsCredentialsAndInvalidCodes() {
        listOf("http://8.8.8.8:8787/pair", "http://example.com:8787/pair", "http://192.168.1.42:8787/other", "http://user:secret@192.168.1.42:8787/pair", "https://192.168.1.42:8787/pair").forEach {
            assertNull(parsePairOffer("$it#id=$id&token=$token"))
        }
        assertNull(parsePairOffer("http://192.168.1.42:8787/pair#id=$id&token=bad"))
    }
    @Test fun decodesMerchantProtocolWithNullableCompletion() {
        val value=Json.decodeFromString<RemoteCheckout>("""{"id":"$id","merchant":"Harbor Coffee","stage":"autograph","revision":4,"expiresAt":1000,"items":[{"id":"latte","name":"Iced latte","quantity":2,"price":550}],"subtotal":1100,"tax":96,"tip":165,"total":1361,"card":1,"autographShared":false,"completedAt":null}""")
        assertEquals(1361,value.total); assertEquals(2,value.items.first().quantity)
    }
    @Test fun oldTransactionDataStillLoads() {
        val t=Json.decodeFromString<Transaction>("""{"id":"old","time":100,"tip":0,"card":0}""")
        assertEquals(950,t.subtotal); assertEquals(2,t.items.size)
    }
}
