/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.gatekeeper.spring.oidc;

import io.camunda.gatekeeper.config.OidcConfig;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.ClientRegistration.Builder;
import org.springframework.security.oauth2.client.registration.ClientRegistrations;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;

/**
 * Factory for creating Spring Security {@link ClientRegistration} instances from gatekeeper {@link
 * OidcConfig} records.
 */
public final class ClientRegistrationFactory {

  private static final Logger LOG = LoggerFactory.getLogger(ClientRegistrationFactory.class);
  private static final String DEFAULT_REDIRECT_URI = "{baseUrl}/sso-callback";
  private static final List<String> SUPPORTED_CLIENT_AUTHENTICATION_METHODS =
      List.of("client_secret_basic", "private_key_jwt");

  private ClientRegistrationFactory() {}

  /**
   * Creates a {@link ClientRegistration} from the given registration ID and OIDC configuration.
   *
   * @param registrationId the registration ID
   * @param config the OIDC configuration
   * @return a configured {@link ClientRegistration}
   */
  public static ClientRegistration createClientRegistration(
      final String registrationId, final OidcConfig config) {
    final Builder builder;
    if (config.issuerUri() != null) {
      builder =
          ClientRegistrations.fromIssuerLocation(config.issuerUri()).registrationId(registrationId);
    } else {
      builder = ClientRegistration.withRegistrationId(registrationId);
    }

    if (config.clientId() != null) {
      builder.clientId(config.clientId());
    }
    if (config.clientSecret() != null) {
      builder.clientSecret(config.clientSecret());
    }

    final var redirectUri = config.redirectUri();
    if (redirectUri == null || redirectUri.isBlank()) {
      builder.redirectUri(DEFAULT_REDIRECT_URI);
    } else {
      builder.redirectUri(redirectUri);
    }

    if (config.authorizationUri() != null) {
      builder.authorizationUri(config.authorizationUri());
    }
    if (config.endSessionEndpointUri() != null) {
      final Map<String, Object> metadata = new HashMap<>();
      metadata.put("end_session_endpoint", config.endSessionEndpointUri());
      builder.providerConfigurationMetadata(metadata);
    }
    if (config.tokenUri() != null) {
      builder.tokenUri(config.tokenUri());
    }
    if (config.jwkSetUri() != null) {
      builder.jwkSetUri(config.jwkSetUri());
    }
    if (config.scope() != null) {
      builder.scope(config.scope());
    }
    if (config.grantType() != null) {
      builder.authorizationGrantType(new AuthorizationGrantType(config.grantType()));
    }
    if (config.clientAuthenticationMethod() != null) {
      requireSupportedClientAuthenticationMethod(config.clientAuthenticationMethod());
      builder.clientAuthenticationMethod(
          ClientAuthenticationMethod.valueOf(config.clientAuthenticationMethod()));
    }

    LOG.debug("Created client registration for '{}'", registrationId);
    return builder.build();
  }

  private static void requireSupportedClientAuthenticationMethod(
      final String clientAuthenticationMethod) {
    if (!SUPPORTED_CLIENT_AUTHENTICATION_METHODS.contains(clientAuthenticationMethod)) {
      throw new IllegalArgumentException(
          "unsupported client authentication method: " + clientAuthenticationMethod);
    }
  }
}
