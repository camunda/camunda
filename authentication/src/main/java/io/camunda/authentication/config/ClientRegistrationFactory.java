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
    if (configuration.getRedirectUri() != null) {
      builder.redirectUri(configuration.getRedirectUri());
    }
    if (configuration.getAuthorizationUri() != null) {
      builder.authorizationUri(configuration.getAuthorizationUri());
    }
    if (configuration.getEndSessionEndpointUri() != null) {
      // end_session_endpoint should be located in the provider metadata
      // this will also override any other metadata provided by the builder previously
      // there is no way to get the current builder metadata or merge into it
      final Map<String, Object> metadata = new HashMap<>();
      metadata.put("end_session_endpoint", configuration.getEndSessionEndpointUri());
      builder.providerConfigurationMetadata(metadata);
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

    return builder.build();
  }

  private static void requireSupportedClientAuthenticationMethod(
      final String clientAuthenticationMethod) {
    if (!CLIENT_AUTHENTICATION_METHODS.contains(clientAuthenticationMethod)) {
      throw new IllegalArgumentException(
          "unsupported client authentication method: " + clientAuthenticationMethod);
    }
  }
}
