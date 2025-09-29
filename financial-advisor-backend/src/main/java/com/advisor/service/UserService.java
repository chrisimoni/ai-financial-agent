package com.advisor.service;

import com.advisor.model.User;
import com.advisor.repository.UserRepository;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.util.Date;

@Service
@RequiredArgsConstructor
@Slf4j
public class UserService {
    private final UserRepository userRepository;

    @Value("${jwt.secret}")
    private String jwtSecret;

    @Value("${jwt.expiration:86400000}")
    private long jwtExpiration;

    private SecretKey getSigningKey() {
        return Keys.hmacShaKeyFor(jwtSecret.getBytes());
    }

    public String generateJwtToken(User user) {
        Date expiryDate = new Date(System.currentTimeMillis() + jwtExpiration);

        return Jwts.builder()
                .setSubject(user.getEmail())
                .setIssuedAt(new Date())
                .setExpiration(expiryDate)
                .claim("userId", user.getId())
                .claim("name", user.getName())
                .signWith(getSigningKey(), SignatureAlgorithm.HS512)
                .compact();
    }

    public User getUserFromJwtToken(String token) {
        try {
            Claims claims = Jwts.parser()
                    .setSigningKey(getSigningKey())
                    .build()
                    .parseClaimsJws(token)
                    .getBody();

            String email = claims.getSubject();
            Long userId = claims.get("userId", Long.class);

            return userRepository.findById(userId)
                    .orElseGet(() -> userRepository.findByEmail(email)
                            .orElseThrow(() -> new RuntimeException("User not found")));

        } catch (Exception e) {
            throw new RuntimeException("Invalid JWT token", e);
        }
    }

    public User findOrCreateUser(String email, String name) {
        return userRepository.findByEmail(email)
                .orElseGet(() -> {
                    User newUser = new User(email, name);
                    return userRepository.save(newUser);
                });
    }

    public User getCurrentUser(Authentication authentication) {
        if (authentication == null) {
            throw new RuntimeException("No authentication found");
        }

        // Handle OAuth2 authentication (during login)
        if (authentication.getPrincipal() instanceof OAuth2User) {
            OAuth2User oauth2User = (OAuth2User) authentication.getPrincipal();
            String email = oauth2User.getAttribute("email");
            return userRepository.findByEmail(email)
                    .orElseThrow(() -> new RuntimeException("User not found"));
        }
        // Handle JWT authentication (for API calls) - principal is the User object
        else if (authentication.getPrincipal() instanceof User) {
            return (User) authentication.getPrincipal();
        }
        else {
            log.error("Unsupported authentication type: {}", authentication.getPrincipal().getClass());
            throw new RuntimeException("Unsupported authentication type");
        }
    }

    public User saveUser(User user) {
        return userRepository.save(user);
    }
}

