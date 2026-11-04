/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.authentication.config;

import static io.camunda.security.configuration.OidcAuthenticationConfiguration.CLIENT_AUTHENTICATION_METHODS;

import io.camunda.security.configuration.OidcAuthenticationConfiguration;
import java.util.HashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.ClientRegistration.Builder;
import org.springframework.security.oauth2.client.registration.ClientRegistrations;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;

public final class ClientRegistrationFactory {
  private static final Logger LOGGER = LoggerFactory.getLogger(ClientRegistrationFactory.class);

  private static final String DEFAULT_REDIRECT_URI = "{baseUrl}" + WebSecurityConfig.REDIRECT_URI;

  /** Discovery metadata keys, per RFC 8414 §2 and OpenID Connect Session Management. */
  private static final String END_SESSION_ENDPOINT_METADATA_KEY = "end_session_endpoint";

  private static final String REVOCATION_ENDPOINT_METADATA_KEY = "revocation_endpoint";

  private ClientRegistrationFactory() {}

  public static ClientRegistration createClientRegistration(
      final String registrationId, final OidcAuthenticationConfiguration configuration) {
    final Builder builder;
    if (configuration.getIssuerUri() != null) {
      builder =
          ClientRegistrations.fromIssuerLocation(configuration.getIssuerUri())
              .registrationId(registrationId);
    } else {
      builder = ClientRegistration.withRegistrationId(registrationId);
    }

    if (configuration.getClientName() != null) {
      builder.clientName(configuration.getClientName());
    }
    if (configuration.getClientId() != null) {
      builder.clientId(configuration.getClientId());
    }
    if (configuration.getClientSecret() != null) {
      builder.clientSecret(configuration.getClientSecret());
    }

    final var redirectUri = configuration.getRedirectUri();
    if (redirectUri == null || redirectUri.isBlank()) {
      builder.redirectUri(DEFAULT_REDIRECT_URI);
    } else {
      builder.redirectUri(redirectUri);
    }

    if (configuration.getAuthorizationUri() != null) {
      builder.authorizationUri(configuration.getAuthorizationUri());
    }
    if (configuration.getTokenUri() != null) {
      builder.tokenUri(configuration.getTokenUri());
    }
    if (configuration.getJwkSetUri() != null) {
      builder.jwkSetUri(configuration.getJwkSetUri());
    }
    if (configuration.getScope() != null) {
      builder.scope(configuration.getScope());
    }
    if (configuration.getGrantType() != null) {
      builder.authorizationGrantType(new AuthorizationGrantType(configuration.getGrantType()));
    }
    if (configuration.getClientAuthenticationMethod() != null) {
      requireSupportedClientAuthenticationMethod(configuration.getClientAuthenticationMethod());
      builder.clientAuthenticationMethod(
          ClientAuthenticationMethod.valueOf(configuration.getClientAuthenticationMethod()));
    }

    if (!configuration.isUserInfoEnabled()) {
      LOGGER.debug("Fetching user info is disabled for client registration {}", registrationId);
      builder.userInfoUri(null);
    }

    return mergeProviderMetadata(builder.build(), registrationId, configuration);
  }

  /**
   * Adds this registration's own endpoints to the provider metadata by build-then-rebuild, because
   * {@code providerConfigurationMetadata} replaces the map rather than merging into it: writing an
   * explicitly configured {@code end_session_endpoint} on the builder used to drop every endpoint
   * issuer discovery had found, {@code revocation_endpoint} among them.
   */
  private static ClientRegistration mergeProviderMetadata(
      final ClientRegistration built,
      final String registrationId,
      final OidcAuthenticationConfiguration configuration) {
    final Map<String, Object> discovered = built.getProviderDetails().getConfigurationMetadata();
    final Map<String, Object> merged = new HashMap<>(discovered);
    if (configuration.getEndSessionEndpointUri() != null) {
      merged.put(END_SESSION_ENDPOINT_METADATA_KEY, configuration.getEndSessionEndpointUri());
    }
    applyRevocationEndpoint(merged, registrationId, configuration);
    return merged.equals(discovered)
        ? built
        : ClientRegistration.withClientRegistration(built)
            .providerConfigurationMetadata(merged)
            .build();
  }

  /**
   * Writes, or removes, this registration's {@code revocation_endpoint}.
   *
   * <p>Removal is the default path, and it is why the key is stripped here rather than tested at
   * logout: the endpoint normally arrives from issuer discovery (Keycloak, Okta and Auth0 all
   * publish one), so without this every discovery-based deployment would gain refresh-token
   * revocation on upgrade, which is a logout behaviour change nobody asked for in a patch release.
   * 8.10 holds the same default through its own {@code OidcRevocationDefaultPostProcessor}, so an
   * 8.9 deployment does not lose revocation by upgrading. Stripping the key keeps {@link
   * RefreshTokenRevocationLogoutHandler} asking one question, and reuses the no-op path it already
   * has for a provider that publishes none.
   *
   * <p>An operator opts in per provider with {@code revocation-enabled=true}. Disabling still wins
   * over a configured URI: setting both is a contradiction, and the flag is the more specific
   * statement. It is logged, because silently ignoring a value someone configured on purpose is
   * otherwise an afternoon lost.
   */
  private static void applyRevocationEndpoint(
      final Map<String, Object> merged,
      final String registrationId,
      final OidcAuthenticationConfiguration configuration) {
    final var configuredUri = configuration.getRevocationEndpointUri();
    if (configuration.isRevocationEnabled()) {
      if (configuredUri != null && !configuredUri.isBlank()) {
        merged.put(REVOCATION_ENDPOINT_METADATA_KEY, configuredUri);
      }
      return;
    }
    if (configuredUri != null && !configuredUri.isBlank()) {
      LOGGER.warn(
          "OIDC registration '{}' sets both revocation-endpoint-uri and revocation-enabled=false;"
              + " the configured URI is ignored and no refresh token will be revoked. Remove one of"
              + " the two to make the intent explicit.",
          RefreshTokenRevocationLogoutHandler.sanitizeForLog(registrationId));
    }
    merged.remove(REVOCATION_ENDPOINT_METADATA_KEY);
  }

  private static void requireSupportedClientAuthenticationMethod(
      final String clientAuthenticationMethod) {
    if (!CLIENT_AUTHENTICATION_METHODS.contains(clientAuthenticationMethod)) {
      throw new IllegalArgumentException(
          "unsupported client authentication method: " + clientAuthenticationMethod);
    }
  }
}
