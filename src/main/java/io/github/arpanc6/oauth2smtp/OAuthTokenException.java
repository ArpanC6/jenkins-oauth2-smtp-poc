package io.github.arpanc6.oauth2smtp;

/**
 * Thrown when OAuth 2.0 token acquisition fails.
 * In the email-ext-plugin integration, this will be caught in
 * ExtendedEmailPublisherDescriptor#getAuthenticator() and surfaced
 * as an actionable Jenkins build log message.
 */
public class OAuthTokenException extends Exception {

    public OAuthTokenException(String message) {
        super(message);
    }

    public OAuthTokenException(String message, Throwable cause) {
        super(message, cause);
    }
}
