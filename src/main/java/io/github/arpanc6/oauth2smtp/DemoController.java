package io.github.arpanc6.oauth2smtp;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.*;

@RestController
@RequestMapping("/api")
public class DemoController {

    @GetMapping("/demo")
    public Map<String, Object> runDemo() {
        Map<String, Object> response = new LinkedHashMap<>();
        List<Map<String, Object>> steps = new ArrayList<>();

        // Step 1: Token Request
        steps.add(step(1, "Token Request",
                "POST https://login.microsoftonline.com/{tenant}/oauth2/v2.0/token",
                "grant_type=client_credentials&scope=https://outlook.office365.com/.default",
                "CACHE MISS — fetching new token from Microsoft Entra ID",
                "info"));

        // Step 2: Token Response
        steps.add(step(2, "Token Received",
                "HTTP 200 OK from Microsoft Identity Platform",
                "{ \"access_token\": \"eyJ0eXAiOiJKV1QiLCJhbGciOiJSUzI1NiJ9...\", \"expires_in\": 3599 }",
                "Bearer token valid for 3599 seconds (~59 minutes)",
                "success"));

        // Step 3: XOAUTH2 Encoding
        String simulatedToken = "eyJ0eXAiOiJKV1QiLCJhbGciOiJSUzI1NiJ9.SIMULATED";
        String xoauth2 = XOAuth2Authenticator.buildXOAuth2Token(
                "jenkins@yourdomain.com", simulatedToken);
        steps.add(step(3, "XOAUTH2 Encoding",
                "XOAuth2Authenticator.buildXOAuth2Token(email, accessToken)",
                "Base64(\"user=jenkins@yourdomain.com\\x01auth=Bearer eyJ0...\\x01\\x01\")",
                "Encoded: " + xoauth2.substring(0, Math.min(40, xoauth2.length())) + "...",
                "info"));

        // Step 4: SMTP AUTH
        steps.add(step(4, "SMTP Authentication",
                "AUTH XOAUTH2 → smtp.office365.com:587",
                "STARTTLS handshake → AUTH XOAUTH2 <base64_token>",
                "← 235 2.7.0 Authentication successful",
                "success"));

        // Step 5: Cache Hit
        steps.add(step(5, "Cache Hit (2nd Request)",
                "OAuthTokenProvider.getToken(credentialsId)",
                "ConcurrentHashMap lookup — token not yet expired",
                "Returned cached token in ~0.016ms (300× faster than 5ms target)",
                "success"));

        response.put("status", "ok");
        response.put("title", "OAuth 2.0 Client Credentials Flow — Live Demo");
        response.put("description",
                "Simulates the exact fix proposed for jenkinsci/email-ext-plugin to resolve Microsoft SMTP OAuth2 authentication.");
        response.put("steps", steps);
        response.put("githubUrl", "https://github.com/ArpanC6/jenkins-oauth2-smtp-poc");
        response.put("issueUrl", "https://github.com/jenkinsci/email-ext-plugin/issues/1420");
        return response;
    }

    private Map<String, Object> step(int num, String title, String action,
                                     String detail, String result, String type) {
        Map<String, Object> s = new LinkedHashMap<>();
        s.put("step", num);
        s.put("title", title);
        s.put("action", action);
        s.put("detail", detail);
        s.put("result", result);
        s.put("type", type);
        return s;
    }
}