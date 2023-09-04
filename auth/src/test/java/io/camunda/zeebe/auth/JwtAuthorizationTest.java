/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.auth;

import static io.camunda.zeebe.auth.api.JwtAuthorizationBuilder.DEFAULT_AUDIENCE;
import static io.camunda.zeebe.auth.api.JwtAuthorizationBuilder.DEFAULT_ISSUER;
import static io.camunda.zeebe.auth.api.JwtAuthorizationBuilder.DEFAULT_SUBJECT;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.auth0.jwt.JWT;
import com.auth0.jwt.algorithms.Algorithm;
import com.auth0.jwt.interfaces.Claim;
import io.camunda.zeebe.auth.api.AuthorizationEncoder;
import io.camunda.zeebe.auth.impl.Authorization;
import io.camunda.zeebe.auth.impl.JwtAuthorizationDecoder;
import io.camunda.zeebe.util.exception.UnrecoverableException;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

public class JwtAuthorizationTest {

  @Test
  public void shouldEncodeJwtTokenWithDefaultClaims() {
    // when
    final AuthorizationEncoder encoder = Authorization.jwtEncoder();
    final String jwtToken = encoder.encode();

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
        Authorization.jwtEncoder().withClaim(Authorization.AUTHORIZED_TENANTS, authorizedTenants);
    final String jwtToken = encoder.encode();

    // then
    final Map<String, Claim> claims = JWT.decode(jwtToken).getClaims();
    // assert default claims are also present
    assertDefaultClaims(claims);
    // and authorized tenants claim is present
    assertThat(claims).containsKey(Authorization.AUTHORIZED_TENANTS);
    final List<String> authorizedTenantClaim =
        claims.get(Authorization.AUTHORIZED_TENANTS).as(List.class);
    assertThat(authorizedTenantClaim).containsExactlyElementsOf(authorizedTenants);
  }

  @Test
  public void shouldValidateAndDecodeJwtTokenWithDefaultClaims() {
    // given
    final String jwtToken =
        JWT.create()
            .withIssuer(DEFAULT_ISSUER)
            .withAudience(DEFAULT_AUDIENCE)
            .withSubject(DEFAULT_SUBJECT)
            .sign(Algorithm.none());

    // when
    final JwtAuthorizationDecoder decoder = Authorization.jwtDecoder(jwtToken);
    final Map<String, Claim> claims = decoder.build().getClaims();

    // then
    assertDefaultClaims(claims);
  }

  @Test
  public void shouldValidateAndDecodeJwtTokenWithAuthorizedTenantsClaim() {
    // given
    final List<String> authorizedTenants = List.of("tenant-1", "tenant-2", "tenant-3");
    final String jwtToken =
        JWT.create()
            .withIssuer(DEFAULT_ISSUER)
            .withAudience(DEFAULT_AUDIENCE)
            .withSubject(DEFAULT_SUBJECT)
            .withClaim(Authorization.AUTHORIZED_TENANTS, authorizedTenants)
            .sign(Algorithm.none());

    // when
    final JwtAuthorizationDecoder decoder =
        Authorization.jwtDecoder(jwtToken).withClaim(Authorization.AUTHORIZED_TENANTS);
    final Map<String, Claim> claims = decoder.build().getClaims();

    // then
    assertDefaultClaims(claims);
    final List<String> authorizedTenantClaim =
        claims.get(Authorization.AUTHORIZED_TENANTS).as(List.class);
    assertThat(authorizedTenantClaim).containsExactlyElementsOf(authorizedTenants);
  }

  @Test
  public void shouldFailJwtTokenValidationWithNoAuthorizedTenants() {
    // given
    final String jwtToken =
        JWT.create()
            .withIssuer(DEFAULT_ISSUER)
            .withAudience(DEFAULT_AUDIENCE)
            .withSubject(DEFAULT_SUBJECT)
            .sign(Algorithm.none());

    // when
    final JwtAuthorizationDecoder decoder =
        Authorization.jwtDecoder(jwtToken).withClaim(Authorization.AUTHORIZED_TENANTS);

    // then
    assertThatThrownBy(() -> decoder.decode())
        .isInstanceOf(UnrecoverableException.class)
        .hasMessage(
            "Authorization data unavailable: The Claim 'authorized_tenants' is not present in the JWT.");
  }

  @Test
  public void shouldFailJwtTokenDecodingWithInvalidJwtToken() {
    // given
    final String invalidJwtToken = "invalid.jwt.token";

    // when
    final JwtAuthorizationDecoder decoder = Authorization.jwtDecoder(invalidJwtToken);

    // then
    assertThatThrownBy(() -> decoder.decode())
        .isInstanceOf(UnrecoverableException.class)
        .hasMessageContaining("Authorization data unavailable")
        .hasMessageContaining("doesn't have a valid JSON format");
  }

  @Test
  public void shouldFailJwtTokenDecodingWithoutJwtToken() {
    // when
    final JwtAuthorizationDecoder decoder =
        Authorization.jwtDecoder(null).withClaim(Authorization.AUTHORIZED_TENANTS);

    // then
    assertThatThrownBy(() -> decoder.decode())
        .isInstanceOf(UnrecoverableException.class)
        .hasMessage("Authorization data unavailable: The token is null.");
  }

  private void assertDefaultClaims(final Map<String, Claim> claims) {
    assertThat(claims).containsKey("iss");
    assertThat(claims.get("iss").as(String.class)).isEqualTo(DEFAULT_ISSUER);
    assertThat(claims).containsKey("aud");
    assertThat(claims.get("aud").as(String.class)).isEqualTo(DEFAULT_AUDIENCE);
    assertThat(claims).containsKey("sub");
    assertThat(claims.get("sub").as(String.class)).isEqualTo(DEFAULT_SUBJECT);
  }
}
