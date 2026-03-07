/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.auth.spring.oidc;

import io.camunda.auth.domain.config.OidcAuthenticationConfiguration;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.ClientRegistrations;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;

public final class ClientRegistrationFactory {

  private ClientRegistrationFactory() {}

  public static ClientRegistration createClientRegistration(
      final String registrationId, final OidcAuthenticationConfiguration config) {
    final ClientRegistration.Builder builder;

    if (config.getTokenUri() != null && config.getAuthorizationUri() != null) {
      builder = ClientRegistration.withRegistrationId(registrationId);
      builder.tokenUri(config.getTokenUri());
      builder.authorizationUri(config.getAuthorizationUri());
      if (config.getJwkSetUri() != null) {
        builder.jwkSetUri(config.getJwkSetUri());
      }
      if (config.getIssuerUri() != null) {
        builder.issuerUri(config.getIssuerUri());
      }
      if (config.getUsernameClaim() != null) {
        builder.userNameAttributeName(config.getUsernameClaim());
      }
    } else {
      builder =
          ClientRegistrations.fromIssuerLocation(config.getIssuerUri())
              .registrationId(registrationId);
    }

    builder
        .clientId(config.getClientId())
        .clientSecret(config.getClientSecret() != null ? config.getClientSecret() : "")
        .scope(
            config.getScope() != null
                ? config.getScope().toArray(String[]::new)
                : new String[] {"openid", "profile"})
        .authorizationGrantType(new AuthorizationGrantType(config.getGrantType()));

    if (config.getClientAuthenticationMethod() != null) {
      builder.clientAuthenticationMethod(
          new ClientAuthenticationMethod(config.getClientAuthenticationMethod()));
    }

    if (config.getRedirectUri() != null) {
      builder.redirectUri(config.getRedirectUri());
    }

    if (config.getClientName() != null) {
      builder.clientName(config.getClientName());
    }

    if (config.getEndSessionEndpointUri() != null) {
      builder.providerConfigurationMetadata(
          java.util.Map.of("end_session_endpoint", config.getEndSessionEndpointUri()));
    }

    return builder.build();
  }
}
