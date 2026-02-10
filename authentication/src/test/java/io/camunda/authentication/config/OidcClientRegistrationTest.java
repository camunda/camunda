/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.authentication.config;

import io.camunda.security.configuration.OidcAuthenticationConfiguration;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.security.oauth2.client.registration.ClientRegistration;

class OidcClientRegistrationTest {

  @Test
  public void validAuthenticationMethodIsSet() {
    final var config = new OidcAuthenticationConfiguration();
    config.setClientId("clientId");
    config.setRedirectUri("redirectUri");
    config.setAuthorizationUri("authorizationUri");
    config.setTokenUri("tokenUri");
    config.setClientAuthenticationMethod("client_secret_basic");

    Assertions.assertThat(ClientRegistrationFactory.createClientRegistration("foo", config))
        .isInstanceOf(ClientRegistration.class);
  }

  @Test
  public void invalidAuthenticationMethodThrows() {
    final var config = new OidcAuthenticationConfiguration();
    config.setClientId("clientId");
    config.setRedirectUri("redirectUri");
    config.setAuthorizationUri("authorizationUri");
    config.setTokenUri("tokenUri");
    config.setClientAuthenticationMethod("does_not_exist");

    Assertions.assertThatThrownBy(
            () -> ClientRegistrationFactory.createClientRegistration("foo", config))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("unsupported client authentication method: does_not_exist");
  }

  @Test
  void shouldEnableUserInfoEndpoint() {
    // given
    final var config = new OidcAuthenticationConfiguration();
    final var builder =
        ClientRegistration.withRegistrationId("foo").userInfoUri("http://localhost:8080");
    config.setClientId("clientId");
    config.setRedirectUri("redirectUri");
    config.setAuthorizationUri("authorizationUri");
    config.setTokenUri("tokenUri");
    config.setClientAuthenticationMethod("client_secret_basic");
    config.setUserInfoEnabled(true);

    try (final var mockedRegistration = Mockito.mockStatic(ClientRegistration.class)) {
      mockedRegistration
          .when(() -> ClientRegistration.withRegistrationId(Mockito.anyString()))
          .thenReturn(builder);

      // when
      final var clientRegistration =
          ClientRegistrationFactory.createClientRegistration("foo", config);

      // then
      Assertions.assertThat(clientRegistration.getProviderDetails().getUserInfoEndpoint().getUri())
          .isEqualTo("http://localhost:8080");
    }
  }

  @Test
  void shouldDisableUserInfoEndpoint() {
    // given
    final var config = new OidcAuthenticationConfiguration();
    final var builder =
        ClientRegistration.withRegistrationId("foo").userInfoUri("http://localhost:8080");
    config.setClientId("clientId");
    config.setRedirectUri("redirectUri");
    config.setAuthorizationUri("authorizationUri");
    config.setTokenUri("tokenUri");
    config.setClientAuthenticationMethod("client_secret_basic");
    config.setUserInfoEnabled(false);

    try (final var mockedRegistration = Mockito.mockStatic(ClientRegistration.class)) {
      mockedRegistration
          .when(() -> ClientRegistration.withRegistrationId(Mockito.anyString()))
          .thenReturn(builder);

      // when
      final var clientRegistration =
          ClientRegistrationFactory.createClientRegistration("foo", config);

      // then
      Assertions.assertThat(clientRegistration.getProviderDetails().getUserInfoEndpoint().getUri())
          .isNull();
    }
  }
}
