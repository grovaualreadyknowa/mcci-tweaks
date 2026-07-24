package dev.caden.mccitweaks.materialmarket;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.caden.mccitweaks.McciTweaksMod;
import net.fabricmc.loader.api.FabricLoader;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

final class MaterialMarketApiClient {
    static final URI ENDPOINT = URI.create("https://api.mccisland.net/graphql");
    static final String QUERY = """
        query MaterialMarketInventory($uuid: UUID!) {
          player(uuid: $uuid) {
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
        """;

    private final Transport transport;
    private final String userAgent;

    MaterialMarketApiClient() {
        this(new JdkTransport(), createUserAgent());
    }

    MaterialMarketApiClient(Transport transport, String userAgent) {
        this.transport = transport;
        this.userAgent = userAgent;
    }

    CompletableFuture<SyncResult> fetchInventory(UUID playerId, String apiKey) {
        String normalizedKey = apiKey == null ? "" : apiKey.trim();
        if (normalizedKey.isEmpty()) {
            return CompletableFuture.completedFuture(SyncResult.failure(Status.MISSING_KEY));
        }

        JsonObject variables = new JsonObject();
        variables.addProperty("uuid", playerId.toString());
        JsonObject requestBody = new JsonObject();
        requestBody.addProperty("query", QUERY);
        requestBody.add("variables", variables);

        Map<String, String> headers = Map.of(
            "Content-Type", "application/json",
            "Accept", "application/json",
            "X-API-Key", normalizedKey,
            "User-Agent", this.userAgent
        );

        try {
            return this.transport.post(ENDPOINT, headers, requestBody.toString())
                .handle((response, failure) -> {
                    if (failure != null) {
                        return SyncResult.failure(Status.CONNECTION_ERROR);
                    }
                    return parseResponse(response);
                });
        } catch (Exception ignored) {
            return CompletableFuture.completedFuture(SyncResult.failure(Status.CONNECTION_ERROR));
        }
    }

    private static SyncResult parseResponse(Response response) {
        if (response.statusCode == 401 || response.statusCode == 403) {
            return SyncResult.failure(Status.INVALID_KEY);
        }
        if (response.statusCode == 429) {
            return SyncResult.failure(Status.RATE_LIMITED);
        }
        if (response.statusCode < 200 || response.statusCode >= 300) {
            return SyncResult.failure(Status.API_ERROR);
        }

        try {
            JsonObject root = JsonParser.parseString(response.body).getAsJsonObject();
            JsonArray errors = optionalArray(root, "errors");
            if (errors != null && !errors.isEmpty()) {
                return SyncResult.failure(classifyGraphQlErrors(errors));
            }

            JsonObject data = requiredObject(root, "data");
            JsonElement playerElement = data.get("player");
            if (playerElement == null || playerElement.isJsonNull()) {
                return SyncResult.failure(Status.PLAYER_NOT_FOUND);
            }

            JsonObject player = playerElement.getAsJsonObject();
            JsonArray infinibag = permissionProtectedArray(player, "infinibag");
            JsonArray infinivault = permissionProtectedArray(player, "infinivault");
            if (infinibag == null || infinivault == null) {
                return SyncResult.failure(Status.PERMISSION_REQUIRED);
            }

            Map<String, AssetAmount> inventory = new LinkedHashMap<>();
            mergeInventory(inventory, infinibag);
            mergeInventory(inventory, infinivault);
            return SyncResult.success(inventory);
        } catch (Exception ignored) {
            return SyncResult.failure(Status.MALFORMED_RESPONSE);
        }
    }

    private static void mergeInventory(Map<String, AssetAmount> inventory, JsonArray entries) {
        for (JsonElement entryElement : entries) {
            JsonObject entry = entryElement.getAsJsonObject();
            JsonObject asset = requiredObject(entry, "asset");
            String displayName = asset.get("name").getAsString().trim();
            long amount = entry.get("amount").getAsLong();
            if (displayName.isEmpty()) {
                throw new IllegalArgumentException("Asset name is blank");
            }

            String key = normalize(displayName);
            AssetAmount existing = inventory.get(key);
            long combined = Math.addExact(existing == null ? 0L : existing.amount, Math.max(0L, amount));
            inventory.put(key, new AssetAmount(existing == null ? displayName : existing.displayName, combined));
        }
    }

    private static JsonObject requiredObject(JsonObject parent, String name) {
        JsonElement element = parent.get(name);
        if (element == null || element.isJsonNull() || !element.isJsonObject()) {
            throw new IllegalArgumentException("Missing object: " + name);
        }
        return element.getAsJsonObject();
    }

    private static JsonArray optionalArray(JsonObject parent, String name) {
        JsonElement element = parent.get(name);
        if (element == null || element.isJsonNull()) {
            return null;
        }
        if (!element.isJsonArray()) {
            throw new IllegalArgumentException("Expected array: " + name);
        }
        return element.getAsJsonArray();
    }

    private static JsonArray permissionProtectedArray(JsonObject parent, String name) {
        return optionalArray(parent, name);
    }

    private static Status classifyGraphQlErrors(JsonArray errors) {
        StringBuilder messages = new StringBuilder();
        for (JsonElement errorElement : errors) {
            if (!errorElement.isJsonObject()) {
                continue;
            }
            JsonElement message = errorElement.getAsJsonObject().get("message");
            if (message != null && message.isJsonPrimitive()) {
                messages.append(' ').append(message.getAsString().toLowerCase(Locale.ROOT));
            }
        }

        String combined = messages.toString();
        if (combined.contains("rate") && combined.contains("limit")) {
            return Status.RATE_LIMITED;
        }
        if (combined.contains("api key")
            || combined.contains("unauthor")
            || combined.contains("forbidden")
            || combined.contains("authentication")) {
            return Status.INVALID_KEY;
        }
        if (combined.contains("infinibag")
            || combined.contains("infinivault")
            || combined.contains("permission")
            || combined.contains("disabled")) {
            return Status.PERMISSION_REQUIRED;
        }
        return Status.API_ERROR;
    }

    static String normalize(String value) {
        return value.trim()
            .toLowerCase(Locale.ROOT)
            .replaceAll("[^a-z0-9]+", " ")
            .trim();
    }

    private static String createUserAgent() {
        String version = FabricLoader.getInstance()
            .getModContainer(McciTweaksMod.MOD_ID)
            .map(container -> container.getMetadata().getVersion().getFriendlyString())
            .orElse("unknown");
        return "MCCI-Tweaks/" + version + " (Minecraft client mod)";
    }

    enum Status {
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

    record AssetAmount(String displayName, long amount) {
    }

    record SyncResult(Status status, Map<String, AssetAmount> inventory) {
        static SyncResult success(Map<String, AssetAmount> inventory) {
            return new SyncResult(Status.SUCCESS, Map.copyOf(inventory));
        }

        static SyncResult failure(Status status) {
            return new SyncResult(status, Map.of());
        }

        boolean successful() {
            return this.status == Status.SUCCESS;
        }
    }

    record Response(int statusCode, String body) {
    }

    @FunctionalInterface
    interface Transport {
        CompletableFuture<Response> post(URI endpoint, Map<String, String> headers, String body);
    }

    private static final class JdkTransport implements Transport {
        private final HttpClient client = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();

        @Override
        public CompletableFuture<Response> post(URI endpoint, Map<String, String> headers, String body) {
            HttpRequest.Builder builder = HttpRequest.newBuilder(endpoint)
                .timeout(Duration.ofSeconds(15))
                .POST(HttpRequest.BodyPublishers.ofString(body));
            headers.forEach(builder::header);
            return this.client.sendAsync(builder.build(), HttpResponse.BodyHandlers.ofString())
                .thenApply(response -> new Response(response.statusCode(), response.body()));
        }
    }
}
