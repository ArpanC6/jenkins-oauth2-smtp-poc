<div align="center">

<img src="https://www.jenkins.io/images/logos/jenkins/jenkins.png" width="120"/>

# Jenkins Email Notifications Using Outlook SMTP with OAuth 2.0

**GSoC 2026 Proof of Concept**

[![Live Demo](https://img.shields.io/badge/Live%20Demo-jenkins--oauth2--smtp--poc.onrender.com-brightgreen?style=for-the-badge&logo=render&logoColor=white)](https://jenkins-oauth2-smtp-poc.onrender.com)
[![Jenkins](https://img.shields.io/badge/Jenkins-GSoC%202026-D24939?style=for-the-badge&logo=jenkins&logoColor=white)](https://www.jenkins.io/projects/gsoc/)
[![Java](https://img.shields.io/badge/Java-11-ED8B00?style=for-the-badge&logo=java&logoColor=white)](https://www.java.com)
[![Maven](https://img.shields.io/badge/Maven-Build-C71A36?style=for-the-badge&logo=apache-maven&logoColor=white)](https://maven.apache.org)
[![Tests](https://img.shields.io/badge/Tests-14%20Passing-brightgreen?style=for-the-badge&logo=github&logoColor=white)](https://github.com/ArpanC6/jenkins-oauth2-smtp-poc)
[![PRs Merged](https://img.shields.io/badge/Merged%20PRs-7-blue?style=for-the-badge&logo=github&logoColor=white)](https://github.com/jenkinsci/email-ext-plugin/pulls?q=is%3Apr+author%3AArpanC6+is%3Amerged)

**Project:** Jenkins Email Notifications Using Outlook SMTP with OAuth 2.0  
**Plugin:** [email-ext-plugin](https://github.com/jenkinsci/email-ext-plugin)  
**Author:** [Arpan Chakraborty](https://github.com/ArpanC6)

</div>

---

##  Live Web Demo

**URL:** https://jenkins-oauth2-smtp-poc.onrender.com

Click **"Run Again"** to see the full OAuth 2.0 Client Credentials Flow simulation:

| Step | What Happens |
|---|---|
| Token Request | POST to Microsoft Entra ID (cache miss) |
| Token Received | Bearer token valid for 3599 seconds |
| XOAUTH2 Encoding | SASL base64 format built by `XOAuth2Authenticator` |
| SMTP Authentication | AUTH XOAUTH2 → 235 Authentication successful |
| Cache Hit | Cached token returned in ~0.016ms |

> **Note:** The first request after inactivity may take ~50 seconds to start (Render free tier spin-up). Subsequent requests are instant.

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

## Contribution Progress (as of April 15, 2026)

I have been actively contributing to [jenkinsci/email-ext-plugin](https://github.com/jenkinsci/email-ext-plugin) throughout the GSoC application period to understand the codebase deeply and demonstrate readiness for the project.

### Merged PRs — 7 Merged

| PR | Description |
|---|---|
| [#1493](https://github.com/jenkinsci/email-ext-plugin/pull/1493) | Fix deprecated `StringUtils.equals` usage in `MailAccount` |
| [#1494](https://github.com/jenkinsci/email-ext-plugin/pull/1494) | Replace deprecated `ACL.SYSTEM` with `Jenkins.getAuthentication()` in `MailAccount` |
| [#1503](https://github.com/jenkinsci/email-ext-plugin/pull/1503) | Add missing `@param` Javadoc tags in `UpstreamComitter` recipient providers |
| [#1505](https://github.com/jenkinsci/email-ext-plugin/pull/1505) | Replace deprecated `ACL.SYSTEM` with `ACL.SYSTEM2` in `doCheckCredentialsId()` and `migrateCredentials()` |
| [#1507](https://github.com/jenkinsci/email-ext-plugin/pull/1507) | Remove unused deprecated `Util` class |
| [#1541](https://github.com/jenkinsci/email-ext-plugin/pull/1541) | Add unit tests for `AbortedTrigger` covering triggered and skipped behavior |
| [#1550](https://github.com/jenkinsci/email-ext-plugin/pull/1550) | Extract SMTP property keys as named constants in `ExtendedEmailPublisherDescriptor` |

### Open PRs — 10 Open (all CI checks passing)

| PR | Description |
|---|---|
| [#1497](https://github.com/jenkinsci/email-ext-plugin/pull/1497) | Fix `GroovyClassLoader` resource leak by wrapping in try-with-resources |
| [#1523](https://github.com/jenkinsci/email-ext-plugin/pull/1523) | Add unit tests for XSS escaping in `EmailExtTemplateAction.renderError()` |
| [#1553](https://github.com/jenkinsci/email-ext-plugin/pull/1553) | Restore thread interrupt flag in `renderTemplate()` and split broad exception handling |
| [#1555](https://github.com/jenkinsci/email-ext-plugin/pull/1555) | Resolve JENKINS-26838 — replace `IsChildFileCallable` with `FilePath.isDescendant()` |
| [#1559](https://github.com/jenkinsci/email-ext-plugin/pull/1559) | Add unit tests for `EmailThrottler` |
| [#1561](https://github.com/jenkinsci/email-ext-plugin/pull/1561) | Restore interrupt flag in `ContentBuilder.transformText()` and add test coverage |
| [#1562](https://github.com/jenkinsci/email-ext-plugin/pull/1562) | Restore interrupt flag in `addContent()` save email output path |
| [#1565](https://github.com/jenkinsci/email-ext-plugin/pull/1565) | Exception handling improvement in `RecipientProviderUtilities` |
| [#1566](https://github.com/jenkinsci/email-ext-plugin/pull/1566) | Add unit tests for `EmailExtTemplateActionFactory` |
| [#1571](https://github.com/jenkinsci/email-ext-plugin/pull/1571) | Replace broad Exception catch with specific exceptions in `renderTemplate` |

### Community Code Reviews

Beyond submitting code, I actively review other contributors' PRs with substantive technical feedback. Some highlights:

- **PR #1480** — Identified a security vulnerability allowing arbitrary file writes via crafted JSON, a Windows path resolution bug, and missing cleanup logic. All 4 issues were fixed immediately.
- **PR #1513** — Suggested `InetAddress.getByName()` for hostname validation. Contributor acknowledged: *"@ArpanC6 it worked. Thanks a lot for the help."*
- **PR #1534** — Identified a trailing quote bug in log messages that would have appeared in actual debug output. Fix commit pushed within minutes.
- **PR #1535** — Identified that `refreshToken()` always threw `OAuthTokenException` as a placeholder without fetching a real token. Contributor fixed all issues immediately.
- **PR #1539** — Identified missing edge case and mock debug mode issue. CI went from 8 to 371 checks green after contributor's fix.

13+ contributors have acted on my review feedback across 30+ reviewed PRs.

## Live Test Result -> Real Microsoft Entra ID (April 17, 2026)

Beyond WireMock-based tests, a real OAuth2 token was successfully 
acquired from Microsoft Entra ID during live testing:

- Real Bearer token acquired (length: 2047 chars)
- STARTTLS established to smtp.office365.com:587
- XOAUTH2 authentication attempted correctly
- SMTP AUTH failed - university tenant admin consent 
  restriction (not a code issue)
- All 14 unit tests still passing

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

**Phase 1 — Token Acquisition**
```
Jenkins  →  POST https://login.microsoftonline.com/{tenant}/oauth2/v2.0/token
            Body: grant_type=client_credentials
                  client_id, client_secret
                  scope=https://outlook.office365.com/.default
         ←  { "access_token": "eyJ0eXAi...", "expires_in": 3599 }
```

**Phase 2 — SMTP Authentication**
```
Jenkins  →  STARTTLS  →  smtp.office365.com:587
         →  AUTH XOAUTH2 <base64(user=email\x01auth=Bearer {token}\x01\x01)>
         ←  235 2.7.0 Authentication successful
         →  SEND EMAIL
         ←  250 Message delivered
```

**Token Cache**
```
First build   →  fetch token from Entra ID  (cache miss)
Second build  →  return cached token        (cache hit, ~0.016ms)
After 59 min  →  proactive refresh 60s before expiry
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
[INFO] Tests run: 9, Failures: 0, Errors: 0, Skipped: 0 -- OAuthTokenProviderTest
[INFO] Tests run: 5, Failures: 0, Errors: 0, Skipped: 0 -- XOAuth2AuthenticatorTest
[INFO] Tests run: 14, Failures: 0, Errors: 0, Skipped: 0
[INFO] BUILD SUCCESS
```

---

## Test Coverage

### `OAuthTokenProviderTest` — Token Acquisition, Caching & Performance (9 tests)

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
| `testCacheHitLatency` | Cache hit latency under 5ms (measured: **0.0163ms**) |

### `XOAuth2AuthenticatorTest` — SASL Encoding & Format (5 tests)

| Test | What It Verifies |
|---|---|
| `testXOAuth2TokenFormat` | XOAUTH2 SASL format matches Microsoft spec (`user=\x01auth=Bearer\x01\x01`) |
| `testPasswordAuthentication` | `getPasswordAuthentication()` returns email as username and XOAUTH2 token as password |
| `testOutputIsValidBase64` | Output is valid base64-decodable string |
| `testEmailWithPlusSign` | Handles email addresses with special characters (e.g. `+`) |
| `testExactMicrosoftFormat` | Byte-for-byte format verification against Microsoft specification |

---

## Performance

The `testCacheHitLatency` test measures the time for a cache hit — after the first token fetch (cache miss via WireMock), the second call returns the cached token with no HTTP request.

```
Cache hit latency: 0.0163ms
```

This is over **300× faster** than the 5ms target, confirming that the `ConcurrentHashMap`-based cache adds negligible overhead to Jenkins email sending.

---

## Demo

###  Live Web Demo (No setup needed)

**https://jenkins-oauth2-smtp-poc.onrender.com**

### Simulation Mode (local, no credentials needed)

```bash
mvn compile exec:java -Dexec.mainClass=io.github.arpanc6.oauth2smtp.OAuthSmtpDemo
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

The `OAuthTokenProvider` and `XOAuth2Authenticator` classes in this POC will be integrated directly into the email-ext-plugin, with token acquisition delegated to the `entra-oauth-plugin` (as confirmed by maintainer Alex Earl [@slide] in [issue #1420](https://github.com/jenkinsci/email-ext-plugin/issues/1420)).

---

## Related

- [email-ext-plugin](https://github.com/jenkinsci/email-ext-plugin)
- [Issue #1420 — SMTP OAuth2 support](https://github.com/jenkinsci/email-ext-plugin/issues/1420)
- [entra-oauth-plugin](https://github.com/jenkinsci/entra-oauth-plugin)
- [Microsoft OAuth2 Client Credentials Flow](https://learn.microsoft.com/en-us/entra/identity-platform/v2-oauth2-client-creds-grant-flow)
- [Community Forum Discussion](https://community.jenkins.io/t/gsoc-2026-arpan-chakraborty-draft-proposal-review-request/36472)
- [All my PRs to email-ext-plugin](https://github.com/jenkinsci/email-ext-plugin/pulls?q=is%3Apr+author%3AArpanC6)

---

*This POC was developed as part of the GSoC 2026 application for the Jenkins organization.*
