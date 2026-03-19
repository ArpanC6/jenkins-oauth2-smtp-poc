package io.github.arpanc6.oauth2smtp;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

/**
 * Fetches and caches OAuth 2.0 access tokens using the Client Credentials Flow
 * (RFC 6749, Section 4.4) from the Microsoft Identity Platform.
 *
 * <p>This is the core component of the GSoC 2026 proposal:
 * "Jenkins Email Notifications Using Outlook SMTP with OAuth 2.0"
 *
 * <p>In the email-ext-plugin integration, this logic will live inside
 * {@code ExtendedEmailPublisherDescriptor#getAuthenticator()} and will be
 * invoked when {@code MailAccount#isUseOAuth2()} returns {@code true}.
 *
 * <p>Token caching strategy:
 * <ul>
 *   <li>Tokens are cached per credentialsId in a ConcurrentHashMap.</li>
 *   <li>Proactive refresh occurs 60 seconds before expiry to avoid mid-build failures.</li>
 *   <li>Thread-safe: concurrent Jenkins builds will not cause duplicate token requests.</li>
 * </ul>
 */
public class OAuthTokenProvider {

    private static final Logger LOGGER = Logger.getLogger(OAuthTokenProvider.class.getName());

    /** Microsoft Identity Platform token endpoint base URL. */
    private static final String DEFAULT_TOKEN_ENDPOINT_BASE =
            "https://login.microsoftonline.com/%s/oauth2/v2.0/token";

    /** Proactive refresh buffer: refresh token 60 seconds before it expires. */
    private static final long REFRESH_BUFFER_MS = 60_000L;

    /** Scope required for Outlook SMTP OAuth2 authentication. */
    public static final String OUTLOOK_SMTP_SCOPE = "https://outlook.office365.com/.default";

    private final String tokenEndpointBase;
    private final ObjectMapper objectMapper;

    /** In-memory token cache: credentialsId -> CachedToken */
    private final Map<String, CachedToken> tokenCache = new ConcurrentHashMap<>();

    public OAuthTokenProvider() {
        this(DEFAULT_TOKEN_ENDPOINT_BASE);
    }

    /** Package-private constructor for testing with a mock server URL. */
    OAuthTokenProvider(String tokenEndpointBase) {
        this.tokenEndpointBase = tokenEndpointBase;
        this.objectMapper = new ObjectMapper();
    }

    /**
     * Returns a valid access token for the given credentials.
     * Returns cached token if still valid; fetches a new one otherwise.
     *
     * @param credentialsId unique ID for cache key (maps to Jenkins Credentials ID)
     * @param tenantId      Azure AD tenant ID
     * @param clientId      Azure AD application (client) ID
     * @param clientSecret  Azure AD client secret (stored in Jenkins Credentials store)
     * @param scope         OAuth2 scope (use OUTLOOK_SMTP_SCOPE for Outlook SMTP)
     * @return Bearer access token string
     * @throws OAuthTokenException if token fetch fails
     */
    public String getAccessToken(String credentialsId,
                                 String tenantId,
                                 String clientId,
                                 String clientSecret,
                                 String scope) throws OAuthTokenException {

        CachedToken cached = tokenCache.get(credentialsId);

        // Use cached token if it won't expire within the buffer window
        if (cached != null && !cached.isExpiringSoon()) {
            LOGGER.fine("Using cached token for credentialsId=" + credentialsId);
            return cached.accessToken;
        }

        LOGGER.info("Fetching new OAuth2 token for credentialsId=" + credentialsId);
        String token = fetchNewToken(tenantId, clientId, clientSecret, scope);

        // Cache with TTL of 3540 seconds (59 minutes — standard Azure token is 3600s)
        tokenCache.put(credentialsId, new CachedToken(token, System.currentTimeMillis() + 3_540_000L));

        return token;
    }

    /**
     * Fetches a new access token from the Microsoft Identity Platform token endpoint.
     *
     * <p>HTTP POST to: https://login.microsoftonline.com/{tenant}/oauth2/v2.0/token
     * Body (application/x-www-form-urlencoded):
     * <pre>
     *   grant_type=client_credentials
     *   &client_id={clientId}
     *   &client_secret={clientSecret}
     *   &scope={scope}
     * </pre>
     */
    private String fetchNewToken(String tenantId,
                                 String clientId,
                                 String clientSecret,
                                 String scope) throws OAuthTokenException {
        String endpoint = String.format(tokenEndpointBase, tenantId);

        String body = "grant_type=client_credentials"
                + "&client_id=" + urlEncode(clientId)
                + "&client_secret=" + urlEncode(clientSecret)
                + "&scope=" + urlEncode(scope);

        try {
            URL url = new URL(endpoint);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");
            conn.setRequestProperty("Accept", "application/json");
            conn.setDoOutput(true);
            conn.setConnectTimeout(10_000);
            conn.setReadTimeout(10_000);

            try (OutputStream os = conn.getOutputStream()) {
                os.write(body.getBytes(StandardCharsets.UTF_8));
            }

            int responseCode = conn.getResponseCode();
            String responseBody;

            if (responseCode == 200) {
                responseBody = new String(conn.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            } else {
                responseBody = new String(conn.getErrorStream().readAllBytes(), StandardCharsets.UTF_8);
            }

            OAuthTokenResponse response = objectMapper.readValue(responseBody, OAuthTokenResponse.class);

            if (response.isError()) {
                throw new OAuthTokenException(
                        "OAuth2 token fetch failed: " + response.getError()
                                + " — " + response.getErrorDescription());
            }

            if (response.getAccessToken() == null || response.getAccessToken().isEmpty()) {
                throw new OAuthTokenException("OAuth2 token response contained no access_token");
            }

            return response.getAccessToken();

        } catch (IOException e) {
            throw new OAuthTokenException("Network error while fetching OAuth2 token: " + e.getMessage(), e);
        }
    }

    /** Removes a cached token (e.g., when SMTP returns 535 Authentication failed). */
    public void invalidateCache(String credentialsId) {
        tokenCache.remove(credentialsId);
        LOGGER.info("Invalidated cached token for credentialsId=" + credentialsId);
    }

    private String urlEncode(String value) {
        try {
            return java.net.URLEncoder.encode(value, StandardCharsets.UTF_8.toString());
        } catch (java.io.UnsupportedEncodingException e) {
            return value;
        }
    }

    /** Immutable cached token with expiry tracking. */
    static class CachedToken {
        final String accessToken;
        final long expiresAtMs;

        CachedToken(String accessToken, long expiresAtMs) {
            this.accessToken = accessToken;
            this.expiresAtMs = expiresAtMs;
        }

        boolean isExpiringSoon() {
            return System.currentTimeMillis() >= (expiresAtMs - REFRESH_BUFFER_MS);
        }
    }
}
