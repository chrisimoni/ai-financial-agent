package com.advisor.controller;

import com.advisor.model.User;
import com.advisor.service.HubSpotService;
import com.advisor.service.RAGService;
import com.advisor.service.UserService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.view.RedirectView;

import java.util.Map;

@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
@Slf4j
public class AuthController {

    private final UserService userService;
    private final HubSpotService hubSpotService;

    @Value("${app.frontend-url:http://localhost:3000}")
    private String frontendUrl;

    @GetMapping("/user")
    public ResponseEntity<?> getCurrentUser(Authentication authentication) {
        log.debug("getCurrentUser method called");
        if (authentication == null) {
            log.error("Authorization object is missing or empty");
            return ResponseEntity.badRequest().body(Map.of("success", false, "error", "Not authenticated"));
        }
        try {
            User user = userService.getCurrentUser(authentication);
            Map<String, Object> response = Map.of(
                    "id", user.getId(),
                    "email", user.getEmail(),
                    "name", user.getName(),
                    "hasGoogleAuth", user.getGoogleAccessToken() != null,
                    "hasHubSpotAuth", user.getHubspotAccessToken() != null
            );
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", "User not found"));
        }
    }

    @PostMapping("/logout")
    public ResponseEntity<?> logout() {
        return ResponseEntity.ok(Map.of("success", true, "message", "Logged out successfully"));
    }

    @GetMapping("/logout/success")
    public RedirectView logoutSuccess() {
        return new RedirectView(frontendUrl + "/login");
    }

    @GetMapping("/hubspot")
    public ResponseEntity<?> connectHubSpot() {
        String hubspotAuthUrl = hubSpotService.getConnectionUrl();
        return ResponseEntity.ok(Map.of("authUrl", hubspotAuthUrl));
    }

    @PostMapping("/hubspot/callback")
    public ResponseEntity<?> hubspotCallback(@RequestBody Map<String, String> request, HttpServletRequest httpRequest) {
        try {
            String code = request.get("code");
            log.info("HubSpot callback triggered with code: {}", code != null ? "present" : "null");

            if (code == null || code.trim().isEmpty()) {
                log.error("Authorization code is missing or empty");
                return ResponseEntity.badRequest().body(Map.of("success", false, "error", "Authorization code is missing"));
            }

            // Extract JWT token from Authorization header
            String authHeader = httpRequest.getHeader("Authorization");
            log.info("Authorization header present: {}", authHeader != null);

            if (authHeader == null || !authHeader.startsWith("Bearer ")) {
                log.error("No valid Authorization header found");
                return ResponseEntity.badRequest().body(Map.of("success", false, "error", "Not authenticated"));
            }

            String token = authHeader.substring(7);
            User user = userService.getUserFromJwtToken(token);
            log.info("Processing HubSpot callback for user: {}", user.getEmail());

            // Exchange code for access token
            String accessToken = hubSpotService.exchangeCodeForToken(code);
            log.info("HubSpot access token obtained successfully");

            // Save HubSpot access token
            user.setHubspotAccessToken(accessToken);
            userService.saveUser(user);
            log.info("HubSpot access token saved for user: {}", user.getEmail());

            return ResponseEntity.ok(Map.of("success", true));

        } catch (org.springframework.web.client.HttpClientErrorException e) {
            log.error("HubSpot API error: {}", e.getResponseBodyAsString());

            // Handle specific HubSpot errors
            String errorMessage = "HubSpot connection failed";
            if (e.getResponseBodyAsString().contains("BAD_AUTH_CODE")) {
                errorMessage = "Authorization code has expired or already been used. Please try connecting again.";
            } else if (e.getResponseBodyAsString().contains("INVALID_CLIENT")) {
                errorMessage = "Invalid HubSpot application configuration";
            }

            return ResponseEntity.badRequest().body(Map.of("success", false, "error", errorMessage));
        } catch (Exception e) {
            log.error("Error in HubSpot callback: ", e);
            return ResponseEntity.badRequest().body(Map.of("success", false, "error", "Connection failed. Please try again."));
        }
    }


}
