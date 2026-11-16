package dev.caden.mccitweaks.materialmarket

import com.google.gson.JsonParser
import java.net.URI
import java.util.UUID
import java.util.concurrent.CompletableFuture
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class MaterialMarketApiClientTest {
    @Test
    fun `constructs graphql request with key only in header`() {
        var endpoint: URI? = null
        var headers: Map<String, String> = emptyMap()
        var body = ""
        val client = MaterialMarketApiClient({ uri, sentHeaders, sentBody ->
            endpoint = uri
            headers = sentHeaders
            body = sentBody
            CompletableFuture.completedFuture(successResponse("[]", "[]"))
        }, "MCCI-Tweaks/test")

        val result = client.fetchInventory(PLAYER_ID, "secret-api-key").join()

        assertTrue(result.successful())
        assertEquals(MaterialMarketApiClient.ENDPOINT, endpoint)
        assertEquals("secret-api-key", headers["X-API-Key"])
        assertEquals("MCCI-Tweaks/test", headers["User-Agent"])
        assertFalse(body.contains("secret-api-key"))
        val request = JsonParser.parseString(body).asJsonObject
        assertEquals(PLAYER_ID.toString(), request.getAsJsonObject("variables").get("uuid").asString)
        assertTrue(request.get("query").asString.contains("infinibag"))
        assertTrue(request.get("query").asString.contains("infinivault"))
    }

    @Test
    fun `merges normalized assets across bag and vault and preserves zero`() {
        val bag = """
            [
              {"asset":{"name":"Refined Wood"},"amount":3},
              {"asset":{"name":"Zero Dust"},"amount":0}
            ]
        """
        val vault = """[{"asset":{"name":"Refined-Wood"},"amount":4}]"""

        val result = respondingWith(successResponse(bag, vault)).fetchInventory(PLAYER_ID, "key").join()

        assertTrue(result.successful())
        assertEquals(2, result.inventory.size)
        assertEquals(7L, result.inventory["refined wood"]!!.amount)
        assertEquals("Refined Wood", result.inventory["refined wood"]!!.displayName)
        assertEquals(0L, result.inventory["zero dust"]!!.amount)
    }

    @Test
    fun `reports missing infinibag permission without inventory`() {
        val client = respondingWith(
            MaterialMarketApiClient.Response(200, """{"data":{"player":{"infinibag":null,"infinivault":null}}}""")
        )

        val result = client.fetchInventory(PLAYER_ID, "key").join()

        assertEquals(MaterialMarketApiClient.Status.PERMISSION_REQUIRED, result.status)
        assertTrue(result.inventory.isEmpty())
    }

    @Test
    fun `classifies authentication rate limit and server failures`() {
        assertStatus(401, MaterialMarketApiClient.Status.INVALID_KEY)
        assertStatus(403, MaterialMarketApiClient.Status.INVALID_KEY)
        assertStatus(429, MaterialMarketApiClient.Status.RATE_LIMITED)
        assertStatus(500, MaterialMarketApiClient.Status.API_ERROR)
    }

    @Test
    fun `classifies graphql permission error`() {
        val client = respondingWith(
            MaterialMarketApiClient.Response(
                200,
                """{"errors":[{"message":"The Infinibag API setting is disabled"}],"data":{"player":null}}"""
            )
        )

        assertEquals(MaterialMarketApiClient.Status.PERMISSION_REQUIRED, client.fetchInventory(PLAYER_ID, "key").join().status)
    }

    @Test
    fun `rejects malformed successful response`() {
        val client = respondingWith(
            MaterialMarketApiClient.Response(
                200,
                """{"data":{"player":{"infinibag":[{"asset":{},"amount":1}],"infinivault":[]}}}"""
            )
        )

        assertEquals(MaterialMarketApiClient.Status.MALFORMED_RESPONSE, client.fetchInventory(PLAYER_ID, "key").join().status)
    }

    @Test
    fun `converts transport failure to connection status`() {
        val client = MaterialMarketApiClient(
            { _, _, _ -> CompletableFuture.failedFuture(IllegalStateException("offline")) },
            "MCCI-Tweaks/test"
        )

        val result = client.fetchInventory(PLAYER_ID, "key").join()

        assertEquals(MaterialMarketApiClient.Status.CONNECTION_ERROR, result.status)
        assertTrue(result.inventory.isEmpty())
    }

    @Test
    fun `blank key does not call transport`() {
        val client = MaterialMarketApiClient({ _, _, _ -> throw AssertionError("Transport should not be called") }, "MCCI-Tweaks/test")

        assertEquals(MaterialMarketApiClient.Status.MISSING_KEY, client.fetchInventory(PLAYER_ID, "  ").join().status)
    }

    private fun respondingWith(response: MaterialMarketApiClient.Response) =
        MaterialMarketApiClient({ _, _, _ -> CompletableFuture.completedFuture(response) }, "MCCI-Tweaks/test")

    private fun successResponse(bag: String, vault: String) = MaterialMarketApiClient.Response(
        200,
        """{"data":{"player":{"infinibag":$bag,"infinivault":$vault}}}"""
    )

    private fun assertStatus(httpStatus: Int, expected: MaterialMarketApiClient.Status) {
        val client = respondingWith(MaterialMarketApiClient.Response(httpStatus, "{}"))
        assertEquals(expected, client.fetchInventory(PLAYER_ID, "key").join().status)
    }

    private companion object {
        val PLAYER_ID: UUID = UUID.fromString("6a085b2c-19fb-4986-b453-231aa942bbec")
    }
}
