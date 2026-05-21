/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.authentication.converter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.camunda.security.api.model.CamundaAuthentication;
import io.camunda.security.oidc.NoopOidcClaimsProvider;
import io.camunda.security.oidc.OidcClaimsProvider;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

public class OidcTokenAuthenticationConverterTest {

  @Test
  void shouldSupportJwtAuthenticationToken() {
    final var converter =
        new OidcTokenAuthenticationConverter(
            mock(TokenClaimsConverter.class), new NoopOidcClaimsProvider());

    assertThat(converter.supports(mock(JwtAuthenticationToken.class))).isTrue();
  }

  @Test
  void shouldNotSupportNonJwtAuthentication() {
    final var converter =
        new OidcTokenAuthenticationConverter(
            mock(TokenClaimsConverter.class), new NoopOidcClaimsProvider());

    assertThat(converter.supports(mock(OAuth2AuthenticationToken.class))).isFalse();
  }

  @Test
  void shouldPassJwtClaimsThroughNoopProviderToTokenClaimsConverter() {
    final TokenClaimsConverter tokenClaimsConverter = mock(TokenClaimsConverter.class);
    final var jwt =
        Jwt.withTokenValue("token-abc")
            .header("alg", "RS256")
            .claim("sub", "alice")
            .claim("iss", "https://idp.example")
            .build();
    final var authentication = new JwtAuthenticationToken(jwt);
    final var expected = CamundaAuthentication.of(b -> b.user("alice"));
    when(tokenClaimsConverter.convert(jwt.getClaims())).thenReturn(expected);

    final var converter =
        new OidcTokenAuthenticationConverter(tokenClaimsConverter, new NoopOidcClaimsProvider());

    assertThat(converter.convert(authentication)).isSameAs(expected);
  }

  @Test
  void shouldUseClaimsReturnedByProviderForAugmentation() {
    final TokenClaimsConverter tokenClaimsConverter = mock(TokenClaimsConverter.class);
    final OidcClaimsProvider claimsProvider = mock(OidcClaimsProvider.class);
    final var jwt =
        Jwt.withTokenValue("token-abc")
            .header("alg", "RS256")
            .claim("sub", "alice")
            .claim("iss", "https://idp.example")
            .build();
    final var authentication = new JwtAuthenticationToken(jwt);

    final Map<String, Object> augmentedClaims = Map.of("sub", "alice", "groups", List.of("eng"));
    when(claimsProvider.claimsFor(any(), eq("token-abc"))).thenReturn(augmentedClaims);
    final var expected = CamundaAuthentication.of(b -> b.user("alice"));
    when(tokenClaimsConverter.convert(augmentedClaims)).thenReturn(expected);

    final var converter =
        new OidcTokenAuthenticationConverter(tokenClaimsConverter, claimsProvider);

    assertThat(converter.convert(authentication)).isSameAs(expected);
  }
}
