/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.authentication.config.controllers;

import java.time.Instant;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientService;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.OAuth2AccessToken;
import org.springframework.security.oauth2.core.OAuth2AccessToken.TokenType;

public class TestOAuth2AuthorizedClientService implements OAuth2AuthorizedClientService {

  @Override
  public OAuth2AuthorizedClient loadAuthorizedClient(
      final String clientRegistrationId, final String principalName) {
    return new OAuth2AuthorizedClient(
        ClientRegistration.withRegistrationId(clientRegistrationId)
            .clientId(clientRegistrationId)
            .authorizationGrantType(AuthorizationGrantType.CLIENT_CREDENTIALS)
            .tokenUri("https://example.com/oauth/token")
            .build(),
        principalName,
        new OAuth2AccessToken(
            TokenType.BEARER, "test-access-token", Instant.now(), Instant.now().plusSeconds(600)));
  }

  @Override
  public void saveAuthorizedClient(
      final OAuth2AuthorizedClient authorizedClient, final Authentication principal) {}

  @Override
  public void removeAuthorizedClient(
      final String clientRegistrationId, final String principalName) {}
}
