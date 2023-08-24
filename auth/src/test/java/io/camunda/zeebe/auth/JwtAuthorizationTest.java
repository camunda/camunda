/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.auth0.jwt.JWT;
import com.auth0.jwt.algorithms.Algorithm;
import com.auth0.jwt.interfaces.Claim;
import io.camunda.zeebe.auth.api.AuthorizationEncoder;
import io.camunda.zeebe.auth.impl.Authorization;
import io.camunda.zeebe.auth.impl.JwtAuthorizationDecoder;
import io.camunda.zeebe.auth.impl.JwtAuthorizationEncoder;
import io.camunda.zeebe.util.exception.UnrecoverableException;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

public class JwtAuthorizationTest {

  private final String defaultIssuer = "zeebe-gateway";
  private final String defaultAudience = "zeebe-broker";
  private final String defaultSubject = "Authorization";

  @Test
  public void shouldEncodeJwtTokenWithDefaultClaims() {
    // when
    final AuthorizationEncoder encoder = Authorization.encodeWithJwt();
    final String jwtToken = encoder.getEncodedString();

    // then
    final Map<String, Claim> claims = JWT.decode(jwtToken).getClaims();
    assertDefaultClaims(claims);
  }

  @Test
  public void shouldEncodeJwtTokenWithAuthorizedTenants() {
    // given
    final List<String> authorizedTenants = List.of("tenant-1", "tenant-2", "tenant-3");

    // when
    final AuthorizationEncoder encoder =
        Authorization.encodeWithJwt()
            .withClaim(JwtAuthorizationEncoder.AUTHORIZED_TENANTS_CLAIM, authorizedTenants);
    final String jwtToken = encoder.getEncodedString();

    // then
    final Map<String, Claim> claims = JWT.decode(jwtToken).getClaims();
    // assert default claims are also present
    assertDefaultClaims(claims);
    // and authorized tenants claim is present
    assertThat(claims).containsKey(JwtAuthorizationEncoder.AUTHORIZED_TENANTS_CLAIM);
    final List<String> authorizedTenantClaim =
        claims.get(JwtAuthorizationEncoder.AUTHORIZED_TENANTS_CLAIM).as(List.class);
    assertThat(authorizedTenantClaim).containsExactlyElementsOf(authorizedTenants);
  }

  @Test
  public void shouldValidateAndDecodeJwtTokenWithDefaultClaims() {
    // given
    final String jwtToken =
        JWT.create()
            .withIssuer(defaultIssuer)
            .withAudience(defaultAudience)
            .withSubject(defaultSubject)
            .sign(Algorithm.none());

    // when
    final JwtAuthorizationDecoder decoder = Authorization.decodeWithJwt().withJwtToken(jwtToken);
    final Map<String, Claim> claims = decoder.getAuthorizations();

    // then
    assertDefaultClaims(claims);
  }

  @Test
  public void shouldValidateAndDecodeJwtTokenWithAuthorizedTenantsClaim() {
    // given
    final List<String> authorizedTenants = List.of("tenant-1", "tenant-2", "tenant-3");
    final String jwtToken =
        JWT.create()
            .withIssuer(defaultIssuer)
            .withAudience(defaultAudience)
            .withSubject(defaultSubject)
            .withClaim(JwtAuthorizationEncoder.AUTHORIZED_TENANTS_CLAIM, authorizedTenants)
            .sign(Algorithm.none());

    // when
    final JwtAuthorizationDecoder decoder =
        Authorization.decodeWithJwt()
            .withClaim(JwtAuthorizationEncoder.AUTHORIZED_TENANTS_CLAIM)
            .withJwtToken(jwtToken);
    final Map<String, Claim> claims = decoder.getAuthorizations();

    // then
    assertDefaultClaims(claims);
    final List<String> authorizedTenantClaim =
        claims.get(JwtAuthorizationEncoder.AUTHORIZED_TENANTS_CLAIM).as(List.class);
    assertThat(authorizedTenantClaim).containsExactlyElementsOf(authorizedTenants);
  }

  @Test
  public void shouldFailJwtTokenValidationWithNoAuthorizedTenants() {
    // given
    final String jwtToken =
        JWT.create()
            .withIssuer(defaultIssuer)
            .withAudience(defaultAudience)
            .withSubject(defaultSubject)
            .sign(Algorithm.none());

    // when
    final JwtAuthorizationDecoder decoder =
        Authorization.decodeWithJwt()
            .withClaim(JwtAuthorizationEncoder.AUTHORIZED_TENANTS_CLAIM)
            .withJwtToken(jwtToken);

    // then
    assertThatThrownBy(() -> decoder.getAuthorizations())
        .isInstanceOf(UnrecoverableException.class)
        .hasMessage(
            "Authorization data unavailable: The Claim 'authorized_tenants' is not present in the JWT.");
  }

  @Test
  public void shouldFailJwtTokenDecodingWithoutJwtToken() {
    // when
    final JwtAuthorizationDecoder decoder =
        Authorization.decodeWithJwt().withClaim(JwtAuthorizationEncoder.AUTHORIZED_TENANTS_CLAIM);

    // then
    assertThatThrownBy(() -> decoder.getAuthorizations())
        .isInstanceOf(UnrecoverableException.class)
        .hasMessage("Authorization data unavailable: JWT token");
  }

  private void assertDefaultClaims(final Map<String, Claim> claims) {
    assertThat(claims).containsKey("iss");
    assertThat(claims.get("iss").as(String.class)).isEqualTo(defaultIssuer);
    assertThat(claims).containsKey("aud");
    assertThat(claims.get("aud").as(String.class)).isEqualTo(defaultAudience);
    assertThat(claims).containsKey("sub");
    assertThat(claims.get("sub").as(String.class)).isEqualTo(defaultSubject);
  }
}
