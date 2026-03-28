package io.github.arpanc6.oauth2smtp;

import jakarta.mail.*;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
import java.util.Properties;
import java.util.logging.Logger;

/**
 * Demonstrates the complete OAuth 2.0 SMTP authentication flow for Jenkins email-ext-plugin.
 *
 * <p>This POC shows how the broken {@code getAuthenticator()} in
 * {@code ExtendedEmailPublisherDescriptor} will be fixed:
 *
 * <p><b>BEFORE (broken — currently in email-ext-plugin):</b>
 * <pre>
 *   // Passes client_secret as SMTP password → 535 5.7.3 Authentication unsuccessful
 *   return new PasswordAuthentication(username, clientSecret);
 * </pre>
 *
 * <p><b>AFTER (this POC — proposed GSoC fix):</b>
 * <pre>
 *   String token = tokenProvider.getAccessToken(credentialsId, tenantId, clientId, clientSecret, scope);
 *   return new XOAuth2Authenticator(email, token);
 * </pre>
 *
 * <p><b>To run with a real Outlook account:</b>
 * <ol>
 *   <li>Register an Azure AD application at portal.azure.com</li>
 *   <li>Grant "SMTP.Send" API permission (Application, not Delegated)</li>
 *   <li>Set the environment variables below</li>
 *   <li>Run: {@code mvn compile exec:java -Dexec.mainClass=io.github.arpanc6.oauth2smtp.OAuthSmtpDemo}</li>
 * </ol>
 */
public class OAuthSmtpDemo {

    private static final Logger LOGGER = Logger.getLogger(OAuthSmtpDemo.class.getName());

    // Read from environment variables (never hardcode credentials)
    private static final String TENANT_ID     = System.getenv("AZURE_TENANT_ID");
    private static final String CLIENT_ID     = System.getenv("AZURE_CLIENT_ID");
    private static final String CLIENT_SECRET = System.getenv("AZURE_CLIENT_SECRET");
    private static final String FROM_EMAIL    = System.getenv("SMTP_FROM_EMAIL");
    private static final String TO_EMAIL      = System.getenv("SMTP_TO_EMAIL");

    public static void main(String[] args) {
        System.out.println("=== Jenkins OAuth2 SMTP POC — GSoC 2026 ===");
        System.out.println("Demonstrating OAuth 2.0 Client Credentials Flow for Outlook SMTP");
        System.out.println();

        // Validate environment
        if (TENANT_ID == null || CLIENT_ID == null || CLIENT_SECRET == null) {
            System.out.println("[DEMO MODE] Environment variables not set.");
            System.out.println("Running in simulation mode to demonstrate the flow...");
            System.out.println();
            runSimulationDemo();
            return;
        }

        runRealDemo();
    }

    /**
     * Simulation mode — shows the complete flow without real Azure AD credentials.
     * This is what evaluators will see when running the POC without Azure setup.
     */
    private static void runSimulationDemo() {
        System.out.println("Step 1: Token Acquisition (simulated)");
        System.out.println("  POST https://login.microsoftonline.com/{tenant}/oauth2/v2.0/token");
        System.out.println("  Body: grant_type=client_credentials&client_id=...&scope=https://outlook.office365.com/.default");
        System.out.println("  Response: { \"access_token\": \"eyJ0eXAi...\", \"expires_in\": 3599 }");
        System.out.println();

        // Demonstrate XOAUTH2 token building
        String simulatedToken = "eyJ0eXAiOiJKV1QiLCJhbGciOiJSUzI1NiJ9.simulated.token";
        String simulatedEmail = "jenkins-notifications@contoso.com";

        System.out.println("Step 2: XOAUTH2 Token Encoding");
        String xoauth2 = XOAuth2Authenticator.buildXOAuth2Token(simulatedEmail, simulatedToken);
        System.out.println("  Raw:     user=" + simulatedEmail + "\\x01auth=Bearer " + simulatedToken + "\\x01\\x01");
        System.out.println("  Base64:  " + xoauth2.substring(0, Math.min(60, xoauth2.length())) + "...");
        System.out.println();

        System.out.println("Step 3: SMTP Session (simulated)");
        System.out.println("  HOST:    smtp.office365.com:587");
        System.out.println("  STARTTLS: enabled");
        System.out.println("  AUTH:    XOAUTH2 " + xoauth2.substring(0, 20) + "...");
        System.out.println("  Server:  235 2.7.0 Authentication successful");
        System.out.println();

        System.out.println("Step 4: Token Cache");
        System.out.println("  Cached for: 3540 seconds (59 minutes)");
        System.out.println("  Proactive refresh: 60 seconds before expiry");
        System.out.println("  Thread-safe: ConcurrentHashMap — safe for concurrent Jenkins builds");
        System.out.println();

        System.out.println("✅ POC simulation complete. See OAuthTokenProviderTest for full unit tests.");
    }

    /**
     * Real mode — runs with actual Azure AD credentials from environment variables.
     */
    private static void runRealDemo() {
        try {
            System.out.println("Step 1: Fetching OAuth2 access token from Azure AD...");
            OAuthTokenProvider tokenProvider = new OAuthTokenProvider();
            String accessToken = tokenProvider.getAccessToken(
                    "demo-credentials-id",
                    TENANT_ID,
                    CLIENT_ID,
                    CLIENT_SECRET,
                    OAuthTokenProvider.OUTLOOK_SMTP_SCOPE
            );
            System.out.println("✅ Token acquired. Length: " + accessToken.length() + " chars");
            System.out.println();

            System.out.println("Step 2: Sending email via Outlook SMTP with XOAUTH2...");
            sendEmail(FROM_EMAIL, TO_EMAIL, accessToken);
            System.out.println("✅ Email sent successfully!");

        } catch (OAuthTokenException e) {
            System.err.println("❌ Token acquisition failed: " + e.getMessage());
        } catch (MessagingException e) {
            System.err.println("❌ SMTP send failed: " + e.getMessage());
        }
    }

    private static void sendEmail(String from, String to, String accessToken)
            throws MessagingException {

        Properties props = new Properties();
        props.put("mail.smtp.host", "smtp.office365.com");
        props.put("mail.smtp.port", "587");
        props.put("mail.smtp.auth", "true");
        props.put("mail.smtp.starttls.enable", "true");
        props.put("mail.smtp.auth.mechanisms", "XOAUTH2");

        Session session = Session.getInstance(props, new XOAuth2Authenticator(from, accessToken));
        session.setDebug(true);

        Message message = new MimeMessage(session);
        message.setFrom(new InternetAddress(from));
        message.setRecipients(Message.RecipientType.TO, InternetAddress.parse(to));
        message.setSubject("Jenkins OAuth2 SMTP POC — GSoC 2026");
        message.setText("This email was sent using OAuth 2.0 Client Credentials Flow.\n\n"
                + "This demonstrates the fix for the broken XOAUTH2 authentication\n"
                + "in the Jenkins email-ext-plugin.\n\n"
                + "GitHub: https://github.com/ArpanC6/jenkins-oauth2-smtp-poc");

        Transport.send(message);
    }
}
