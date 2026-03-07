/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.auth.spring.oidc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import io.camunda.auth.domain.config.OidcAuthenticationConfiguration;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.jwt.Jwt;

@ExtendWith(MockitoExtension.class)
class TokenValidatorFactoryTest {

  @Mock private OidcAuthenticationConfigurationRepository oidcConfigRepository;

  @Test
  void shouldCreateTimestampValidatorWithDefaultClockSkew() {
    // given
    when(oidcConfigRepository.getOidcAuthenticationConfigurations()).thenReturn(Map.of());
    final var factory = new TokenValidatorFactory(oidcConfigRepository);
    final ClientRegistration registration = createClientRegistration("test-reg");

    // Jwt that expired 50 seconds ago (within default 60s skew)
    final Jwt jwt =
        Jwt.withTokenValue("token")
            .header("alg", "RS256")
            .claim("sub", "user1")
            .issuedAt(Instant.now().minusSeconds(600))
            .expiresAt(Instant.now().minusSeconds(50))
            .build();

    // when
    final var validator = factory.createTokenValidator(registration);
    final var result = validator.validate(jwt);

    // then
    assertThat(result.hasErrors()).isFalse();
  }

  @Test
  void shouldCreateTimestampValidatorWithConfiguredClockSkew() {
    // given
    final var config = new OidcAuthenticationConfiguration();
    config.setClientId("my-client");
    config.setClockSkew(Duration.ofSeconds(10));
    when(oidcConfigRepository.getOidcAuthenticationConfigurations())
        .thenReturn(Map.of("test-reg", config));
    final var factory = new TokenValidatorFactory(oidcConfigRepository);
    final ClientRegistration registration = createClientRegistration("test-reg");

    // Jwt that expired 50 seconds ago (outside 10s skew)
    final Jwt jwt =
        Jwt.withTokenValue("token")
            .header("alg", "RS256")
            .claim("sub", "user1")
            .issuedAt(Instant.now().minusSeconds(600))
            .expiresAt(Instant.now().minusSeconds(50))
            .build();

    // when
    final var validator = factory.createTokenValidator(registration);
    final var result = validator.validate(jwt);

    // then
    assertThat(result.hasErrors()).isTrue();
  }

  @Test
  void shouldAddAudienceValidatorWhenAudiencesConfigured() {
    // given
    final var config = new OidcAuthenticationConfiguration();
    config.setClientId("my-client");
    config.setAudiences(Set.of("my-audience"));
    when(oidcConfigRepository.getOidcAuthenticationConfigurations())
        .thenReturn(Map.of("test-reg", config));
    final var factory = new TokenValidatorFactory(oidcConfigRepository);
    final ClientRegistration registration = createClientRegistration("test-reg");

    // Jwt without the expected audience
    final Jwt jwt =
        Jwt.withTokenValue("token")
            .header("alg", "RS256")
            .claim("aud", List.of("wrong-audience"))
            .issuedAt(Instant.now())
            .expiresAt(Instant.now().plusSeconds(300))
            .build();

    // when
    final var validator = factory.createTokenValidator(registration);
    final var result = validator.validate(jwt);

    // then
    assertThat(result.hasErrors()).isTrue();
    assertThat(result.getErrors()).anyMatch(error -> "invalid_token".equals(error.getErrorCode()));
  }

  private ClientRegistration createClientRegistration(final String registrationId) {
    return ClientRegistration.withRegistrationId(registrationId)
        .clientId("client-id")
        .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
        .redirectUri("https://example.com/callback")
        .authorizationUri("https://example.com/authorize")
        .tokenUri("https://example.com/token")
        .jwkSetUri("https://example.com/.well-known/jwks.json")
        .issuerUri("https://issuer.example.com")
        .build();
  }
}
