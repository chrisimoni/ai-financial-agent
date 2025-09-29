package com.advisor.config;

import com.advisor.model.User;
import com.advisor.service.RAGService;
import com.advisor.service.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientService;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.security.web.authentication.SimpleUrlAuthenticationSuccessHandler;
import org.springframework.stereotype.Component;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.LocalDateTime;
import java.time.ZoneId;

@Component
@RequiredArgsConstructor
@Slf4j
public class OAuth2AuthenticationSuccessHandler extends SimpleUrlAuthenticationSuccessHandler {

    private final UserService userService;
    private final RAGService ragService;
    private final OAuth2AuthorizedClientService authorizedClientService;


    @Value("${app.frontend-url:http://localhost:3000}")
    private String frontendUrl;

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response,
                                        Authentication authentication) throws IOException, ServletException {
        try {
            OAuth2User oauth2User = (OAuth2User) authentication.getPrincipal();
            String email = oauth2User.getAttribute("email");
            String name = oauth2User.getAttribute("name");

            // Create or find user
            User user = userService.findOrCreateUser(email, name);
            saveGoogleAccessToken(user, authentication);

            // Generate JWT token
            String token = userService.generateJwtToken(user);

            // Start RAG indexing in background
            try {
//                ragService.performFullIndexing(user);
            } catch (Exception e) {
                log.error("Failed to start RAG indexing {} ", e.getMessage());
            }

            // Redirect to frontend with token
            String targetUrl = frontendUrl + "/login/success?token=" + token;
            getRedirectStrategy().sendRedirect(request, response, targetUrl);

        } catch (Exception e) {
            e.printStackTrace();
            String errorUrl = frontendUrl + "/login/error";
            getRedirectStrategy().sendRedirect(request, response, errorUrl);
        }
    }

    private void saveGoogleAccessToken(User user, Authentication authentication) {
        try {
            OAuth2AuthenticationToken oauth2Token = (OAuth2AuthenticationToken) authentication;

            OAuth2AuthorizedClient authorizedClient = authorizedClientService.loadAuthorizedClient(
                    oauth2Token.getAuthorizedClientRegistrationId(),
                    oauth2Token.getName()
            );

            if (authorizedClient != null && authorizedClient.getAccessToken() != null) {
                String accessToken = authorizedClient.getAccessToken().getTokenValue();
                user.setGoogleAccessToken(accessToken);

                if (authorizedClient.getAccessToken().getExpiresAt() != null) {
                    LocalDateTime expirationTime = LocalDateTime.ofInstant(
                            authorizedClient.getAccessToken().getExpiresAt(),
                            ZoneId.systemDefault()
                    );
                    user.setGoogleTokenExpiration(expirationTime);
                    log.info("Google token expires at: {}", expirationTime);
                }

                // Save refresh token if available
                if (authorizedClient.getRefreshToken() != null) {
                    user.setGoogleRefreshToken(authorizedClient.getRefreshToken().getTokenValue());
                    log.info("Refresh token saved for user: {}", user.getEmail());
                } else {
                    log.warn("No refresh token available for user: {}", user.getEmail());
                }

                userService.saveUser(user);
                log.info("Google access token saved for user: {}", user.getEmail());
            }
        } catch (Exception e) {
            log.error("Failed to save Google access token: {}", e.getMessage(), e);
        }
    }
}