/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.authentication.config;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.security.spring.oidc.AssertionJwkProvider;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.oauth2.client.endpoint.OAuth2AuthorizationCodeGrantRequest;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationExchange;
import org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationRequest;
import org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationResponse;
import org.springframework.security.oauth2.core.endpoint.OAuth2ParameterNames;
import org.springframework.web.client.RestClient;

@ExtendWith(MockitoExtension.class)
class OidcTokenEndpointCustomizerTest {

  @Mock private AssertionJwkProvider assertionJwkProvider;

  @Test
  void shouldCopyResourceFromAuthorizationRequest() {
    // given
    final var customizer =
        new OidcTokenEndpointCustomizer(assertionJwkProvider, RestClient.create());
    final var request = grantRequest(List.of("resource-a", "resource-b"));

    // when
    final var parameters = customizer.createResourceParameterConverter().convert(request);

    // then
    assertThat(parameters)
        .isNotNull()
        .containsEntry(OAuth2ParameterNames.RESOURCE, List.of("resource-a", "resource-b"));
  }

  @Test
  void shouldCopySingleResourceFromAuthorizationRequest() {
    // given
    final var customizer =
        new OidcTokenEndpointCustomizer(assertionJwkProvider, RestClient.create());
    final var request = grantRequest("resource-a");

    // when
    final var parameters = customizer.createResourceParameterConverter().convert(request);

    // then
    assertThat(parameters)
        .isNotNull()
        .containsEntry(OAuth2ParameterNames.RESOURCE, List.of("resource-a"));
  }

  @Test
  void shouldNotAddResourceWhenAuthorizationRequestHasNone() {
    // given
    final var customizer =
        new OidcTokenEndpointCustomizer(assertionJwkProvider, RestClient.create());
    final var request = grantRequest(null);

    // when
    final var parameters = customizer.createResourceParameterConverter().convert(request);

    // then
    assertThat(parameters).isNull();
  }

  private static OAuth2AuthorizationCodeGrantRequest grantRequest(final Object resources) {
    final var registration =
        ClientRegistration.withRegistrationId("custom")
            .clientId("client")
            .clientSecret("secret")
            .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
            .redirectUri("https://example.com/callback")
            .authorizationUri("https://idp.example.com/authorize")
            .tokenUri("https://idp.example.com/token")
            .build();
    final var requestBuilder =
        OAuth2AuthorizationRequest.authorizationCode()
            .authorizationUri("https://idp.example.com/authorize")
            .clientId("client")
            .redirectUri("https://example.com/callback")
            .state("state");
    if (resources != null) {
      requestBuilder.additionalParameters(Map.of(OAuth2ParameterNames.RESOURCE, resources));
    }
    final var authorizationRequest = requestBuilder.build();
    final var authorizationResponse =
        OAuth2AuthorizationResponse.success("code")
            .redirectUri("https://example.com/callback")
            .state("state")
            .build();
    return new OAuth2AuthorizationCodeGrantRequest(
        registration, new OAuth2AuthorizationExchange(authorizationRequest, authorizationResponse));
  }
}
