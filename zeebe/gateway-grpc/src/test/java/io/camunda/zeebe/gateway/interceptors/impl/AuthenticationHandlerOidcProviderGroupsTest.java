/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.gateway.interceptors.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.camunda.security.configuration.AuthenticationConfiguration;
import io.camunda.security.configuration.OidcAuthenticationConfiguration;
import io.camunda.security.configuration.ProvidersConfiguration;
import io.camunda.security.entity.AuthenticationMethod;
import io.camunda.security.oidc.OidcClaimsProvider;
import io.camunda.zeebe.gateway.interceptors.impl.AuthenticationHandler.Oidc;
import io.grpc.Context;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;

/** Several OIDC providers, of which only the additional one has a groups claim. */
class AuthenticationHandlerOidcProviderGroupsTest {

  private static final String DEFAULT_ISSUER = "https://default.example.com";
  private static final String PARTNER_ISSUER = "https://partner.example.com";

  private final JwtDecoder jwtDecoder = mock(JwtDecoder.class);
  private final OidcClaimsProvider claimsProvider = mock(OidcClaimsProvider.class);
  private Oidc handler;

  @BeforeEach
  void setUp() {
    final var defaultOidc = new OidcAuthenticationConfiguration();
    defaultOidc.setIssuerUri(DEFAULT_ISSUER);
    defaultOidc.setUsernameClaim("sub");
    final var partnerOidc = new OidcAuthenticationConfiguration();
    partnerOidc.setIssuerUri(PARTNER_ISSUER);
    partnerOidc.setGroupsClaim("groups");
    final var providers = new ProvidersConfiguration();
    providers.setOidc(Map.of("partner", partnerOidc));
    final var authentication = new AuthenticationConfiguration();
    authentication.setMethod(AuthenticationMethod.OIDC);
    authentication.setOidc(defaultOidc);
    authentication.setProviders(providers);
    handler = new Oidc(jwtDecoder, claimsProvider, authentication);
  }

  @Test
  void shouldReadGroupsFromTheClaimOfTheProviderThatIssuedTheToken() throws Exception {
    // given
    final var context =
        authenticate(Map.of("sub", "alice", "iss", PARTNER_ISSUER, "groups", List.of("g1")));

    // then
    assertThat(context.call(() -> AuthenticationHandler.IS_CAMUNDA_GROUPS_ENABLED.get())).isFalse();
    assertThat(context.call(() -> AuthenticationHandler.GROUPS_CLAIMS.get())).containsExactly("g1");
  }

  @Test
  void shouldKeepAnEmptyGroupsClaimOfAProviderWithAGroupsClaim() throws Exception {
    // given
    final var context = authenticate(Map.of("sub", "alice", "iss", PARTNER_ISSUER));

    // then
    assertThat(context.call(() -> AuthenticationHandler.IS_CAMUNDA_GROUPS_ENABLED.get())).isFalse();
    assertThat(context.call(() -> AuthenticationHandler.GROUPS_CLAIMS.get())).isEmpty();
  }

  @Test
  void shouldNotReadGroupsOfAProviderWithoutAGroupsClaim() throws Exception {
    // given
    final var context =
        authenticate(Map.of("sub", "alice", "iss", DEFAULT_ISSUER, "groups", List.of("g1")));

    // then
    assertThat(context.call(() -> AuthenticationHandler.GROUPS_CLAIMS.get())).isNull();
  }

  @Test
  void shouldNotReadGroupsOfAnUnknownIssuer() throws Exception {
    // given
    final var context =
        authenticate(
            Map.of("sub", "alice", "iss", "https://unknown.example.com", "groups", List.of("g1")));

    // then
    assertThat(context.call(() -> AuthenticationHandler.GROUPS_CLAIMS.get())).isNull();
  }

  private Context authenticate(final Map<String, Object> claims) {
    final var jwt =
        Jwt.withTokenValue("token").header("alg", "RS256").claim("sub", "alice").build();
    when(jwtDecoder.decode("token")).thenReturn(jwt);
    when(claimsProvider.claimsFor(any(), eq("token"))).thenReturn(claims);
    final var result = handler.authenticate("Bearer token");
    assertThat(result.isRight()).isTrue();
    return result.get();
  }

  @Test
  void shouldNotRequireGroupsToExistWhenOneProviderHasAGroupsClaim() throws Exception {
    // given a token of the provider without a groups claim
    final var context = authenticate(Map.of("sub", "alice", "iss", DEFAULT_ISSUER));

    // then a group of the other provider's claim can't be required to exist in Camunda
    assertThat(context.call(() -> AuthenticationHandler.IS_CAMUNDA_GROUPS_ENABLED.get())).isFalse();
  }
}
