/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.auth;

import static io.camunda.zeebe.auth.api.JwtAuthorizationBuilder.DEFAULT_AUDIENCE;
import static io.camunda.zeebe.auth.api.JwtAuthorizationBuilder.DEFAULT_ISSUER;
import static io.camunda.zeebe.auth.api.JwtAuthorizationBuilder.DEFAULT_SUBJECT;
import static io.camunda.zeebe.auth.api.JwtAuthorizationBuilder.USER_TOKEN_CLAIM_PREFIX;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.auth0.jwt.JWT;
import com.auth0.jwt.algorithms.Algorithm;
import com.auth0.jwt.interfaces.Claim;
import io.camunda.zeebe.auth.api.AuthorizationEncoder;
import io.camunda.zeebe.auth.impl.Authorization;
import io.camunda.zeebe.auth.impl.JwtAuthorizationDecoder;
import io.camunda.zeebe.util.exception.UnrecoverableException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
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
  public void shouldEncodeJwtTokenWithAuthenticatedUserKey() {
    // given
    final Long authenticatedUserKey = 123L;

    // when
    final AuthorizationEncoder encoder =
        Authorization.jwtEncoder()
            .withClaim(Authorization.AUTHORIZED_USER_KEY, authenticatedUserKey);
    final String jwtToken = encoder.encode();

    // then
    final Map<String, Claim> claims = JWT.decode(jwtToken).getClaims();
    // assert default claims are also present
    assertDefaultClaims(claims);
    // and authorized tenants claim is present
    assertThat(claims).containsKey(Authorization.AUTHORIZED_USER_KEY);
    final Long authenticatedUserKeyClaim =
        claims.get(Authorization.AUTHORIZED_USER_KEY).as(Long.class);
    assertThat(authenticatedUserKeyClaim).isEqualTo(authenticatedUserKey);
  }

  @Test
  public void shouldDecodeJwtTokenWithExtraClaimsKey() {
    // when
    final AuthorizationEncoder encoder =
        Authorization.jwtEncoder()
            .withClaim(USER_TOKEN_CLAIM_PREFIX + "usr", "usr1")
            .withClaim(USER_TOKEN_CLAIM_PREFIX + "sub", "sub1")
            .withClaim(USER_TOKEN_CLAIM_PREFIX + "groups", List.of("g1", "g2"));
    final String jwtToken = encoder.encode();

    // then
    final Map<String, Claim> claims = JWT.decode(jwtToken).getClaims();
    // assert default claims are also present
    assertDefaultClaims(claims);
    // and extra claims are present
    assertThat(claims).containsKey(USER_TOKEN_CLAIM_PREFIX + "usr");
    assertThat(claims).containsKey(USER_TOKEN_CLAIM_PREFIX + "sub");
    assertThat(claims).containsKey(USER_TOKEN_CLAIM_PREFIX + "groups");
    assertThat(claims.get(USER_TOKEN_CLAIM_PREFIX + "usr").as(String.class)).isEqualTo("usr1");
    assertThat(claims.get(USER_TOKEN_CLAIM_PREFIX + "sub").as(String.class)).isEqualTo("sub1");
    assertThat(claims.get(USER_TOKEN_CLAIM_PREFIX + "groups").as(List.class))
        .containsAll(List.of("g1", "g2"));
  }

  @Test
  public void shouldValidateAndDecodeJwtTokenWithExtraClaims() {
    // given
    final String jwtToken =
        JWT.create()
            .withIssuer(DEFAULT_ISSUER)
            .withAudience(DEFAULT_AUDIENCE)
            .withSubject(DEFAULT_SUBJECT)
            .withClaim(USER_TOKEN_CLAIM_PREFIX + "usr", "usr1")
            .withClaim(USER_TOKEN_CLAIM_PREFIX + "sub", "sub1")
            .withClaim(USER_TOKEN_CLAIM_PREFIX + "groups", List.of("g1", "g2"))
            .sign(Algorithm.none());

    // when
    final JwtAuthorizationDecoder decoder = Authorization.jwtDecoder(jwtToken);
    final Map<String, Claim> claims = decoder.build().getClaims();

    // then
    assertDefaultClaims(claims);

    assertThat(claims).containsKey(USER_TOKEN_CLAIM_PREFIX + "usr");
    assertThat(claims).containsKey(USER_TOKEN_CLAIM_PREFIX + "sub");
    assertThat(claims).containsKey(USER_TOKEN_CLAIM_PREFIX + "groups");
    assertThat(claims.get(USER_TOKEN_CLAIM_PREFIX + "usr").as(String.class)).isEqualTo("usr1");
    assertThat(claims.get(USER_TOKEN_CLAIM_PREFIX + "sub").as(String.class)).isEqualTo("sub1");
    assertThat(claims.get(USER_TOKEN_CLAIM_PREFIX + "groups").as(List.class))
        .containsAll(List.of("g1", "g2"));
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

  @Test
  public void shouldNotFailVerificationForFutureIssuedAt() {
    // given
    final String jwtToken =
        JWT.create()
            .withIssuer(DEFAULT_ISSUER)
            .withAudience(DEFAULT_AUDIENCE)
            .withSubject(DEFAULT_SUBJECT)
            .withClaim(Authorization.AUTHORIZED_TENANTS, List.of())
            .withIssuedAt(Instant.now().plus(10L, ChronoUnit.MINUTES))
            .sign(Algorithm.none());

    // when /then
    Authorization.jwtDecoder(jwtToken).withClaim(Authorization.AUTHORIZED_TENANTS).build();
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
