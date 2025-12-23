package dev.horbatiuk.timecapsule.service.security;

import dev.horbatiuk.timecapsule.security.CustomUserDetails;
import io.jsonwebtoken.*;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.stereotype.Component;

import java.security.Key;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;

@Component
public class JwtService {

    private static final Logger logger = LoggerFactory.getLogger(JwtService.class);

    @Value("${jwt.secret.key}")
    public String SECRET;

    @Value("${jwt.secret.access-token-expiration-ms}")
    private long accessTokenExpiration;

    public String generateToken(CustomUserDetails userDetails) {
        Map<String, Object> claims = new HashMap<>();
        claims.put("roles", userDetails.getAuthorities()
                .stream()
                .map(GrantedAuthority::getAuthority)
                .toList());

        String email = userDetails.getEmail();
        logger.debug("Generating JWT token for user: {}", email);
        String token = createToken(claims, email);
        logger.info("JWT token generated for user: {}", email);
        return token;
    }

    private String createToken(Map<String, Object> claims, String username) {
        Date now = new Date(System.currentTimeMillis());
        Date expiry = new Date(now.getTime() + accessTokenExpiration);

        logger.debug("Creating JWT with expiration at {}", expiry);
        return Jwts.builder()
                .setClaims(claims)
                .setSubject(username)
                .setIssuedAt(now)
                .setExpiration(expiry)
                .signWith(getSignKey(), SignatureAlgorithm.HS256)
                .compact();
    }

    private Key getSignKey() {
        logger.debug("Decoding JWT signing key");
        byte[] keyBytes = Decoders.BASE64.decode(SECRET);
        return Keys.hmacShaKeyFor(keyBytes);
    }

    public String extractUsername(String token) {
        String username = extractClaim(token, Claims::getSubject);
        logger.debug("Extracted username from token: {}", username);
        return username;
    }

    public Date extractExpiration(String token) {
        Date expiration = extractClaim(token, Claims::getExpiration);
        logger.debug("Extracted expiration from token: {}", expiration);
        return expiration;
    }

    public <T> T extractClaim(String token, Function<Claims, T> claimsResolver) {
        final Claims claims = extractAllClaims(token);
        return claimsResolver.apply(claims);
    }

    private Claims extractAllClaims(String token) {
        try {
            logger.debug("Parsing JWT claims");
            return Jwts.parser()
                    .setSigningKey(getSignKey())
                    .build()
                    .parseClaimsJws(token)
                    .getBody();
        } catch (ExpiredJwtException e) {
            logger.warn("JWT token expired: {}", token);
            throw new JwtException("JWT token expired");
        } catch (JwtException e) {
            logger.warn("Invalid JWT token: {}", token);
            throw new JwtException("Invalid JWT token");
        }
    }

    private Boolean isTokenExpired(String token) {
        boolean expired = extractExpiration(token).before(new Date());
        logger.debug("Token expiration check: expired={}", expired);
        return expired;
    }

    public Boolean validateToken(String token, CustomUserDetails userDetails) {
        String username = extractUsername(token);
        boolean valid = username.equals(userDetails.getEmail()) && !isTokenExpired(token);
        logger.info("JWT validation result for user {}: {}", username, valid ? "valid" : "invalid");
        return valid;
    }
}
