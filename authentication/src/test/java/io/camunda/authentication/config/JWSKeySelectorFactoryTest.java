/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.authentication.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.KeyUse;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import com.nimbusds.jose.proc.SecurityContext;
import com.nimbusds.jose.util.JSONObjectUtils;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import java.util.Date;
import java.util.List;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.http.HttpStatus;

final class JWSKeySelectorFactoryTest {

  @RegisterExtension
  static WireMockExtension wireMock =
      WireMockExtension.newInstance().configureStaticDsl(true).build();

  private static RSAKey primaryKey;
  private static RSAKey additionalKey;

  private final JWSKeySelectorFactory factory = new JWSKeySelectorFactory();

  @BeforeAll
  static void generateKeys() throws JOSEException {
    primaryKey =
        new RSAKeyGenerator(2048)
            .keyID("primary-kid")
            .keyUse(KeyUse.SIGNATURE)
            .algorithm(JWSAlgorithm.RS256)
            .generate();

    additionalKey =
        new RSAKeyGenerator(2048)
            .keyID("additional-kid")
            .keyUse(KeyUse.SIGNATURE)
            .algorithm(JWSAlgorithm.RS256)
            .generate();
  }

  @Test
  void shouldDelegateToSingleArgWhenAdditionalUrisNull() {
    // given
    final var jwkSetUri = baseUrl() + "/primary/.well-known/jwks.json";
    mockJwksEndpoint("/primary/.well-known/jwks.json", primaryKey);

    // when
    final var selector = factory.createJWSKeySelector(jwkSetUri, null);

    // then
    assertThat(selector).isNotNull();
  }

  @Test
  void shouldDelegateToSingleArgWhenAdditionalUrisEmpty() {
    // given
    final var jwkSetUri = baseUrl() + "/primary/.well-known/jwks.json";
    mockJwksEndpoint("/primary/.well-known/jwks.json", primaryKey);

    // when
    final var selector = factory.createJWSKeySelector(jwkSetUri, List.of());

    // then
    assertThat(selector).isNotNull();
  }

  @Test
  void shouldThrowWhenPrimaryJwkSetUriMissingWithAdditionalUris() {
    // when // then
    assertThatThrownBy(() -> factory.createJWSKeySelector(null, List.of("http://example.com/jwks")))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void shouldResolveKeyFromPrimarySource() throws Exception {
    // given
    final var primaryPath = "/primary-resolve/.well-known/jwks.json";
    final var additionalPath = "/additional-resolve/.well-known/jwks.json";
    mockJwksEndpoint(primaryPath, primaryKey);
    mockJwksEndpoint(additionalPath, additionalKey);

    final var selector =
        factory.createJWSKeySelector(baseUrl() + primaryPath, List.of(baseUrl() + additionalPath));

    final var jwt = createSignedJwt(primaryKey, "primary-kid");

    // when
    final var keys = selector.selectJWSKeys(jwt.getHeader(), (SecurityContext) null);

    // then
    assertThat(keys).isNotEmpty();
  }

  @Test
  void shouldResolveKeyFromAdditionalSourceWhenPrimaryDoesNotHaveIt() throws Exception {
    // given
    final var primaryPath = "/primary-miss/.well-known/jwks.json";
    final var additionalPath = "/additional-hit/.well-known/jwks.json";
    // primary has primaryKey, additional has additionalKey
    mockJwksEndpoint(primaryPath, primaryKey);
    mockJwksEndpoint(additionalPath, additionalKey);

    final var selector =
        factory.createJWSKeySelector(baseUrl() + primaryPath, List.of(baseUrl() + additionalPath));

    // JWT signed with the additional key's kid
    final var jwt = createSignedJwt(additionalKey, "additional-kid");

    // when
    final var keys = selector.selectJWSKeys(jwt.getHeader(), (SecurityContext) null);

    // then
    assertThat(keys).isNotEmpty();
  }

  private static String baseUrl() {
    return "http://localhost:" + wireMock.getPort();
  }

  private static void mockJwksEndpoint(final String path, final RSAKey key) {
    final var jwksBody = JSONObjectUtils.toJSONString(new JWKSet(key.toPublicJWK()).toJSONObject());
    wireMock
        .getRuntimeInfo()
        .getWireMock()
        .register(
            WireMock.get(WireMock.urlEqualTo(path))
                .willReturn(WireMock.jsonResponse(jwksBody, HttpStatus.OK.value())));
  }

  private static SignedJWT createSignedJwt(final RSAKey key, final String kid)
      throws JOSEException {
    final var jwt =
        new SignedJWT(
            new JWSHeader.Builder(JWSAlgorithm.RS256).keyID(kid).build(),
            new JWTClaimsSet.Builder()
                .subject("test")
                .issuer("http://issuer.example.com")
                .expirationTime(new Date(new Date().getTime() + 60_000))
                .build());
    jwt.sign(new RSASSASigner(key));
    return jwt;
  }
}
