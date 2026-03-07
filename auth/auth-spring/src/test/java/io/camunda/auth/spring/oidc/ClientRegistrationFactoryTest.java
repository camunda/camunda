/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.auth.spring.oidc;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.auth.domain.config.OidcAuthenticationConfiguration;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ClientRegistrationFactoryTest {

  @Test
  void shouldBuildRegistrationWithManualUris() {
    // given
    final var config = new OidcAuthenticationConfiguration();
    config.setClientId("my-client");
    config.setTokenUri("https://example.com/token");
    config.setAuthorizationUri("https://example.com/authorize");
    config.setGrantType("authorization_code");
    config.setRedirectUri("https://example.com/callback");
    config.setIssuerUri("https://issuer.example.com");
    config.setJwkSetUri("https://example.com/.well-known/jwks.json");

    // when
    final var registration = ClientRegistrationFactory.createClientRegistration("test-reg", config);

    // then
    assertThat(registration.getRegistrationId()).isEqualTo("test-reg");
    assertThat(registration.getClientId()).isEqualTo("my-client");
    assertThat(registration.getProviderDetails().getTokenUri())
        .isEqualTo("https://example.com/token");
    assertThat(registration.getProviderDetails().getAuthorizationUri())
        .isEqualTo("https://example.com/authorize");
    assertThat(registration.getProviderDetails().getIssuerUri())
        .isEqualTo("https://issuer.example.com");
    assertThat(registration.getProviderDetails().getJwkSetUri())
        .isEqualTo("https://example.com/.well-known/jwks.json");
  }

  @Test
  void shouldApplyDefaultScope() {
    // given
    final var config = new OidcAuthenticationConfiguration();
    config.setClientId("my-client");
    config.setTokenUri("https://example.com/token");
    config.setAuthorizationUri("https://example.com/authorize");
    config.setGrantType("authorization_code");
    config.setRedirectUri("https://example.com/callback");
    config.setScope(null);

    // when
    final var registration = ClientRegistrationFactory.createClientRegistration("test-reg", config);

    // then
    assertThat(registration.getScopes()).containsExactlyInAnyOrder("openid", "profile");
  }

  @Test
  void shouldApplyConfiguredScope() {
    // given
    final var config = new OidcAuthenticationConfiguration();
    config.setClientId("my-client");
    config.setTokenUri("https://example.com/token");
    config.setAuthorizationUri("https://example.com/authorize");
    config.setGrantType("authorization_code");
    config.setRedirectUri("https://example.com/callback");
    config.setScope(List.of("openid", "email", "groups"));

    // when
    final var registration = ClientRegistrationFactory.createClientRegistration("test-reg", config);

    // then
    assertThat(registration.getScopes()).containsExactlyInAnyOrder("openid", "email", "groups");
  }

  @Test
  void shouldSetOptionalFields() {
    // given
    final var config = new OidcAuthenticationConfiguration();
    config.setClientId("my-client");
    config.setClientSecret("my-secret");
    config.setTokenUri("https://example.com/token");
    config.setAuthorizationUri("https://example.com/authorize");
    config.setGrantType("authorization_code");
    config.setRedirectUri("https://example.com/callback");
    config.setClientName("My OIDC Provider");
    config.setClientAuthenticationMethod("client_secret_post");

    // when
    final var registration = ClientRegistrationFactory.createClientRegistration("test-reg", config);

    // then
    assertThat(registration.getRedirectUri()).isEqualTo("https://example.com/callback");
    assertThat(registration.getClientName()).isEqualTo("My OIDC Provider");
    assertThat(registration.getClientAuthenticationMethod().getValue())
        .isEqualTo("client_secret_post");
    assertThat(registration.getClientSecret()).isEqualTo("my-secret");
  }

  @Test
  void shouldSetEndSessionMetadata() {
    // given
    final var config = new OidcAuthenticationConfiguration();
    config.setClientId("my-client");
    config.setTokenUri("https://example.com/token");
    config.setAuthorizationUri("https://example.com/authorize");
    config.setGrantType("authorization_code");
    config.setRedirectUri("https://example.com/callback");
    config.setEndSessionEndpointUri("https://example.com/logout");

    // when
    final var registration = ClientRegistrationFactory.createClientRegistration("test-reg", config);

    // then
    assertThat(registration.getProviderDetails().getConfigurationMetadata())
        .containsEntry("end_session_endpoint", "https://example.com/logout");
  }
}
