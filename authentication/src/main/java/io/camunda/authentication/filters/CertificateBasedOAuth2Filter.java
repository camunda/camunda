/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.authentication.filters;

import io.camunda.authentication.config.CertificateClientAssertionService;
import io.camunda.authentication.oauth.ClientAssertionConstants;
import io.camunda.authentication.oauth.TokenResponse;
import io.camunda.security.configuration.OidcAuthenticationConfiguration;
import io.camunda.security.configuration.SecurityConfiguration;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.filter.OncePerRequestFilter;

public class CertificateBasedOAuth2Filter extends OncePerRequestFilter {

  private static final Logger LOG = LoggerFactory.getLogger(CertificateBasedOAuth2Filter.class);

  private final SecurityConfiguration securityConfig;
  private final CertificateClientAssertionService clientAssertionService;
  private final RestTemplate restTemplate;
  private final JwtDecoder jwtDecoder;

  public CertificateBasedOAuth2Filter(
      final SecurityConfiguration securityConfig,
      final CertificateClientAssertionService clientAssertionService,
      final RestTemplate restTemplate,
      final JwtDecoder jwtDecoder) {
    this.securityConfig = securityConfig;
    this.clientAssertionService = clientAssertionService;
    this.restTemplate = restTemplate;
    this.jwtDecoder = jwtDecoder;
  }

  @Override
  protected void doFilterInternal(
      final HttpServletRequest request,
      final HttpServletResponse response,
      final FilterChain filterChain)
      throws ServletException, IOException {

    LOG.debug("CertificateBasedOAuth2Filter processing: {}", request.getRequestURI());

    final boolean certificateAuthEnabled = isCertificateAuthenticationEnabled();

    // Check if we need to restore authentication from session
    Authentication currentAuth = SecurityContextHolder.getContext().getAuthentication();
    if (currentAuth == null || !currentAuth.isAuthenticated()) {
      final Authentication storedAuth = getAuthenticationFromSession(request);
      if (storedAuth != null) {
        SecurityContextHolder.getContext().setAuthentication(storedAuth);
        currentAuth = storedAuth;
        LOG.info(
            "Restored authentication from session for: {} - Principal: {}",
            request.getRequestURI(),
            storedAuth.getName());
      }
    }

    final boolean needsAuth = needsAuthentication();

    LOG.info(
        "CertificateBasedOAuth2Filter - certificateAuthEnabled: {}, needsAuth: {}, for: {}",
        certificateAuthEnabled,
        needsAuth,
        request.getRequestURI());

    // For certificate-based authentication with client credentials, always perform authentication
    // on protected endpoints when certificate auth is enabled, regardless of existing auth state
    if (certificateAuthEnabled && (needsAuth || !hasValidJwtAuthentication())) {
      try {
        LOG.info("Performing OAuth2 client credentials flow for: {}", request.getRequestURI());
        final Authentication authentication = performOAuth2ClientCredentialsFlow(request);

        // Set authentication in SecurityContext
        SecurityContextHolder.getContext().setAuthentication(authentication);

        // Store authentication in session to survive thread switches
        storeAuthenticationInSession(authentication, request);

        LOG.info(
            "Successfully authenticated certificate-based request for: {} - Auth type: {}, Principal: {}, Authenticated: {}",
            request.getRequestURI(),
            authentication.getClass().getSimpleName(),
            authentication.getName(),
            authentication.isAuthenticated());
      } catch (final Exception e) {
        LOG.error(
            "Failed to authenticate certificate-based request for: " + request.getRequestURI(), e);
        response.sendError(
            HttpServletResponse.SC_UNAUTHORIZED,
            "Certificate authentication failed: " + e.getMessage());
        return;
      }
    } else {
      LOG.info(
          "Skipping OAuth2 authentication for: {} (certificateAuthEnabled: {}, needsAuth: {}, hasValidJwt: {})",
          request.getRequestURI(),
          certificateAuthEnabled,
          needsAuth,
          hasValidJwtAuthentication());
    }

    // Log the final authentication state before passing to next filter
    final Authentication finalAuth = SecurityContextHolder.getContext().getAuthentication();
    LOG.info(
        "CertificateBasedOAuth2Filter - Final auth state for {}: Type: {}, Principal: {}, Authenticated: {}, Authorities: {}",
        request.getRequestURI(),
        finalAuth != null ? finalAuth.getClass().getSimpleName() : "null",
        finalAuth != null ? finalAuth.getName() : "null",
        finalAuth != null ? finalAuth.isAuthenticated() : false,
        finalAuth != null ? finalAuth.getAuthorities() : "null");

    LOG.info(
        "CertificateBasedOAuth2Filter - Thread: {}, SecurityContext: {}",
        Thread.currentThread().getName(),
        SecurityContextHolder.getContext().hashCode());

    // Pass the original request - authentication is already stored in SecurityContext and session
    filterChain.doFilter(request, response);

    // Log authentication state after filter chain execution
    final Authentication postChainAuth = SecurityContextHolder.getContext().getAuthentication();
    LOG.info(
        "CertificateBasedOAuth2Filter - AFTER filter chain for {}: Type: {}, Principal: {}, Authenticated: {}, Thread: {}",
        request.getRequestURI(),
        postChainAuth != null ? postChainAuth.getClass().getSimpleName() : "null",
        postChainAuth != null ? postChainAuth.getName() : "null",
        postChainAuth != null ? postChainAuth.isAuthenticated() : false,
        Thread.currentThread().getName());
  }

  private boolean needsAuthentication() {
    final Authentication auth = SecurityContextHolder.getContext().getAuthentication();
    return auth == null || !auth.isAuthenticated();
  }

  private boolean isCertificateAuthenticationEnabled() {
    final var oidcConfig = getOidcConfig();
    return oidcConfig.isClientAssertionEnabled()
        && ClientAssertionConstants.CLIENT_ASSERTION_GRANT_TYPE.equals(oidcConfig.getGrantType());
  }

  private OidcAuthenticationConfiguration getOidcConfig() {
    return securityConfig.getAuthentication().getOidc();
  }

  private String determineScope(final OidcAuthenticationConfiguration oidcConfig) {
    final var configuredScopes = oidcConfig.getScope();
    if (configuredScopes != null && !configuredScopes.isEmpty()) {
      final String scopeToUse = String.join(" ", configuredScopes);
      LOG.info("Using configured scopes: {}", scopeToUse);
      return scopeToUse;
    } else {
      // Fallback to clientId/.default for MS Entra
      final String clientId = oidcConfig.getClientId();
      final String scopeToUse = clientId + "/.default";
      LOG.info("Using fallback scope for MS Entra client credentials: {}", scopeToUse);
      return scopeToUse;
    }
  }

  private boolean hasValidJwtAuthentication() {
    final Authentication auth = SecurityContextHolder.getContext().getAuthentication();
    return auth instanceof JwtAuthenticationToken && auth.isAuthenticated();
  }

  /** Store authentication in session to survive thread switches */
  private void storeAuthenticationInSession(
      final Authentication auth, final HttpServletRequest request) {
    try {
      request.getSession(true).setAttribute(ClientAssertionConstants.SESSION_KEY, auth);

      LOG.info(
          "Stored authentication in session: {} - Principal: {}, Thread: {}",
          auth.getClass().getSimpleName(),
          auth.getName(),
          Thread.currentThread().getName());
    } catch (final Exception e) {
      LOG.warn("Failed to store authentication in session", e);
    }
  }

  /** Retrieve authentication from session if SecurityContext is empty */
  private Authentication getAuthenticationFromSession(final HttpServletRequest request) {
    try {
      final var session = request.getSession(false);
      if (session != null) {
        return (Authentication) session.getAttribute(ClientAssertionConstants.SESSION_KEY);
      }
    } catch (final Exception e) {
      LOG.warn("Failed to retrieve authentication from session", e);
    }
    return null;
  }

  private Authentication performOAuth2ClientCredentialsFlow(final HttpServletRequest request)
      throws Exception {
    // Create client assertion JWT using certificate
    final var oidcConfig = getOidcConfig();
    final String tokenEndpoint = oidcConfig.getTokenUri();
    final String clientAssertion =
        clientAssertionService.createClientAssertion(oidcConfig, tokenEndpoint);

    LOG.info("Created client assertion for token endpoint: {}", tokenEndpoint);

    // Perform OAuth2 client credentials flow
    final MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
    params.add(
        ClientAssertionConstants.CLIENT_ASSERTION_GRANT_TYPE_PARAM,
        ClientAssertionConstants.CLIENT_ASSERTION_GRANT_TYPE);
    params.add(
        ClientAssertionConstants.CLIENT_ASSERTION_TYPE_PARAM,
        ClientAssertionConstants.CLIENT_ASSERTION_TYPE_JWT_BEARER);
    params.add(ClientAssertionConstants.CLIENT_ASSERTION_PARAM, clientAssertion);

    // For MS Entra client credentials, use configured scope or fallback to clientId/.default
    final String scopeToUse = determineScope(oidcConfig);
    params.add("scope", scopeToUse);

    final HttpHeaders headers = new HttpHeaders();
    headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);

    final HttpEntity<MultiValueMap<String, String>> entity = new HttpEntity<>(params, headers);

    LOG.info("Making OAuth2 token request to: {}", tokenEndpoint);
    final ResponseEntity<TokenResponse> tokenResponse =
        restTemplate.postForEntity(tokenEndpoint, entity, TokenResponse.class);

    if (!tokenResponse.getStatusCode().is2xxSuccessful() || tokenResponse.getBody() == null) {
      throw new RuntimeException("Failed to obtain access token from MS Entra");
    }

    final String accessToken = tokenResponse.getBody().getAccessToken();
    LOG.info("Successfully obtained access token");

    // Decode and validate the JWT
    final Jwt jwt = jwtDecoder.decode(accessToken);

    // Create JWT authentication token with role extraction from JWT
    final List<SimpleGrantedAuthority> authorities = extractAuthoritiesFromJwt(jwt);

    return new JwtAuthenticationToken(jwt, authorities);
  }

  private List<SimpleGrantedAuthority> extractAuthoritiesFromJwt(final Jwt jwt) {
    final List<SimpleGrantedAuthority> authorities = new ArrayList<>();

    // Try to extract roles from standard JWT claims
    final Object rolesClaim = jwt.getClaim("roles");
    if (rolesClaim instanceof List<?> roles) {
      for (final Object role : roles) {
        if (role instanceof String roleStr && !roleStr.isEmpty()) {
          authorities.add(
              new SimpleGrantedAuthority(
                  roleStr.startsWith("ROLE_") ? roleStr : "ROLE_" + roleStr));
        }
      }
    }

    // Try alternative claim names
    if (authorities.isEmpty()) {
      final Object authoritiesClaim = jwt.getClaim("authorities");
      if (authoritiesClaim instanceof List<?> auths) {
        for (final Object auth : auths) {
          if (auth instanceof String authStr && !authStr.isEmpty()) {
            authorities.add(
                new SimpleGrantedAuthority(
                    authStr.startsWith("ROLE_") ? authStr : "ROLE_" + authStr));
          }
        }
      }
    }

    // Fallback to standard user role if no roles found in JWT
    if (authorities.isEmpty()) {
      authorities.add(new SimpleGrantedAuthority(ClientAssertionConstants.CERT_USER_ROLE));
    }

    return authorities;
  }
}
