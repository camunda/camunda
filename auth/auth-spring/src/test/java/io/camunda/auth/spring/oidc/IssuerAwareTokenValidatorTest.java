/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.auth.spring.oidc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;

@ExtendWith(MockitoExtension.class)
class IssuerAwareTokenValidatorTest {

  private static final String ISSUER_URI = "https://issuer.example.com";

  @Mock private TokenValidatorFactory tokenValidatorFactory;

  @Test
  void shouldFailForUnknownIssuer() {
    // given
    final var validator = new IssuerAwareTokenValidator(List.of(), tokenValidatorFactory);
    final Jwt jwt = createJwt("https://unknown.example.com");

    // when
    final var result = validator.validate(jwt);

    // then
    assertThat(result.hasErrors()).isTrue();
    assertThat(result.getErrors()).anyMatch(error -> "invalid_token".equals(error.getErrorCode()));
  }

  @Test
  @SuppressWarnings("unchecked")
  void shouldDelegateToFactoryForKnownIssuer() {
    // given
    final ClientRegistration registration = createClientRegistration(ISSUER_URI);
    final OAuth2TokenValidator<Jwt> mockValidator =
        org.mockito.Mockito.mock(OAuth2TokenValidator.class);
    when(tokenValidatorFactory.createTokenValidator(registration)).thenReturn(mockValidator);
    when(mockValidator.validate(org.mockito.ArgumentMatchers.any()))
        .thenReturn(OAuth2TokenValidatorResult.success());

    final var validator =
        new IssuerAwareTokenValidator(List.of(registration), tokenValidatorFactory);
    final Jwt jwt = createJwt(ISSUER_URI);

    // when
    final var result = validator.validate(jwt);

    // then
    assertThat(result.hasErrors()).isFalse();
    verify(tokenValidatorFactory).createTokenValidator(registration);
  }

  @Test
  @SuppressWarnings("unchecked")
  void shouldCacheValidatorPerIssuer() {
    // given
    final ClientRegistration registration = createClientRegistration(ISSUER_URI);
    final OAuth2TokenValidator<Jwt> mockValidator =
        org.mockito.Mockito.mock(OAuth2TokenValidator.class);
    when(tokenValidatorFactory.createTokenValidator(registration)).thenReturn(mockValidator);
    when(mockValidator.validate(org.mockito.ArgumentMatchers.any()))
        .thenReturn(OAuth2TokenValidatorResult.success());

    final var validator =
        new IssuerAwareTokenValidator(List.of(registration), tokenValidatorFactory);
    final Jwt jwt = createJwt(ISSUER_URI);

    // when
    validator.validate(jwt);
    validator.validate(jwt);

    // then
    verify(tokenValidatorFactory, times(1)).createTokenValidator(registration);
  }

  private Jwt createJwt(final String issuer) {
    return Jwt.withTokenValue("token")
        .header("alg", "RS256")
        .claim("iss", issuer)
        .issuedAt(Instant.now())
        .expiresAt(Instant.now().plusSeconds(300))
        .build();
  }

  private ClientRegistration createClientRegistration(final String issuerUri) {
    return ClientRegistration.withRegistrationId("test-reg")
        .clientId("client-id")
        .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
        .redirectUri("https://example.com/callback")
        .authorizationUri("https://example.com/authorize")
        .tokenUri("https://example.com/token")
        .jwkSetUri("https://example.com/.well-known/jwks.json")
        .issuerUri(issuerUri)
        .build();
  }
}
