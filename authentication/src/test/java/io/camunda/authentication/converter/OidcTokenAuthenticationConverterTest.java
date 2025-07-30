/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.authentication.converter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.camunda.security.auth.CamundaAuthentication;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

public class OidcTokenAuthenticationConverterTest {

  @Mock private TokenClaimsConverter tokenClaimsConverter;
  @InjectMocks private OidcTokenAuthenticationConverter authenticationConverter;

  @BeforeEach
  void setup() throws Exception {
    MockitoAnnotations.openMocks(this).close();
  }

  @Test
  void shouldSupport() {
    // given
    final var authentication = mock(JwtAuthenticationToken.class);

    // when
    final var supports = authenticationConverter.supports(authentication);

    // then
    assertThat(supports).isTrue();
  }

  @Test
  void shouldNotSupport() {
    // given
    final var authentication = mock(OAuth2AuthenticationToken.class);

    // when
    final var supports = authenticationConverter.supports(authentication);

    // then
    assertThat(supports).isFalse();
  }

  @Test
  public void shouldConvertAccessToken() {
    // given

    final Map<String, Object> accessTokenClaims =
        Map.of("access_token", "test-access-token", "token_type", "Bearer", "expires_in", 3600);
    final var authentication = mock(JwtAuthenticationToken.class);
    when(authentication.getTokenAttributes()).thenReturn(accessTokenClaims);

    final var expectedAuthentication = CamundaAuthentication.of(b -> b.user("foo"));
    when(tokenClaimsConverter.convert(eq(accessTokenClaims))).thenReturn(expectedAuthentication);

    // when
    final var userToken = authenticationConverter.convert(authentication);

    // then
    assertThat(userToken).isEqualTo(expectedAuthentication);
    verify(tokenClaimsConverter).convert(eq(accessTokenClaims));
  }
}
