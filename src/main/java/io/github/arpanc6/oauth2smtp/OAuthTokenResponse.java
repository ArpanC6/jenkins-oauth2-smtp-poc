package io.github.arpanc6.oauth2smtp;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Represents the OAuth 2.0 token response from Microsoft Identity Platform.
 *
 * Example response from https://login.microsoftonline.com/{tenant}/oauth2/v2.0/token:
 * {
 *   "token_type": "Bearer",
 *   "expires_in": 3599,
 *   "access_token": "eyJ0eXAiOiJKV1Qi..."
 * }
 */
public class OAuthTokenResponse {

    @JsonProperty("access_token")
    private String accessToken;

    @JsonProperty("token_type")
    private String tokenType;

    @JsonProperty("expires_in")
    private long expiresIn;

    @JsonProperty("error")
    private String error;

    @JsonProperty("error_description")
    private String errorDescription;

    public String getAccessToken() { return accessToken; }
    public String getTokenType() { return tokenType; }
    public long getExpiresIn() { return expiresIn; }
    public String getError() { return error; }
    public String getErrorDescription() { return errorDescription; }

    public boolean isError() {
        return error != null && !error.isEmpty();
    }
}
