package dev.caden.mccitweaks.materialmarket

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import dev.caden.mccitweaks.McciTweaksMod
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration
import java.util.Locale
import java.util.UUID
import java.util.concurrent.CompletableFuture
import net.fabricmc.loader.api.FabricLoader

internal class MaterialMarketApiClient(
    private val transport: Transport = JdkTransport(),
    private val userAgent: String = createUserAgent()
) {
    fun fetchInventory(playerId: UUID, apiKey: String?): CompletableFuture<SyncResult> {
        val normalizedKey = apiKey?.trim().orEmpty()
        if (normalizedKey.isEmpty()) {
            return CompletableFuture.completedFuture(SyncResult.failure(Status.MISSING_KEY))
        }

        val requestBody = JsonObject().apply {
            addProperty("query", QUERY)
            add("variables", JsonObject().apply { addProperty("uuid", playerId.toString()) })
        }

        val headers = mapOf(
            "Content-Type" to "application/json",
            "Accept" to "application/json",
            "X-API-Key" to normalizedKey,
            "User-Agent" to userAgent
        )

        return try {
            transport.post(ENDPOINT, headers, requestBody.toString())
                .handle { response, failure ->
                    if (failure != null) SyncResult.failure(Status.CONNECTION_ERROR) else parseResponse(response)
                }
        } catch (_: Exception) {
            CompletableFuture.completedFuture(SyncResult.failure(Status.CONNECTION_ERROR))
        }
    }

    enum class Status {
        SUCCESS,
        MISSING_KEY,
        INVALID_KEY,
        RATE_LIMITED,
        PERMISSION_REQUIRED,
        PLAYER_NOT_FOUND,
        API_ERROR,
        CONNECTION_ERROR,
        MALFORMED_RESPONSE
    }

    data class AssetAmount(val displayName: String, val amount: Long)

    data class SyncResult(val status: Status, val inventory: Map<String, AssetAmount>) {
        fun successful(): Boolean = status == Status.SUCCESS

        companion object {
            fun success(inventory: Map<String, AssetAmount>) = SyncResult(Status.SUCCESS, LinkedHashMap(inventory))

            fun failure(status: Status) = SyncResult(status, emptyMap())
        }
    }

    data class Response(val statusCode: Int, val body: String)

    fun interface Transport {
        fun post(endpoint: URI, headers: Map<String, String>, body: String): CompletableFuture<Response>
    }

    private class JdkTransport : Transport {
        private val client: HttpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build()

        override fun post(endpoint: URI, headers: Map<String, String>, body: String): CompletableFuture<Response> {
            val builder = HttpRequest.newBuilder(endpoint)
                .timeout(Duration.ofSeconds(15))
                .POST(HttpRequest.BodyPublishers.ofString(body))
            headers.forEach { (name, value) -> builder.header(name, value) }
            return client.sendAsync(builder.build(), HttpResponse.BodyHandlers.ofString())
                .thenApply { response -> Response(response.statusCode(), response.body()) }
        }
    }

    companion object {
        val ENDPOINT: URI = URI.create("https://api.mccisland.net/graphql")
        val QUERY: String = """
            query MaterialMarketInventory(${'$'}uuid: UUID!) {
              player(uuid: ${'$'}uuid) {
                infinibag {
                  asset {
                    name
                  }
                  amount
                }
                infinivault {
                  asset {
                    name
                  }
                  amount
                }
              }
            }
            """.trimIndent() + "\n"

        private val NON_ALPHANUMERIC = Regex("[^a-z0-9]+")

        private fun parseResponse(response: Response): SyncResult {
            when {
                response.statusCode == 401 || response.statusCode == 403 -> return SyncResult.failure(Status.INVALID_KEY)
                response.statusCode == 429 -> return SyncResult.failure(Status.RATE_LIMITED)
                response.statusCode !in 200..299 -> return SyncResult.failure(Status.API_ERROR)
            }

            return try {
                val root = JsonParser.parseString(response.body).asJsonObject
                val errors = optionalArray(root, "errors")
                if (errors != null && !errors.isEmpty) {
                    return SyncResult.failure(classifyGraphQlErrors(errors))
                }

                val data = requiredObject(root, "data")
                val playerElement = data.get("player")
                if (playerElement == null || playerElement.isJsonNull) {
                    return SyncResult.failure(Status.PLAYER_NOT_FOUND)
                }

                val player = playerElement.asJsonObject
                val infinibag = optionalArray(player, "infinibag")
                val infinivault = optionalArray(player, "infinivault")
                if (infinibag == null || infinivault == null) {
                    return SyncResult.failure(Status.PERMISSION_REQUIRED)
                }

                val inventory = LinkedHashMap<String, AssetAmount>()
                mergeInventory(inventory, infinibag)
                mergeInventory(inventory, infinivault)
                SyncResult.success(inventory)
            } catch (_: Exception) {
                SyncResult.failure(Status.MALFORMED_RESPONSE)
            }
        }

        private fun mergeInventory(inventory: MutableMap<String, AssetAmount>, entries: JsonArray) {
            for (entryElement in entries) {
                val entry = entryElement.asJsonObject
                val asset = requiredObject(entry, "asset")
                val displayName = asset.get("name").asString.trim()
                val amount = entry.get("amount").asLong
                require(displayName.isNotEmpty()) { "Asset name is blank" }

                val key = normalize(displayName)
                val existing = inventory[key]
                val combined = Math.addExact(existing?.amount ?: 0L, maxOf(0L, amount))
                inventory[key] = AssetAmount(existing?.displayName ?: displayName, combined)
            }
        }

        private fun requiredObject(parent: JsonObject, name: String): JsonObject {
            val element = parent.get(name)
            require(element != null && !element.isJsonNull && element.isJsonObject) { "Missing object: $name" }
            return element.asJsonObject
        }

        private fun optionalArray(parent: JsonObject, name: String): JsonArray? {
            val element = parent.get(name)
            if (element == null || element.isJsonNull) return null
            require(element.isJsonArray) { "Expected array: $name" }
            return element.asJsonArray
        }

        private fun classifyGraphQlErrors(errors: JsonArray): Status {
            val combined = errors
                .filter { it.isJsonObject }
                .mapNotNull { it.asJsonObject.get("message") }
                .filter { it.isJsonPrimitive }
                .joinToString("") { " " + it.asString.lowercase(Locale.ROOT) }

            return when {
                combined.contains("rate") && combined.contains("limit") -> Status.RATE_LIMITED
                listOf("api key", "unauthor", "forbidden", "authentication").any(combined::contains) -> Status.INVALID_KEY
                listOf("infinibag", "infinivault", "permission", "disabled").any(combined::contains) ->
                    Status.PERMISSION_REQUIRED
                else -> Status.API_ERROR
            }
        }

        @JvmStatic
        fun normalize(value: String): String =
            NON_ALPHANUMERIC.replace(value.trim().lowercase(Locale.ROOT), " ").trim()

        private fun createUserAgent(): String {
            val version = FabricLoader.getInstance()
                .getModContainer(McciTweaksMod.MOD_ID)
                .map { it.metadata.version.friendlyString }
                .orElse("unknown")
            return "MCCI-Tweaks/$version (Minecraft client mod)"
        }
    }
}
