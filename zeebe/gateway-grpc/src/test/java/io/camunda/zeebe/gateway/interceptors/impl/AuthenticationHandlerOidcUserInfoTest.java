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

import io.camunda.security.configuration.OidcAuthenticationConfiguration;
import io.camunda.security.oidc.OidcClaimsProvider;
import io.camunda.zeebe.gateway.interceptors.impl.AuthenticationHandler.Oidc;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;

class AuthenticationHandlerOidcUserInfoTest {

  @Test
  void usesClaimsProviderResultForGroupsAndPrincipal() throws Exception {
    final var jwtDecoder = mock(JwtDecoder.class);
    final var claimsProvider = mock(OidcClaimsProvider.class);
    final var oidc = new OidcAuthenticationConfiguration();
    oidc.setUsernameClaim("sub");
    oidc.setGroupsClaim("groups");

    final var jwt =
        Jwt.withTokenValue("token-abc").header("alg", "RS256").claim("sub", "alice").build();
    when(jwtDecoder.decode("token-abc")).thenReturn(jwt);
    // provider adds groups from userinfo that the JWT does not have
    when(claimsProvider.claimsFor(any(), eq("token-abc")))
        .thenReturn(Map.of("sub", "alice", "groups", List.of("engineering")));

    final var handler = new Oidc(jwtDecoder, oidc, claimsProvider);
    final var result = handler.authenticate("Bearer token-abc");

    assertThat(result.isRight()).isTrue();
    final var ctx = result.get();
    assertThat(ctx.call(() -> AuthenticationHandler.GROUPS_CLAIMS.get()))
        .isEqualTo(List.of("engineering"));
    assertThat(ctx.call(() -> AuthenticationHandler.USERNAME.get())).isEqualTo("alice");
  }

  @Test
  void failsClosedWhenClaimsProviderThrows() {
    final var jwtDecoder = mock(JwtDecoder.class);
    final var claimsProvider = mock(OidcClaimsProvider.class);
    final var oidc = new OidcAuthenticationConfiguration();
    oidc.setUsernameClaim("sub");

    final var jwt =
        Jwt.withTokenValue("token-abc").header("alg", "RS256").claim("sub", "alice").build();
    when(jwtDecoder.decode("token-abc")).thenReturn(jwt);
    when(claimsProvider.claimsFor(any(), eq("token-abc")))
        .thenThrow(new RuntimeException("userinfo down"));

    final var handler = new Oidc(jwtDecoder, oidc, claimsProvider);
    final var result = handler.authenticate("Bearer token-abc");

    assertThat(result.isLeft()).isTrue();
    assertThat(result.getLeft().getCode()).isEqualTo(io.grpc.Status.UNAUTHENTICATED.getCode());
  }

  @Test
  void bearerAuthFailureStatusDoesNotLeakIdpDetails() {
    // Cause exceptions whose messages embed IdP URLs and internal error codes. The gRPC
    // Status description returned to the client must NOT carry these through (no
    // .withCause(e), no upstream error strings).
    final var jwtDecoder = mock(JwtDecoder.class);
    final var claimsProvider = mock(OidcClaimsProvider.class);
    final var oidc = new OidcAuthenticationConfiguration();
    oidc.setUsernameClaim("sub");

    // (1) claims-provider failure with a URL-bearing cause
    final var jwt =
        Jwt.withTokenValue("token-abc").header("alg", "RS256").claim("sub", "alice").build();
    when(jwtDecoder.decode("token-abc")).thenReturn(jwt);
    when(claimsProvider.claimsFor(any(), eq("token-abc")))
        .thenThrow(
            new RuntimeException(
                "UserInfo request to https://internal.idp.example/userinfo returned 502"));

    final var handler = new Oidc(jwtDecoder, oidc, claimsProvider);
    final var claimsFail = handler.authenticate("Bearer token-abc");

    assertThat(claimsFail.isLeft()).isTrue();
    final String claimsDescription = claimsFail.getLeft().getDescription();
    assertThat(claimsDescription)
        .doesNotContain("internal.idp.example")
        .doesNotContain("502")
        .doesNotContain("userinfo");
    assertThat(claimsFail.getLeft().getCause()).isNull();

    // (2) JWT decode failure with a diagnostic message
    when(jwtDecoder.decode("token-bad"))
        .thenThrow(
            new org.springframework.security.oauth2.jwt.JwtException(
                "Jwt expired at 2024-01-01; iss mismatch: https://wrong.example"));

    final var decodeFail = handler.authenticate("Bearer token-bad");
    final String decodeDescription = decodeFail.getLeft().getDescription();
    assertThat(decodeDescription).doesNotContain("wrong.example").doesNotContain("2024-01-01");
    assertThat(decodeFail.getLeft().getCause()).isNull();
  }
}
