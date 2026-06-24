/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.authentication.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import io.camunda.security.api.model.config.oidc.OidcConfiguration;
import io.camunda.security.core.port.in.OidcProviderConfigurationPort;
import io.camunda.security.spring.oidc.TokenValidatorFactory;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtDecoderFactory;

/**
 * Regression test for the bug where {@code idTokenDecoderFactory}'s JWS algorithm resolver was
 * keyed by {@link ClientRegistration} instance rather than by {@code registrationId}. Per-PT scoped
 * chains construct their own {@link ClientRegistration} objects (same {@code registrationId},
 * different object identity), so the old instance-keyed map returned {@code null} and the scoped
 * id_token signature verification failed with {@code missing_signature_verifier}.
 *
 * <p>The fix keys the resolver by {@code registrationId} string, which works for both the cluster
 * chain and PT scoped chains regardless of object identity.
 */
@ExtendWith(MockitoExtension.class)
class OidcOverrideBeansConfigurationIdTokenDecoderTest {

  @Mock private OidcProviderConfigurationPort oidcProviderConfigurationPort;

  @Test
  void shouldResolveIdTokenAlgorithmByRegistrationIdNotInstance() {
    // given
    final var registrationId = "tenanta";

    final var oidcConfig = new OidcConfiguration();
    oidcConfig.setIdTokenAlgorithm(OidcConfiguration.DEFAULT_ID_TOKEN_ALGORITHM); // "RS256"

    when(oidcProviderConfigurationPort.getOidcAuthenticationConfigurations())
        .thenReturn(Map.of(registrationId, oidcConfig));

    final var tokenValidatorFactory =
        new TokenValidatorFactory(
            Map.of(registrationId, oidcConfig), Duration.ofSeconds(60), List.of());

    final var configuration = new OidcOverrideBeansConfiguration(null);
    final JwtDecoderFactory<ClientRegistration> decoderFactory =
        configuration.idTokenDecoderFactory(tokenValidatorFactory, oidcProviderConfigurationPort);

    // Build a fresh ClientRegistration with the same registrationId but a different object
    // identity — this is exactly what PT scoped chains do at runtime.
    final var freshRegistration = buildRegistration(registrationId);

    // when
    final JwtDecoder decoder = decoderFactory.createDecoder(freshRegistration);

    // then — must be non-null; before the fix, the instance lookup returned null and
    // OidcIdTokenDecoderFactory threw "missing_signature_verifier".
    assertThat(decoder).isNotNull();
  }

  @Test
  void shouldResolveIdenticallyForDistinctInstancesWithSameRegistrationId() {
    // given
    final var registrationId = "tenanta";

    final var oidcConfig = new OidcConfiguration();
    oidcConfig.setIdTokenAlgorithm(OidcConfiguration.DEFAULT_ID_TOKEN_ALGORITHM);

    when(oidcProviderConfigurationPort.getOidcAuthenticationConfigurations())
        .thenReturn(Map.of(registrationId, oidcConfig));

    final var tokenValidatorFactory =
        new TokenValidatorFactory(
            Map.of(registrationId, oidcConfig), Duration.ofSeconds(60), List.of());

    final var configuration = new OidcOverrideBeansConfiguration(null);
    final JwtDecoderFactory<ClientRegistration> decoderFactory =
        configuration.idTokenDecoderFactory(tokenValidatorFactory, oidcProviderConfigurationPort);

    // Two distinct instances — same registrationId, different objects
    final var instanceOne = buildRegistration(registrationId);
    final var instanceTwo = buildRegistration(registrationId);

    // when
    final JwtDecoder decoderOne = decoderFactory.createDecoder(instanceOne);
    final JwtDecoder decoderTwo = decoderFactory.createDecoder(instanceTwo);

    // then — both resolve successfully; the pre-fix code would have thrown for both because
    // neither instance was the object stored in the algorithm map.
    assertThat(decoderOne).isNotNull();
    assertThat(decoderTwo).isNotNull();
  }

  private static ClientRegistration buildRegistration(final String registrationId) {
    return ClientRegistration.withRegistrationId(registrationId)
        .clientId("client-id")
        .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
        .redirectUri("{baseUrl}/login/oauth2/code/{registrationId}")
        .authorizationUri("https://example.com/oauth2/authorize")
        .tokenUri("https://example.com/oauth2/token")
        .jwkSetUri("https://example.com/oauth2/jwks")
        .build();
  }
}
