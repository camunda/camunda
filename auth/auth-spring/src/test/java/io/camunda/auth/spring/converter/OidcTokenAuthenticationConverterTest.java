/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.auth.spring.converter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.camunda.auth.domain.model.CamundaAuthentication;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

@ExtendWith(MockitoExtension.class)
class OidcTokenAuthenticationConverterTest {

  @Mock private TokenClaimsConverter tokenClaimsConverter;
  @InjectMocks private OidcTokenAuthenticationConverter converter;

  @Test
  void shouldSupportOAuth2TokenAuthentication() {
    // given
    final JwtAuthenticationToken jwtAuth = new JwtAuthenticationToken(buildJwt());

    // when
    final boolean result = converter.supports(jwtAuth);

    // then
    assertThat(result).isTrue();
  }

  @Test
  void shouldNotSupportNonOAuth2Authentication() {
    // given
    final var usernamePasswordAuth = new UsernamePasswordAuthenticationToken("user", "pass");

    // when
    final boolean result = converter.supports(usernamePasswordAuth);

    // then
    assertThat(result).isFalse();
  }

  @Test
  void shouldNotSupportNull() {
    // when
    final boolean result = converter.supports(null);

    // then
    assertThat(result).isFalse();
  }

  @Test
  void shouldDelegateToTokenClaimsConverter() {
    // given
    final Jwt jwt = buildJwt();
    final JwtAuthenticationToken jwtAuth = new JwtAuthenticationToken(jwt);
    final CamundaAuthentication expectedAuth = CamundaAuthentication.anonymous();
    when(tokenClaimsConverter.convert(anyMap())).thenReturn(expectedAuth);

    // when
    final CamundaAuthentication result = converter.convert(jwtAuth);

    // then
    assertThat(result).isSameAs(expectedAuth);
    verify(tokenClaimsConverter).convert(jwt.getClaims());
  }

  private Jwt buildJwt() {
    return Jwt.withTokenValue("token-value")
        .header("alg", "RS256")
        .claim("sub", "user1")
        .claim("preferred_username", "john")
        .issuedAt(Instant.now())
        .expiresAt(Instant.now().plusSeconds(300))
        .build();
  }
}
