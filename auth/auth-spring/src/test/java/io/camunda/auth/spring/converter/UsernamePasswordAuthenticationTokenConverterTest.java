/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.auth.spring.converter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.camunda.auth.domain.model.CamundaAuthentication;
import io.camunda.auth.domain.spi.BasicAuthMembershipResolver;
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
class UsernamePasswordAuthenticationTokenConverterTest {

  @Mock private BasicAuthMembershipResolver basicAuthMembershipResolver;
  @InjectMocks private UsernamePasswordAuthenticationTokenConverter converter;

  @Test
  void shouldSupportUsernamePasswordToken() {
    // given
    final var auth = new UsernamePasswordAuthenticationToken("user", "pass");

    // when
    final boolean result = converter.supports(auth);

    // then
    assertThat(result).isTrue();
  }

  @Test
  void shouldNotSupportOAuth2Token() {
    // given
    final Jwt jwt =
        Jwt.withTokenValue("token-value")
            .header("alg", "RS256")
            .claim("sub", "user1")
            .claim("preferred_username", "john")
            .issuedAt(Instant.now())
            .expiresAt(Instant.now().plusSeconds(300))
            .build();
    final JwtAuthenticationToken jwtAuth = new JwtAuthenticationToken(jwt);

    // when
    final boolean result = converter.supports(jwtAuth);

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
  void shouldDelegateToBasicAuthMembershipResolver() {
    // given
    final var auth = new UsernamePasswordAuthenticationToken("john", "secret");
    final CamundaAuthentication expectedAuth = CamundaAuthentication.anonymous();
    when(basicAuthMembershipResolver.resolveMemberships("john")).thenReturn(expectedAuth);

    // when
    final CamundaAuthentication result = converter.convert(auth);

    // then
    assertThat(result).isSameAs(expectedAuth);
    verify(basicAuthMembershipResolver).resolveMemberships("john");
  }
}
