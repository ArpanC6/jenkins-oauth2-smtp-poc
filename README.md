# Jenkins OAuth2 SMTP POC

**GSoC 2026 Proof of Concept**  
**Project:** Jenkins Email Notifications Using Outlook SMTP with OAuth 2.0  
**Plugin:** [email-ext-plugin](https://github.com/jenkinsci/email-ext-plugin)  
**Author:** [Arpan Chakraborty](https://github.com/ArpanC6)

---

## Problem This Solves

Microsoft deprecated basic authentication for Exchange Online SMTP in September 2025.  
Jenkins administrators using Outlook for email notifications see this error:

```
DEBUG SMTP: AUTH XOAUTH2 failed
535 5.7.3 Authentication unsuccessful
```

**Root cause:** `ExtendedEmailPublisherDescriptor#getAuthenticator()` passes `client_secret` directly as the SMTP password instead of fetching a proper OAuth2 access token.

---

## What This POC Demonstrates

This repository is a standalone Java implementation of the **OAuth 2.0 Client Credentials Flow** (RFC 6749, §4.4) for Outlook SMTP authentication — the exact fix proposed for the email-ext-plugin.

### Components

| Class | Purpose |
|---|---|
| `OAuthTokenProvider` | Fetches + caches Bearer tokens from Microsoft Identity Platform |
| `XOAuth2Authenticator` | Jakarta Mail `Authenticator` using XOAUTH2 SASL format |
| `OAuthTokenResponse` | JSON model for Azure AD token response |
| `OAuthTokenException` | Typed exception for actionable error messages |
| `OAuthSmtpDemo` | End-to-end demo runner (simulation + real mode) |

### Flow

```
Jenkins                     Microsoft Identity Platform
  |                                    |
  |-- POST /{tenant}/oauth2/v2.0/token -->|
  |   grant_type=client_credentials    |
  |   client_id, client_secret         |
  |   scope=https://outlook.office365.com/.default
  |                                    |
  |<------ access_token (TTL: 3599s) --|
  |
  |           Outlook SMTP (smtp.office365.com:587)
  |                    |
  |-- STARTTLS ------->|
  |-- AUTH XOAUTH2 --->|  (base64 encoded: user=email\x01auth=Bearer {token}\x01\x01)
  |<-- 235 Auth OK ----|
  |-- SEND EMAIL ----->|
  |<-- 250 Delivered --|
```

---

## Running the Tests (No Azure Account Required)

Tests use **WireMock** to mock the Microsoft token endpoint — runs completely offline.

```bash
git clone https://github.com/ArpanC6/jenkins-oauth2-smtp-poc
cd jenkins-oauth2-smtp-poc
mvn test
```

Expected output:

```
[INFO] Tests run: 8, Failures: 0, Errors: 0, Skipped: 0 -- OAuthTokenProviderTest
[INFO] Tests run: 5, Failures: 0, Errors: 0, Skipped: 0 -- XOAuth2AuthenticatorTest
[INFO] Tests run: 13, Failures: 0, Errors: 0, Skipped: 0
[INFO] BUILD SUCCESS
```

### Test Coverage

#### `OAuthTokenProviderTest` — Token Acquisition & Caching (8 tests)

| Test | What It Verifies |
|---|---|
| `testSuccessfulTokenFetch` | Happy path token acquisition |
| `testTokenCaching` | Second call uses cache (no redundant HTTP) |
| `testCacheInvalidation` | Fresh token after `invalidateCache()` |
| `testIndependentCachingPerCredentialsId` | Separate cache per Jenkins credential |
| `testInvalidClientError` | Actionable error on wrong client secret |
| `testUnauthorizedClientError` | Error on missing SMTP.Send permission |
| `testNetworkTimeout` | Graceful failure on network error |
| `testMissingAccessToken` | Error on malformed token response |

#### `XOAuth2AuthenticatorTest` — SASL Encoding & Format (5 tests)

| Test | What It Verifies |
|---|---|
| `testXOAuth2TokenFormat` | XOAUTH2 SASL format matches Microsoft spec (`user=\x01auth=Bearer\x01\x01`) |
| `testPasswordAuthentication` | `getPasswordAuthentication()` returns email as username and XOAUTH2 token as password |
| `testOutputIsValidBase64` | Output is valid base64-decodable string |
| `testEmailWithPlusSign` | Handles email addresses with special characters (e.g. `+`) |
| `testExactMicrosoftFormat` | Byte-for-byte format verification against Microsoft specification |

---

## Demo

### Build & Test — Full Run

> Terminal recording of `mvn clean test` with all 13 tests passing:

<!-- Add your terminal screenshot or GIF here -->
<!-- Example: ![mvn clean test passing](./assets/demo-terminal.png) -->
<!-- Or link to a video: [Watch demo on YouTube](https://youtu.be/your-link) -->

### Simulation Mode (no credentials needed)

```bash
mvn compile exec:java -Dexec.mainClass=io.github.arpanc6.oauth2smtp.OAuthSmtpDemo
```

Output:
```
=== Jenkins OAuth2 SMTP POC — GSoC 2026 ===

Step 1: Token Acquisition (simulated)
  POST https://login.microsoftonline.com/{tenant}/oauth2/v2.0/token
  Body: grant_type=client_credentials&client_id=...&scope=...
  Response: { "access_token": "eyJ0eXAi...", "expires_in": 3599 }

Step 2: XOAUTH2 Token Encoding
  Raw:    user=jenkins@contoso.com\x01auth=Bearer eyJ0eXAi...\x01\x01
  Base64: dXNlcj1qZW5raW5zQGNvbnRvc28uY29tA...

Step 3: SMTP Session (simulated)
  HOST:     smtp.office365.com:587
  STARTTLS: enabled
  AUTH:     XOAUTH2 dXNlcj1...
  Server:   235 2.7.0 Authentication successful

Step 4: Token Cache
  Cached for:        3540 seconds (59 minutes)
  Proactive refresh: 60 seconds before expiry
  Thread-safe:       ConcurrentHashMap — safe for concurrent Jenkins builds

✅ POC simulation complete.
```

### Real Mode (with Azure AD app)

1. Register an app at [portal.azure.com](https://portal.azure.com)
2. Add API Permission: `SMTP.Send` (Application, not Delegated)
3. Create a client secret

```bash
export AZURE_TENANT_ID="your-tenant-id"
export AZURE_CLIENT_ID="your-client-id"
export AZURE_CLIENT_SECRET="your-client-secret"
export SMTP_FROM_EMAIL="jenkins@yourdomain.com"
export SMTP_TO_EMAIL="recipient@example.com"

mvn compile exec:java -Dexec.mainClass=io.github.arpanc6.oauth2smtp.OAuthSmtpDemo
```

---

## How This Maps to the email-ext-plugin Fix

The core change in the plugin will be in `ExtendedEmailPublisherDescriptor.java`:

**Before (broken):**
```java
// Passes client_secret as SMTP password → AUTH XOAUTH2 fails
private Authenticator getAuthenticator(final MailAccount acc, ...) {
    return authenticatorProvider.apply(acc, context.getRun());
}
```

**After (this POC):**
```java
private Authenticator getAuthenticator(final MailAccount acc, ...) {
    if (acc.isUseOAuth2() && !StringUtils.isBlank(acc.getOauthCredentialsId())) {
        EntraOAuthCredentials creds = CredentialsProvider.findCredentialById(...);
        String accessToken = creds.getAccessToken("https://outlook.office365.com/.default");
        return new XOAuth2Authenticator(acc.getAddress(), accessToken);
    }
    // Backward compatible basic auth fallback
    return authenticatorProvider.apply(acc, context.getRun());
}
```

The `OAuthTokenProvider` and `XOAuth2Authenticator` classes in this POC will be integrated directly into the email-ext-plugin, with the token acquisition delegated to the `entra-oauth-plugin` (as confirmed by maintainer Alex Earl [@slide] in [issue #1420](https://github.com/jenkinsci/email-ext-plugin/issues/1420)).

---

## Related

- [GSoC 2026 Proposal (PDF)](https://github.com/ArpanC6/jenkins-oauth2-smtp-poc/blob/main/proposal/GSoC_2026_Jenkins_OAuth_SMTP.pdf)
- [email-ext-plugin](https://github.com/jenkinsci/email-ext-plugin)
- [Issue #1420 — SMTP OAuth2 support](https://github.com/jenkinsci/email-ext-plugin/issues/1420)
- [entra-oauth-plugin](https://github.com/jenkinsci/entra-oauth-plugin)
- [Microsoft OAuth2 Client Credentials Flow](https://learn.microsoft.com/en-us/entra/identity-platform/v2-oauth2-client-creds-grant-flow)

---

*This POC was developed as part of the GSoC 2026 application for the Jenkins organization.*

