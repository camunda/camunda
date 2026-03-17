/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.gatekeeper.spring.unit.oidc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.camunda.gatekeeper.config.OidcConfig;
import io.camunda.gatekeeper.spring.oidc.ClientRegistrationFactory;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;

final class ClientRegistrationFactoryTest {

  @Test
  void shouldCreateClientRegistrationWithBasicFields() {
    // given
    final var config = createOidcConfig("my-client", "secret", "http://token-uri");

    // when
    final var registration = ClientRegistrationFactory.createClientRegistration("test-reg", config);

    // then
    assertThat(registration.getRegistrationId()).isEqualTo("test-reg");
    assertThat(registration.getClientId()).isEqualTo("my-client");
    assertThat(registration.getClientSecret()).isEqualTo("secret");
    assertThat(registration.getProviderDetails().getTokenUri()).isEqualTo("http://token-uri");
  }

  @Test
  void shouldUseDefaultRedirectUriWhenNoneProvided() {
    // given
    final var config = createOidcConfig("my-client", "secret", "http://token-uri");

    // when
    final var registration = ClientRegistrationFactory.createClientRegistration("test-reg", config);

    // then
    assertThat(registration.getRedirectUri()).isEqualTo("{baseUrl}/sso-callback");
  }

  @Test
  void shouldUseCustomRedirectUriWhenProvided() {
    // given
    final var config =
        new OidcConfig(
            null,
            "my-client",
            "secret",
            null,
            List.of(),
            "http://auth-uri",
            "http://token-uri",
            null,
            null,
            "sub",
            null,
            null,
            false,
            null,
            List.of(),
            "http://localhost/callback",
            Duration.ofSeconds(60),
            true,
            "authorization_code",
            "client_secret_basic",
            null,
            null);

    // when
    final var registration = ClientRegistrationFactory.createClientRegistration("test-reg", config);

    // then
    assertThat(registration.getRedirectUri()).isEqualTo("http://localhost/callback");
  }

  @Test
  void shouldSetGrantTypeAndAuthMethod() {
    // given
    final var config = createOidcConfig("my-client", "secret", "http://token-uri");

    // when
    final var registration = ClientRegistrationFactory.createClientRegistration("test-reg", config);

    // then
    assertThat(registration.getAuthorizationGrantType())
        .isEqualTo(AuthorizationGrantType.AUTHORIZATION_CODE);
    assertThat(registration.getClientAuthenticationMethod())
        .isEqualTo(ClientAuthenticationMethod.CLIENT_SECRET_BASIC);
  }

  @Test
  void shouldRejectUnsupportedClientAuthenticationMethod() {
    // given
    final var config =
        new OidcConfig(
            null,
            "my-client",
            "secret",
            null,
            List.of(),
            "http://auth-uri",
            "http://token-uri",
            null,
            null,
            "sub",
            null,
            null,
            false,
            null,
            List.of(),
            null,
            Duration.ofSeconds(60),
            true,
            "authorization_code",
            "unsupported_method",
            null,
            null);

    // when / then
    assertThatThrownBy(() -> ClientRegistrationFactory.createClientRegistration("test-reg", config))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("unsupported client authentication method");
  }

  private OidcConfig createOidcConfig(
      final String clientId, final String clientSecret, final String tokenUri) {
    return new OidcConfig(
        null,
        clientId,
        clientSecret,
        null,
        List.of(),
        "http://auth-uri",
        tokenUri,
        null,
        null,
        "sub",
        null,
        null,
        false,
        null,
        List.of(),
        null,
        Duration.ofSeconds(60),
        true,
        "authorization_code",
        "client_secret_basic",
        null,
        null);
  }
}
