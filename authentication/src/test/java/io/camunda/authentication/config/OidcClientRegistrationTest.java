/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.authentication.config;

import io.camunda.security.configuration.OidcAuthenticationConfiguration;
import java.util.Map;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.mockito.Mockito;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.security.oauth2.client.registration.ClientRegistration;

@ExtendWith(OutputCaptureExtension.class)
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

  @ParameterizedTest
  @NullAndEmptySource
  public void shouldFallbackToValidDefaultRedirectUri(final String blankRedirectUri) {
    final var config = new OidcAuthenticationConfiguration();
    config.setClientId("clientId");
    config.setAuthorizationUri("authorizationUri");
    config.setRedirectUri(blankRedirectUri);
    config.setTokenUri("tokenUri");
    config.setClientAuthenticationMethod("client_secret_basic");

    Assertions.assertThat(ClientRegistrationFactory.createClientRegistration("foo", config))
        .returns("{baseUrl}/sso-callback", ClientRegistration::getRedirectUri);
  }

  @Test
  void shouldWriteConfiguredRevocationEndpointIntoProviderMetadata() {
    // given a provider whose revocation endpoint is configured rather than discovered, and an
    // operator who opted in
    final var config = baseConfig();
    config.setRevocationEndpointUri("https://idp.example.com/oauth2/revoke");
    config.setRevocationEnabled(true);

    // when
    final var clientRegistration =
        ClientRegistrationFactory.createClientRegistration("foo", config);

    // then it lands under the key RefreshTokenRevocationLogoutHandler reads
    Assertions.assertThat(clientRegistration.getProviderDetails().getConfigurationMetadata())
        .containsEntry("revocation_endpoint", "https://idp.example.com/oauth2/revoke");
  }

  @Test
  void shouldNotWriteARevocationEndpointWhenNoneIsConfigured() {
    // given a provider with no revocation endpoint configured and none to discover
    final var config = baseConfig();

    // when
    final var clientRegistration =
        ClientRegistrationFactory.createClientRegistration("foo", config);

    // then there is nothing for the logout handler to call
    Assertions.assertThat(clientRegistration.getProviderDetails().getConfigurationMetadata())
        .doesNotContainKey("revocation_endpoint");
  }

  /**
   * The default. Revocation is off unless an operator asks for it, matching what 8.10 ships through
   * its {@code OidcRevocationDefaultPostProcessor}, so that upgrading from 8.9 to 8.10 does not
   * silently stop revoking. Without this the endpoint a provider publishes would be enough to turn
   * logout into an outbound call to the IdP.
   */
  @Test
  void shouldIgnoreADiscoveredRevocationEndpointUnlessRevocationIsEnabled() {
    // given a provider that publishes a revocation endpoint and an operator who said nothing
    final var config = baseConfig();
    final var discovered =
        Map.<String, Object>of("revocation_endpoint", "https://idp.example.com/oauth2/revoke");

    // when
    final var clientRegistration = createWithDiscoveredMetadata(config, discovered);

    // then nothing is revoked until revocation-enabled=true
    Assertions.assertThat(clientRegistration.getProviderDetails().getConfigurationMetadata())
        .doesNotContainKey("revocation_endpoint");
  }

  /**
   * The flag has to strip a <em>discovered</em> endpoint, not just decline to write a configured
   * one: Keycloak, Okta and Auth0 all publish {@code revocation_endpoint}, so without this an
   * upgrade would switch revocation on for every discovery-based deployment with no way to decline.
   */
  @Test
  void shouldRemoveADiscoveredRevocationEndpointWhenRevocationIsDisabled() {
    // given a provider that publishes a revocation endpoint, and an operator who declined it
    final var config = baseConfig();
    config.setRevocationEnabled(false);
    final var discovered =
        Map.<String, Object>of("revocation_endpoint", "https://idp.example.com/oauth2/revoke");

    // when
    final var clientRegistration = createWithDiscoveredMetadata(config, discovered);

    // then the key is gone, so the logout handler takes its existing no-op path
    Assertions.assertThat(clientRegistration.getProviderDetails().getConfigurationMetadata())
        .doesNotContainKey("revocation_endpoint");
  }

  @Test
  void shouldIgnoreAConfiguredRevocationEndpointWhenRevocationIsDisabled() {
    // given both the endpoint and the opt-out configured, which is a contradiction
    final var config = baseConfig();
    config.setRevocationEndpointUri("https://idp.example.com/oauth2/revoke");
    config.setRevocationEnabled(false);

    // when
    final var clientRegistration =
        ClientRegistrationFactory.createClientRegistration("foo", config);

    // then disabling wins, as it does for the end-session redirect
    Assertions.assertThat(clientRegistration.getProviderDetails().getConfigurationMetadata())
        .doesNotContainKey("revocation_endpoint");
  }

  /**
   * The registration id in this warning is operator-supplied configuration, the same as the one
   * {@link RefreshTokenRevocationLogoutHandler} escapes at logout, and has to go through the same
   * escaping: a CR or LF in it would otherwise forge a second line in the log it lands in.
   */
  @Test
  void shouldNotLetAConfiguredRegistrationIdForgeALogLineInTheContradictionWarning(
      final CapturedOutput output) {
    // given a registration id carrying a line break, with both the endpoint and the opt-out
    // configured so the warning fires
    final var config = baseConfig();
    config.setRevocationEndpointUri("https://idp.example.com/oauth2/revoke");
    config.setRevocationEnabled(false);
    final var forgingId = "foo\n\rWARN  forged line";

    // when
    ClientRegistrationFactory.createClientRegistration(forgingId, config);

    // then the break is escaped rather than written out, so the forged line never appears
    Assertions.assertThat(output.getAll())
        .contains("foo\\u000a\\u000dWARN  forged line")
        .doesNotContain("\nWARN  forged line");
  }

  /**
   * Regression for the metadata merge. {@code providerConfigurationMetadata} replaces the map, so
   * writing an explicitly configured {@code end_session_endpoint} on the builder used to drop every
   * endpoint issuer discovery had found, including the revocation endpoint this feature depends on
   * for providers that publish one.
   */
  @Test
  void shouldKeepDiscoveredProviderMetadataWhenEndSessionEndpointIsConfigured() {
    // given a provider that published a revocation endpoint and an operator who opted in, plus an
    // explicitly configured end-session endpoint
    final var config = baseConfig();
    config.setEndSessionEndpointUri("https://idp.example.com/protocol/openid-connect/logout");
    config.setRevocationEnabled(true);
    final var discovered =
        Map.<String, Object>of("revocation_endpoint", "https://idp.example.com/oauth2/revoke");

    // when
    final var clientRegistration = createWithDiscoveredMetadata(config, discovered);

    // then both survive
    Assertions.assertThat(clientRegistration.getProviderDetails().getConfigurationMetadata())
        .containsEntry("revocation_endpoint", "https://idp.example.com/oauth2/revoke")
        .containsEntry(
            "end_session_endpoint", "https://idp.example.com/protocol/openid-connect/logout");
  }

  /**
   * Stands in for issuer discovery, which is the only thing that fills the provider metadata before
   * the factory gets to it, by handing the factory a builder that already carries it.
   */
  private static ClientRegistration createWithDiscoveredMetadata(
      final OidcAuthenticationConfiguration config, final Map<String, Object> discovered) {
    final var builder =
        ClientRegistration.withRegistrationId("foo").providerConfigurationMetadata(discovered);
    try (final var mockedRegistration = Mockito.mockStatic(ClientRegistration.class)) {
      mockedRegistration
          .when(() -> ClientRegistration.withRegistrationId(Mockito.anyString()))
          .thenReturn(builder);
      mockedRegistration
          .when(() -> ClientRegistration.withClientRegistration(Mockito.any()))
          .thenCallRealMethod();
      return ClientRegistrationFactory.createClientRegistration("foo", config);
    }
  }

  private static OidcAuthenticationConfiguration baseConfig() {
    final var config = new OidcAuthenticationConfiguration();
    config.setClientId("clientId");
    config.setRedirectUri("redirectUri");
    config.setAuthorizationUri("authorizationUri");
    config.setTokenUri("tokenUri");
    config.setClientAuthenticationMethod("client_secret_basic");
    return config;
  }
}
