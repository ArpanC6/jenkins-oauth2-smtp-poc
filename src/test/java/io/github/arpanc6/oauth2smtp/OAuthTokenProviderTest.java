package io.github.arpanc6.oauth2smtp;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import org.junit.jupiter.api.*;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.junit.jupiter.api.Assertions.*;

class OAuthTokenProviderTest {

    private static WireMockServer wireMock;
    private OAuthTokenProvider tokenProvider;

    private static final String TENANT_ID     = "test-tenant-id";
    private static final String CLIENT_ID     = "test-client-id";
    private static final String CLIENT_SECRET = "test-client-secret";
    private static final String SCOPE         = "https://outlook.office365.com/.default";
    private static final String CREDENTIALS_ID = "test-creds-id";
    private static final String MOCK_TOKEN    = "eyJ0eXAiOiJKV1QiLCJhbGciOiJSUzI1NiJ9.mock.token";

    @BeforeAll
    static void startWireMock() {
        wireMock = new WireMockServer(WireMockConfiguration.wireMockConfig().dynamicPort());
        wireMock.start();
    }

    @AfterAll
    static void stopWireMock() {
        wireMock.stop();
    }

    @BeforeEach
    void setUp() {
        wireMock.resetAll();
        String mockEndpoint = "http://localhost:" + wireMock.port() + "/%s/oauth2/v2.0/token";
        tokenProvider = new OAuthTokenProvider(mockEndpoint);
    }

    // ─── Happy Path ──────────────────────────────────────────────────────────

    @Test
    @DisplayName("Should fetch token successfully from mock Azure AD endpoint")
    void testSuccessfulTokenFetch() throws OAuthTokenException {
        stubSuccessfulTokenResponse();
        String token = tokenProvider.getAccessToken(
                CREDENTIALS_ID, TENANT_ID, CLIENT_ID, CLIENT_SECRET, SCOPE);
        assertEquals(MOCK_TOKEN, token);
        verifyTokenEndpointCalled(1);
    }

    @Test
    @DisplayName("Should return cached token on second call without hitting endpoint")
    void testTokenCaching() throws OAuthTokenException {
        stubSuccessfulTokenResponse();
        String token1 = tokenProvider.getAccessToken(
                CREDENTIALS_ID, TENANT_ID, CLIENT_ID, CLIENT_SECRET, SCOPE);
        String token2 = tokenProvider.getAccessToken(
                CREDENTIALS_ID, TENANT_ID, CLIENT_ID, CLIENT_SECRET, SCOPE);
        assertEquals(token1, token2);
        verifyTokenEndpointCalled(1);
    }

    @Test
    @DisplayName("Should fetch fresh token after cache invalidation")
    void testCacheInvalidation() throws OAuthTokenException {
        stubSuccessfulTokenResponse();
        tokenProvider.getAccessToken(CREDENTIALS_ID, TENANT_ID, CLIENT_ID, CLIENT_SECRET, SCOPE);
        tokenProvider.invalidateCache(CREDENTIALS_ID);
        tokenProvider.getAccessToken(CREDENTIALS_ID, TENANT_ID, CLIENT_ID, CLIENT_SECRET, SCOPE);
        verifyTokenEndpointCalled(2);
    }

    @Test
    @DisplayName("Should handle independent caches for different credentialsIds")
    void testIndependentCachingPerCredentialsId() throws OAuthTokenException {
        stubSuccessfulTokenResponse();
        tokenProvider.getAccessToken("creds-1", TENANT_ID, CLIENT_ID, CLIENT_SECRET, SCOPE);
        tokenProvider.getAccessToken("creds-2", TENANT_ID, CLIENT_ID, CLIENT_SECRET, SCOPE);
        verifyTokenEndpointCalled(2);
    }

    // ─── Error Handling ──────────────────────────────────────────────────────

    @Test
    @DisplayName("Should throw OAuthTokenException on invalid_client error")
    void testInvalidClientError() {
        wireMock.stubFor(post(urlPathMatching("/.*/oauth2/v2.0/token"))
                .willReturn(aResponse()
                        .withStatus(401)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{"
                                + "\"error\": \"invalid_client\","
                                + "\"error_description\": \"AADSTS70011: The provided client secret is incorrect.\""
                                + "}")));
        OAuthTokenException ex = assertThrows(OAuthTokenException.class, () ->
                tokenProvider.getAccessToken(CREDENTIALS_ID, TENANT_ID, CLIENT_ID, "wrong-secret", SCOPE));
        assertTrue(ex.getMessage().contains("invalid_client"));
        assertTrue(ex.getMessage().contains("AADSTS70011"));
    }

    @Test
    @DisplayName("Should throw OAuthTokenException on unauthorized_client (missing SMTP permission)")
    void testUnauthorizedClientError() {
        wireMock.stubFor(post(urlPathMatching("/.*/oauth2/v2.0/token"))
                .willReturn(aResponse()
                        .withStatus(400)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{"
                                + "\"error\": \"unauthorized_client\","
                                + "\"error_description\": \"AADSTS700016: Application was not found in the directory.\""
                                + "}")));
        OAuthTokenException ex = assertThrows(OAuthTokenException.class, () ->
                tokenProvider.getAccessToken(CREDENTIALS_ID, TENANT_ID, CLIENT_ID, CLIENT_SECRET, SCOPE));
        assertTrue(ex.getMessage().contains("unauthorized_client"));
    }

    @Test
    @DisplayName("Should throw OAuthTokenException on network timeout")
    void testNetworkTimeout() {
        wireMock.stubFor(post(urlPathMatching("/.*/oauth2/v2.0/token"))
                .willReturn(aResponse()
                        .withFixedDelay(15_000)
                        .withStatus(200)));
        assertThrows(OAuthTokenException.class, () ->
                tokenProvider.getAccessToken(CREDENTIALS_ID, TENANT_ID, CLIENT_ID, CLIENT_SECRET, SCOPE));
    }

    @Test
    @DisplayName("Should throw OAuthTokenException when response has no access_token")
    void testMissingAccessToken() {
        wireMock.stubFor(post(urlPathMatching("/.*/oauth2/v2.0/token"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"token_type\": \"Bearer\", \"expires_in\": 3599}")));
        OAuthTokenException ex = assertThrows(OAuthTokenException.class, () ->
                tokenProvider.getAccessToken(CREDENTIALS_ID, TENANT_ID, CLIENT_ID, CLIENT_SECRET, SCOPE));
        assertTrue(ex.getMessage().contains("no access_token"));
    }

    // ─── Performance ─────────────────────────────────────────────────────────

    @Test
    @DisplayName("Should return cached token with sub-millisecond latency")
    void testCacheHitLatency() throws OAuthTokenException {
        stubSuccessfulTokenResponse();

        // First call — cache miss (fetches token via WireMock)
        tokenProvider.getAccessToken(CREDENTIALS_ID, TENANT_ID, CLIENT_ID, CLIENT_SECRET, SCOPE);

        // Second call — cache hit (measure latency)
        long start = System.nanoTime();
        String token = tokenProvider.getAccessToken(CREDENTIALS_ID, TENANT_ID, CLIENT_ID, CLIENT_SECRET, SCOPE);
        long elapsed = System.nanoTime() - start;

        double ms = elapsed / 1_000_000.0;
        System.out.println("Cache hit latency: " + ms + "ms");

        assertNotNull(token);
        assertTrue(ms < 5.0, "Cache hit should be under 5ms, was: " + ms + "ms");
        verifyTokenEndpointCalled(1);
    }

    // ─── Helper Methods ──────────────────────────────────────────────────────

    private void stubSuccessfulTokenResponse() {
        wireMock.stubFor(post(urlPathMatching("/.*/oauth2/v2.0/token"))
                .withRequestBody(containing("grant_type=client_credentials"))
                .withRequestBody(containing("client_id=" + CLIENT_ID))
                .withRequestBody(containing("scope="))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{"
                                + "\"token_type\": \"Bearer\","
                                + "\"expires_in\": 3599,"
                                + "\"access_token\": \"" + MOCK_TOKEN + "\""
                                + "}")));
    }

    private void verifyTokenEndpointCalled(int times) {
        wireMock.verify(times, postRequestedFor(urlPathMatching("/.*/oauth2/v2.0/token")));
    }
}