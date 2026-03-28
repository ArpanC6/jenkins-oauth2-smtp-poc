package io.github.arpanc6.oauth2smtp;

import jakarta.mail.Authenticator;
import jakarta.mail.PasswordAuthentication;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.logging.Logger;

/**
 * Jakarta Mail {@link Authenticator} that uses an OAuth 2.0 access token
 * for XOAUTH2 SMTP authentication with Outlook/Exchange Online.
 *
 * <p>This is the direct replacement for the broken code in email-ext-plugin's
 * {@code ExtendedEmailPublisherDescriptor#getAuthenticator()}, which currently
 * passes {@code client_secret} as the SMTP password, causing:
 * <pre>
 *   DEBUG SMTP: AUTH XOAUTH2 failed
 *   535 5.7.3 Authentication unsuccessful
 * </pre>
 *
 * <p>XOAUTH2 SASL format (RFC 6749 + Google/Microsoft extension):
 * <pre>
 *   base64("user={email}\x01auth=Bearer {token}\x01\x01")
 * </pre>
 *
 * <p>Jakarta Mail usage:
 * <pre>
 *   Properties props = new Properties();
 *   props.put("mail.smtp.auth.mechanisms", "XOAUTH2");
 *   props.put("mail.smtp.auth", "true");
 *   props.put("mail.smtp.starttls.enable", "true");
 *   props.put("mail.smtp.host", "smtp.office365.com");
 *   props.put("mail.smtp.port", "587");
 *
 *   Session session = Session.getInstance(props, new XOAuth2Authenticator(email, accessToken));
 * </pre>
 */
public class XOAuth2Authenticator extends Authenticator {

    private static final Logger LOGGER = Logger.getLogger(XOAuth2Authenticator.class.getName());

    private final String email;
    private final String accessToken;

    /**
     * @param email       The sender email address (used as SMTP username)
     * @param accessToken Bearer access token from OAuth2 token endpoint
     */
    public XOAuth2Authenticator(String email, String accessToken) {
        this.email = email;
        this.accessToken = accessToken;
    }

    /**
     * Returns the XOAUTH2-encoded credentials as a PasswordAuthentication.
     *
     * <p>Jakarta Mail passes the "password" field directly to the XOAUTH2
     * SASL mechanism, which expects it to be base64-encoded in the format:
     * {@code "user={email}\x01auth=Bearer {token}\x01\x01"}
     */
    @Override
    protected PasswordAuthentication getPasswordAuthentication() {
        String xoauth2Token = buildXOAuth2Token(email, accessToken);
        LOGGER.fine("Built XOAUTH2 token for email=" + email);
        // Username is the email; password is the base64-encoded XOAUTH2 string
        return new PasswordAuthentication(email, xoauth2Token);
    }

    /**
     * Builds the base64-encoded XOAUTH2 token string.
     *
     * <p>Format: {@code base64("user={email}\x01auth=Bearer {token}\x01\x01")}
     *
     * @param email       sender email address
     * @param accessToken OAuth2 Bearer access token
     * @return base64-encoded XOAUTH2 string for SMTP AUTH XOAUTH2
     */
    static String buildXOAuth2Token(String email, String accessToken) {
        // \u0001 is the SOH (Start of Heading) control character used as XOAUTH2 separator
        String raw = "user=" + email
                + "\u0001auth=Bearer " + accessToken
                + "\u0001\u0001";
        return Base64.getEncoder().encodeToString(raw.getBytes(StandardCharsets.UTF_8));
    }
}
