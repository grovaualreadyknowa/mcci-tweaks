package dev.caden.mccitweaks.materialmarket;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class MaterialMarketApiClientTest {
    private static final UUID PLAYER_ID = UUID.fromString("6a085b2c-19fb-4986-b453-231aa942bbec");

    @Test
    void constructsGraphQlRequestWithKeyOnlyInHeader() {
        AtomicReference<URI> endpoint = new AtomicReference<>();
        AtomicReference<Map<String, String>> headers = new AtomicReference<>();
        AtomicReference<String> body = new AtomicReference<>();
        MaterialMarketApiClient client = new MaterialMarketApiClient((uri, sentHeaders, sentBody) -> {
            endpoint.set(uri);
            headers.set(sentHeaders);
            body.set(sentBody);
            return CompletableFuture.completedFuture(successResponse("[]", "[]"));
        }, "MCCI-Tweaks/test");

        MaterialMarketApiClient.SyncResult result = client.fetchInventory(PLAYER_ID, "secret-api-key").join();

        assertTrue(result.successful());
        assertEquals(MaterialMarketApiClient.ENDPOINT, endpoint.get());
        assertEquals("secret-api-key", headers.get().get("X-API-Key"));
        assertEquals("MCCI-Tweaks/test", headers.get().get("User-Agent"));
        assertFalse(body.get().contains("secret-api-key"));
        JsonObject request = JsonParser.parseString(body.get()).getAsJsonObject();
        assertEquals(
            PLAYER_ID.toString(),
            request.getAsJsonObject("variables").get("uuid").getAsString()
        );
        assertTrue(request.get("query").getAsString().contains("infinibag"));
        assertTrue(request.get("query").getAsString().contains("infinivault"));
    }

    @Test
    void mergesNormalizedAssetsAcrossBagAndVaultAndPreservesZero() {
        String bag = """
            [
              {"asset":{"name":"Refined Wood"},"amount":3},
              {"asset":{"name":"Zero Dust"},"amount":0}
            ]
            """;
        String vault = """
            [
              {"asset":{"name":"Refined-Wood"},"amount":4}
            ]
            """;
        MaterialMarketApiClient client = respondingWith(successResponse(bag, vault));

        MaterialMarketApiClient.SyncResult result = client.fetchInventory(PLAYER_ID, "key").join();

        assertTrue(result.successful());
        assertEquals(2, result.inventory().size());
        assertEquals(7, result.inventory().get("refined wood").amount());
        assertEquals("Refined Wood", result.inventory().get("refined wood").displayName());
        assertEquals(0, result.inventory().get("zero dust").amount());
    }

    @Test
    void reportsMissingInfinibagPermissionWithoutInventory() {
        MaterialMarketApiClient client = respondingWith(new MaterialMarketApiClient.Response(
            200,
            """
                {"data":{"player":{"infinibag":null,"infinivault":null}}}
                """
        ));

        MaterialMarketApiClient.SyncResult result = client.fetchInventory(PLAYER_ID, "key").join();

        assertEquals(MaterialMarketApiClient.Status.PERMISSION_REQUIRED, result.status());
        assertTrue(result.inventory().isEmpty());
    }

    @Test
    void classifiesAuthenticationRateLimitAndServerFailures() {
        assertStatus(401, MaterialMarketApiClient.Status.INVALID_KEY);
        assertStatus(403, MaterialMarketApiClient.Status.INVALID_KEY);
        assertStatus(429, MaterialMarketApiClient.Status.RATE_LIMITED);
        assertStatus(500, MaterialMarketApiClient.Status.API_ERROR);
    }

    @Test
    void classifiesGraphQlPermissionError() {
        MaterialMarketApiClient client = respondingWith(new MaterialMarketApiClient.Response(
            200,
            """
                {"errors":[{"message":"The Infinibag API setting is disabled"}],"data":{"player":null}}
                """
        ));

        assertEquals(
            MaterialMarketApiClient.Status.PERMISSION_REQUIRED,
            client.fetchInventory(PLAYER_ID, "key").join().status()
        );
    }

    @Test
    void rejectsMalformedSuccessfulResponse() {
        MaterialMarketApiClient client = respondingWith(new MaterialMarketApiClient.Response(
            200,
            """
                {"data":{"player":{"infinibag":[{"asset":{},"amount":1}],"infinivault":[]}}}
                """
        ));

        assertEquals(
            MaterialMarketApiClient.Status.MALFORMED_RESPONSE,
            client.fetchInventory(PLAYER_ID, "key").join().status()
        );
    }

    @Test
    void convertsTransportFailureToConnectionStatus() {
        MaterialMarketApiClient client = new MaterialMarketApiClient(
            (endpoint, headers, body) -> CompletableFuture.failedFuture(new IllegalStateException("offline")),
            "MCCI-Tweaks/test"
        );

        MaterialMarketApiClient.SyncResult result = client.fetchInventory(PLAYER_ID, "key").join();

        assertEquals(MaterialMarketApiClient.Status.CONNECTION_ERROR, result.status());
        assertTrue(result.inventory().isEmpty());
    }

    @Test
    void blankKeyDoesNotCallTransport() {
        MaterialMarketApiClient client = new MaterialMarketApiClient((endpoint, headers, body) -> {
            throw new AssertionError("Transport should not be called");
        }, "MCCI-Tweaks/test");

        assertEquals(
            MaterialMarketApiClient.Status.MISSING_KEY,
            client.fetchInventory(PLAYER_ID, "  ").join().status()
        );
    }

    private static MaterialMarketApiClient respondingWith(MaterialMarketApiClient.Response response) {
        return new MaterialMarketApiClient(
            (endpoint, headers, body) -> CompletableFuture.completedFuture(response),
            "MCCI-Tweaks/test"
        );
    }

    private static MaterialMarketApiClient.Response successResponse(String bag, String vault) {
        return new MaterialMarketApiClient.Response(
            200,
            "{\"data\":{\"player\":{\"infinibag\":" + bag + ",\"infinivault\":" + vault + "}}}"
        );
    }

    private static void assertStatus(int httpStatus, MaterialMarketApiClient.Status expected) {
        MaterialMarketApiClient client = respondingWith(new MaterialMarketApiClient.Response(httpStatus, "{}"));
        assertEquals(expected, client.fetchInventory(PLAYER_ID, "key").join().status());
    }
}
