package io.github.arpanc6.oauth2smtp;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import javax.mail.PasswordAuthentication;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for XOAuth2Authenticator.
 * Verifies XOAUTH2 SASL token format as required by Microsoft and Google SMTP.
 */
class XOAuth2AuthenticatorTest {

    private static final String EMAIL = "jenkins@contoso.com";
    private static final String TOKEN = "mock-access-token-12345";

    @Test
    @DisplayName("Should produce correct XOAUTH2 format: base64(user={email}\\x01auth=Bearer {token}\\x01\\x01)")
    void testXOAuth2TokenFormat() {
        String result = XOAuth2Authenticator.buildXOAuth2Token(EMAIL, TOKEN);

        // Decode and verify the raw format
        String decoded = new String(Base64.getDecoder().decode(result), StandardCharsets.UTF_8);

        assertTrue(decoded.startsWith("user=" + EMAIL));
        assertTrue(decoded.contains("\u0001auth=Bearer " + TOKEN));
        assertTrue(decoded.endsWith("\u0001\u0001"));
    }

    @Test
    @DisplayName("getPasswordAuthentication should use email as username and XOAUTH2 token as password")
    void testPasswordAuthentication() {
        XOAuth2Authenticator authenticator = new XOAuth2Authenticator(EMAIL, TOKEN);
        PasswordAuthentication pa = authenticator.getPasswordAuthentication();

        assertEquals(EMAIL, pa.getUserName());

        // Password should be the base64-encoded XOAUTH2 string
        String expectedToken = XOAuth2Authenticator.buildXOAuth2Token(EMAIL, TOKEN);
        assertEquals(expectedToken, pa.getPassword());
    }

    @Test
    @DisplayName("Should produce valid base64 output")
    void testOutputIsValidBase64() {
        String result = XOAuth2Authenticator.buildXOAuth2Token(EMAIL, TOKEN);

        assertDoesNotThrow(() -> Base64.getDecoder().decode(result),
                "Output should be valid base64");
    }

    @Test
    @DisplayName("Should handle email addresses with special characters")
    void testEmailWithPlusSign() {
        String emailWithPlus = "jenkins+notifications@contoso.com";
        String result = XOAuth2Authenticator.buildXOAuth2Token(emailWithPlus, TOKEN);
        String decoded = new String(Base64.getDecoder().decode(result), StandardCharsets.UTF_8);

        assertTrue(decoded.contains("user=" + emailWithPlus));
    }

    @Test
    @DisplayName("XOAUTH2 token should exactly match Microsoft specification")
    void testExactMicrosoftFormat() {
        // Microsoft specification: base64("user=<email>\x01auth=Bearer <token>\x01\x01")
        String expectedRaw = "user=" + EMAIL + "\u0001auth=Bearer " + TOKEN + "\u0001\u0001";
        String expectedEncoded = Base64.getEncoder()
                .encodeToString(expectedRaw.getBytes(StandardCharsets.UTF_8));

        String result = XOAuth2Authenticator.buildXOAuth2Token(EMAIL, TOKEN);

        assertEquals(expectedEncoded, result,
                "XOAUTH2 token must exactly match Microsoft specification");
    }
}
