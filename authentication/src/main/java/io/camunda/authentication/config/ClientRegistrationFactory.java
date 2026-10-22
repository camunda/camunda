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

  /** Spring's issuer-mismatch message reads {@code <prefix><declared><suffix><requested>"}. */
  private static final String MISMATCH_PREFIX = "The Issuer \"";

  private static final String MISMATCH_SUFFIX =
      "\" provided in the configuration metadata did not match the requested issuer \"";

  private ClientRegistrationFactory() {}

  /**
   * The issuer with its trailing slash removed, or with one added: {@code https://idp/v2.0/} and
   * {@code https://idp/v2.0} give each other. Identity providers are inconsistent here (Entra ID's
   * discovery document declares {@code .../v2.0}, Okta's has no trailing slash, Auth0's has one),
   * and operators copy either form into {@code issuer-uri}.
   *
   * @return the other form, or {@code null} for a value that has no path to trim or is otherwise
   *     not a plain issuer URL
   */
  static String withToggledTrailingSlash(final String issuer) {
    if (issuer == null || issuer.isBlank() || issuer.contains("?") || issuer.contains("#")) {
      return null;
    }
    if (!issuer.endsWith("/")) {
      return issuer + "/";
    }
    final var trimmed = issuer.substring(0, issuer.length() - 1);
    // Never trim into an empty or scheme-only authority, such as "https:/".
    return trimmed.endsWith("/") || !trimmed.contains("://") || trimmed.endsWith(":/")
        ? null
        : trimmed;
  }

  /**
   * The issuer the discovery document declared, read from Spring's mismatch message ({@code The
   * Issuer "<declared>" provided in the configuration metadata did not match the requested issuer
   * "<requested>"}), as {@code fromIssuerLocation} does not expose the document. {@code null} if
   * the message has another form, which makes {@link #discover} rethrow: a Spring upgrade that
   * changes the message fails safe, and the tests of the trailing-slash retry catch it.
   */
  static String declaredIssuer(final IllegalStateException issuerMismatch) {
    final var message = issuerMismatch.getMessage();
    if (message == null) {
      return null;
    }
    if (!message.startsWith(MISMATCH_PREFIX)) {
      return null;
    }
    final var end = message.indexOf(MISMATCH_SUFFIX, MISMATCH_PREFIX.length());
    return end < 0 ? null : message.substring(MISMATCH_PREFIX.length(), end);
  }

  /**
   * Discovery from {@code issuer-uri}, tolerating a trailing slash the identity provider's document
   * doesn't have (or has where the configuration doesn't). See camunda/camunda#61896.
   *
   * <p>{@code fromIssuerLocation} requires the document's {@code issuer} to equal the configured
   * string exactly, and throws {@link IllegalStateException} when it doesn't — the one failure it
   * reports with that type; an unreachable or unparseable document is an {@link
   * IllegalArgumentException}. When the two differ only by a trailing slash, discovery is retried
   * with the other form, and the registration takes the provider's issuer. That is what the
   * provider's tokens carry in {@code iss}, and what {@link IssuerAwareTokenValidator} and {@link
   * IssuerAwareJWSKeySelector} compare it with. Any other mismatch, or a retry that fails too,
   * rethrows the original exception.
   */
  private static Builder discover(final String issuerUri) {
    try {
      return ClientRegistrations.fromIssuerLocation(issuerUri);
    } catch (final IllegalStateException issuerMismatch) {
      final var alternative = withToggledTrailingSlash(issuerUri);
      // Retry only if the document declared exactly the other slash form. Any other mismatch is a
      // real misconfiguration, and the retry could otherwise reach a different document that
      // happens to declare the alternative.
      if (alternative == null || !alternative.equals(declaredIssuer(issuerMismatch))) {
        throw issuerMismatch;
      }
      final Builder builder;
      try {
        builder = ClientRegistrations.fromIssuerLocation(alternative);
      } catch (final RuntimeException retryFailure) {
        issuerMismatch.addSuppressed(retryFailure);
        throw issuerMismatch;
      }
      LOGGER.warn(
          "The configured issuer-uri '{}' differs from the issuer '{}' that the identity provider's"
              + " discovery document declares only by a trailing slash. Using '{}', which is what"
              + " its tokens carry; set issuer-uri to it to remove this warning.",
          issuerUri,
          alternative,
          alternative);
      return builder;
    }
  }

  public static ClientRegistration createClientRegistration(
      final String registrationId, final OidcAuthenticationConfiguration configuration) {
    final Builder builder;
    if (configuration.getIssuerUri() != null) {
      builder = discover(configuration.getIssuerUri()).registrationId(registrationId);
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
