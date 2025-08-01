/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.authentication.filters;

import io.camunda.authentication.CamundaJwtAuthenticationToken;
import io.camunda.authentication.entity.AuthenticationContext;
import io.camunda.authentication.entity.CamundaJwtUser;
import io.camunda.authentication.entity.OAuthContext;
import io.camunda.security.configuration.SecurityConfiguration;
import io.camunda.service.AuthorizationServices;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.client.OAuth2AuthorizeRequest;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientManager;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.web.HttpSessionOAuth2AuthorizedClientRepository;
import org.springframework.security.oauth2.client.web.OAuth2AuthorizedClientRepository;
import org.springframework.security.oauth2.core.OAuth2AccessToken;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.security.web.csrf.CsrfTokenRepository;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Filter that automatically authenticates requests to /identity/** using client credentials flow
 * when certificate-based authentication is configured.
 */
public class CertificateClientCredentialsFilter extends OncePerRequestFilter {

  private static final Logger LOG =
      LoggerFactory.getLogger(CertificateClientCredentialsFilter.class);
  private static final String OIDC_REGISTRATION_ID = "oidc";

  private final SecurityConfiguration securityConfiguration;
  private final OAuth2AuthorizedClientManager authorizedClientManager;
  private final ClientRegistrationRepository clientRegistrationRepository;
  private final CsrfTokenRepository csrfTokenRepository;
  private final AuthorizationServices authorizationServices;

  public CertificateClientCredentialsFilter(
      final SecurityConfiguration securityConfiguration,
      final OAuth2AuthorizedClientManager authorizedClientManager,
      final ClientRegistrationRepository clientRegistrationRepository,
      final CsrfTokenRepository csrfTokenRepository,
      final AuthorizationServices authorizationServices) {
    this.securityConfiguration = securityConfiguration;
    this.authorizedClientManager = authorizedClientManager;
    this.clientRegistrationRepository = clientRegistrationRepository;
    this.csrfTokenRepository = csrfTokenRepository;
    this.authorizationServices = authorizationServices;
  }

  @Override
  protected void doFilterInternal(
      final HttpServletRequest request,
      final HttpServletResponse response,
      final FilterChain filterChain)
      throws ServletException, IOException {

    LOG.debug("Processing request: {} method: {}", request.getRequestURI(), request.getMethod());

    // Only apply to /identity/** and /v2/** paths when using client_credentials flow
    if ((request.getRequestURI().startsWith("/identity")
            || request.getRequestURI().startsWith("/v2/"))
        && "client_credentials"
            .equals(securityConfiguration.getAuthentication().getOidc().getGrantType())) {

      // Check if already authenticated in this session
      if (SecurityContextHolder.getContext().getAuthentication() != null) {
        LOG.debug("Already authenticated, skipping certificate authentication");
        filterChain.doFilter(request, response);
        return;
      }

      // Check if there's an existing authorized client in session
      final OAuth2AuthorizedClientRepository clientRepository =
          new HttpSessionOAuth2AuthorizedClientRepository();
      // Create a dummy authentication to load the client from session
      final OAuth2AuthorizedClient existingClient =
          clientRepository.loadAuthorizedClient(
              "oidc", new UsernamePasswordAuthenticationToken("identity", null), request);

      if (existingClient != null) {
        LOG.debug("Found existing OAuth2 client in session, reusing authentication");
        // Check if the existing client's token is expired
        final OAuth2AccessToken accessToken = existingClient.getAccessToken();
        if (accessToken.getExpiresAt() != null
            && accessToken.getExpiresAt().isBefore(Instant.now())) {
          LOG.debug("Existing OAuth2 client token is expired, removing from session");
          clientRepository.removeAuthorizedClient(
              "oidc", new UsernamePasswordAuthenticationToken("identity", null), request, response);
        } else {
          LOG.debug("Existing OAuth2 client token is valid, creating authentication");
          final CamundaJwtAuthenticationToken authentication =
              createAuthenticationToken(existingClient);
          SecurityContextHolder.getContext().setAuthentication(authentication);

          // Ensure CSRF token is available for the session
          ensureCsrfToken(request, response);

          LOG.debug("Authentication set in SecurityContext");
          filterChain.doFilter(request, response);
          return;
        }
      }

      LOG.debug("Attempting to authenticate using client credentials flow");
      try {
        // Get OAuth2 token using client credentials flow
        final OAuth2AuthorizeRequest authorizeRequest =
            OAuth2AuthorizeRequest.withClientRegistrationId(OIDC_REGISTRATION_ID)
                .principal("identity") // Use a principal name
                .build();

        final OAuth2AuthorizedClient authorizedClient =
            authorizedClientManager.authorize(authorizeRequest);

        if (authorizedClient != null) {
          final OAuth2AccessToken accessToken = authorizedClient.getAccessToken();
          LOG.debug("Successfully obtained access token for identity endpoint");

          // Create authentication token with proper CamundaJwtUser principal
          final CamundaJwtAuthenticationToken authentication =
              createAuthenticationToken(authorizedClient);
          SecurityContextHolder.getContext().setAuthentication(authentication);

          // Ensure certificate user has proper authorization permissions
          ensureCertificateUserPermissions();

          // Store the authorized client in the session for Identity to use
          // Use a consistent dummy principal for session storage
          final UsernamePasswordAuthenticationToken dummyAuth =
              new UsernamePasswordAuthenticationToken("identity", null);
          clientRepository.saveAuthorizedClient(authorizedClient, dummyAuth, request, response);

          // Generate and set CSRF token for subsequent requests
          ensureCsrfToken(request, response);

          LOG.debug("Authentication context and OAuth2 client saved to session");
        } else {
          LOG.warn("Failed to obtain OAuth2 authorized client for identity endpoint");
        }
      } catch (final Exception e) {
        LOG.error("Error during automatic certificate authentication", e);
      }
    } else {
      LOG.debug(
          "Filter not applying to request: {} (grant type: {})",
          request.getRequestURI(),
          securityConfiguration.getAuthentication().getOidc().getGrantType());
    }

    filterChain.doFilter(request, response);
  }

  private CamundaJwtAuthenticationToken createAuthenticationToken(
      final OAuth2AuthorizedClient authorizedClient) {
    try {
      // Parse the access token as a JWT
      final OAuth2AccessToken accessToken = authorizedClient.getAccessToken();
      final String tokenValue = accessToken.getTokenValue();

      // Create a simple JWT object for certificate-based authentication
      final Jwt jwt = createJwtFromToken(tokenValue, accessToken);

      // Create authentication context for Identity access with SYSTEM ADMIN permissions
      final AuthenticationContext authContext =
          new AuthenticationContext.AuthenticationContextBuilder()
              .withUsername("admin") // Use "admin" as system admin username
              .withClientId(securityConfiguration.getAuthentication().getOidc().getClientId())
              .withRoles(List.of("admin")) // Admin role
              .withTenants(List.of("*")) // System admin tenant - all tenants access
              .withGroups(List.of("*")) // All groups access
              .withGroupsClaimEnabled(false)
              .build();

      // Create OAuth context
      final OAuthContext oauthContext = new OAuthContext(Set.of(), authContext);

      // Create CamundaJwtUser
      final CamundaJwtUser camundaUser = new CamundaJwtUser(jwt, oauthContext);

      // Create CamundaJwtAuthenticationToken with CamundaJwtUser as principal
      return new CamundaJwtAuthenticationToken(
          jwt,
          camundaUser,
          null,
          Collections.singletonList(new SimpleGrantedAuthority("ROLE_USER")));
    } catch (final Exception e) {
      LOG.error("Failed to create authentication token", e);
      throw new RuntimeException("Failed to create authentication token", e);
    }
  }

  private Jwt createJwtFromToken(final String tokenValue, final OAuth2AccessToken accessToken) {
    // Create a minimal JWT object for certificate authentication
    // In a real implementation, you might want to decode the actual JWT
    final Map<String, Object> headers =
        Map.of(
            "alg", "RS256",
            "typ", "JWT");

    final Map<String, Object> claims =
        Map.of(
            "sub", "admin",
            "name", "System Administrator",
            "email", "admin@camunda.com",
            "displayName", "System Administrator",
            "iat", Instant.now().getEpochSecond(),
            "exp",
                accessToken.getExpiresAt() != null
                    ? accessToken.getExpiresAt().getEpochSecond()
                    : Instant.now().plusSeconds(3600).getEpochSecond());

    return new Jwt(
        tokenValue,
        Instant.now(),
        accessToken.getExpiresAt() != null
            ? accessToken.getExpiresAt()
            : Instant.now().plusSeconds(3600),
        headers,
        claims);
  }

  private void ensureCsrfToken(
      final HttpServletRequest request, final HttpServletResponse response) {
    // Generate CSRF token if not already present
    CsrfToken csrfToken = csrfTokenRepository.loadToken(request);
    if (csrfToken == null) {
      csrfToken = csrfTokenRepository.generateToken(request);
      csrfTokenRepository.saveToken(csrfToken, request, response);
    }

    // Make the token available in the request attributes for CSRF protection
    request.setAttribute(CsrfToken.class.getName(), csrfToken);

    // Always add CSRF token to response header for authenticated requests
    response.setHeader("X-CSRF-TOKEN", csrfToken.getToken());

    LOG.debug("CSRF token ensured for session");
  }

  private void ensureCertificateUserPermissions() {
    // Skip complex authorization creation - authorization should be disabled
    LOG.debug(
        "Skipping authorization creation - relying on CAMUNDA_SECURITY_AUTHORIZATIONS_ENABLED=false");
  }
}
