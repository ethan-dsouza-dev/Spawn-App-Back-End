package com.danielagapov.spawn.auth.internal.services;


import com.danielagapov.spawn.shared.util.OAuthProvider;
import com.danielagapov.spawn.shared.exceptions.Logger.ILogger;
import com.danielagapov.spawn.shared.exceptions.TokenExpiredException;
import com.danielagapov.spawn.shared.exceptions.OAuthProviderUnavailableException;
import com.danielagapov.spawn.shared.util.RetryHelper;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdToken;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdTokenVerifier;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.gson.GsonFactory;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.security.GeneralSecurityException;

@Service
public final class GoogleOAuthStrategy implements OAuthStrategy {
    private final ILogger logger;
    private GoogleIdTokenVerifier verifier;

    @Value("${google.client.id}")
    private String googleClientId;

    @Value("${google.android.client.id:}")
    private String googleAndroidClientId;


    @Autowired
    public GoogleOAuthStrategy(ILogger logger) {
        this.logger = logger;
        // Don't initialize verifier here - will be initialized in @PostConstruct with proper client ID
    }

    @Override
    public OAuthProvider getOAuthProvider() {
        return OAuthProvider.google;
    }

    /**
     * Verifies a Google ID token and extracts the subject (user ID)
     *
     * @param idToken Google ID token to verify
     * @return the subject (user ID) extracted from the token
     */
    @Override
    public String verifyIdToken(String idToken) {
        try {
            logger.info("Attempting to verify Google ID token");
            logger.info("Using client ID: " + googleClientId);

            // Use retry helper for token verification
            return RetryHelper.executeOAuthWithRetry(() -> {
                try {
                    GoogleIdToken googleIdToken = null;
                    // Verify the token
                    try {
                         googleIdToken = verifier.verify(idToken);
                    } catch (Error e) {
                        logger.error(e.getMessage());
                    }

                    if (googleIdToken == null) {
                        logger.error("Token verification failed - invalid token. Token prefix: " + (idToken != null ? idToken.substring(0, Math.min(20, idToken.length())) + "..." : "null"));
                        throw new SecurityException("Invalid Google ID token - token may be expired or malformed");
                    }

                    logger.info("Token verified successfully");
                    // Get payload data
                    GoogleIdToken.Payload payload = googleIdToken.getPayload();
                    String userId = payload.getSubject();  // Get the user's ID
                    logger.info("Extracted user ID: " + userId);

                    // Check token expiration
                    Long expiration = payload.getExpirationTimeSeconds();
                    if (expiration != null && expiration < System.currentTimeMillis() / 1000) {
                        logger.error("Token has expired. Expiration: " + expiration + ", Current time: " + (System.currentTimeMillis() / 1000));
                        throw new TokenExpiredException("Google ID token has expired, please sign in again");
                    }

                    // Verify additional claims if needed
                    // For example, verify email is verified
                    Boolean emailVerified = payload.getEmailVerified();
                    if (emailVerified == null || !emailVerified) {
                        logger.error("Email not verified for user ID: " + userId + ", emailVerified value: " + emailVerified);
                        throw new SecurityException("Google account email is not verified");
                    }

                    return userId;

                } catch (GeneralSecurityException e) {
                    logger.error("Security error during token verification: " + e.getMessage());
                    throw new SecurityException("Security error during Google token verification: " + e.getMessage(), e);
                } catch (IOException e) {
                    logger.error("Network error during token verification: " + e.getMessage());
                    throw new OAuthProviderUnavailableException("Google authentication service is temporarily unavailable. Please try again later.", e);
                }
            });

        } catch (TokenExpiredException e) {
            logger.error("Token expired: " + e.getMessage());
            throw e;
        } catch (OAuthProviderUnavailableException e) {
            logger.error("OAuth provider unavailable: " + e.getMessage());
            throw e;
        } catch (Exception e) {
            logger.error("Unexpected error during token verification: " + e.getMessage());
            logger.error("Token details: " + (idToken != null ? idToken.substring(0, Math.min(20, idToken.length())) + "..." : "null"));
            throw new SecurityException("Unexpected error during Google token verification: " + e.getMessage(), e);
        }
    }

    // Updated method with @PostConstruct to ensure client ID is loaded from properties
    @PostConstruct
    public void initializeGoogleVerifier() {
        // Try to get iOS client ID from property, which should come from env variable
        String iosClientId = googleClientId;
        logger.info("Retrieved Google iOS client ID from application properties: " + (iosClientId != null ? (iosClientId.substring(0, Math.min(10, iosClientId.length())) + "...") : "null"));

        // If not set in property, try to get directly from environment
        if (iosClientId == null || iosClientId.isEmpty()) {
            iosClientId = System.getenv("GOOGLE_CLIENT_ID");
            logger.info("Getting Google iOS client ID directly from environment variable: " + (iosClientId != null ? (iosClientId.substring(0, Math.min(10, iosClientId.length())) + "...") : "null"));
        }

        // Try to get Android client ID from property
        String androidClientId = googleAndroidClientId;
        logger.info("Retrieved Google Android client ID from application properties: " + (androidClientId != null ? (androidClientId.substring(0, Math.min(10, androidClientId.length())) + "...") : "null"));

        // If not set in property, try to get directly from environment
        if (androidClientId == null || androidClientId.isEmpty()) {
            androidClientId = System.getenv("GOOGLE_ANDROID_CLIENT_ID");
            logger.info("Getting Google Android client ID directly from environment variable: " + (androidClientId != null ? (androidClientId.substring(0, Math.min(10, androidClientId.length())) + "...") : "null"));
        }

        // Build list of allowed client IDs
        java.util.List<String> allowedClientIds = new java.util.ArrayList<>();
        
        if (iosClientId != null && !iosClientId.isEmpty()) {
            allowedClientIds.add(iosClientId);
            logger.info("Added iOS client ID to allowed audience");
        }
        
        if (androidClientId != null && !androidClientId.isEmpty()) {
            allowedClientIds.add(androidClientId);
            logger.info("Added Android client ID to allowed audience");
        }

        // Re-initialize Google ID token verifier with client IDs from properties or environment
        if (!allowedClientIds.isEmpty()) {
            logger.info("Initializing Google token verifier with " + allowedClientIds.size() + " client ID(s)");
            this.verifier = new GoogleIdTokenVerifier.Builder(new NetHttpTransport(), new GsonFactory())
                    .setAudience(allowedClientIds)
                    .build();
            logger.info("Google token verifier successfully initialized with audience: " + allowedClientIds);
        } else {
            logger.error("Google client IDs not set, token verification will fail. Set GOOGLE_CLIENT_ID and/or GOOGLE_ANDROID_CLIENT_ID in your environment.");
            // Create a dummy verifier that will reject all tokens
            this.verifier = new GoogleIdTokenVerifier.Builder(new NetHttpTransport(), new GsonFactory()).build();
            logger.warn("Created dummy verifier that will reject all tokens - Google OAuth will not work");
        }
    }
}
