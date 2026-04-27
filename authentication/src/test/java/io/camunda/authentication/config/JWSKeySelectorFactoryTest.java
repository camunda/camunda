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

  @Test
  void shouldThrowWhenAdditionalUriIsMalformed() {
    // given
    final var primaryPath = "/primary-malformed/.well-known/jwks.json";
    mockJwksEndpoint(primaryPath, primaryKey);

    // when // then
    assertThatThrownBy(
            () -> factory.createJWSKeySelector(baseUrl() + primaryPath, List.of("not-a-valid-url")))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void shouldSkipBlankAdditionalUriAndResolveFromPrimary() throws Exception {
    // given — blank additional URI should be silently filtered out
    final var primaryPath = "/primary-blank/.well-known/jwks.json";
    mockJwksEndpoint(primaryPath, primaryKey);

    final var selector = factory.createJWSKeySelector(baseUrl() + primaryPath, List.of(""));

    final var jwt = createSignedJwt(primaryKey, "primary-kid");

    // when
    final var keys = selector.selectJWSKeys(jwt.getHeader(), (SecurityContext) null);

    // then — key is resolved from primary, blank URI was skipped
    assertThat(keys).isNotEmpty();
  }

  @Test
  void shouldResolveKeyFromSecondAdditionalSourceWithMultipleAdditionalUris() throws Exception {
    // given — primary + 2 additional sources, key only at second additional
    final var primaryPath = "/primary-multi/.well-known/jwks.json";
    final var additional1Path = "/additional1-multi/.well-known/jwks.json";
    final var additional2Path = "/additional2-multi/.well-known/jwks.json";

    final var additional2Key =
        new RSAKeyGenerator(2048)
            .keyID("additional2-kid")
            .keyUse(KeyUse.SIGNATURE)
            .algorithm(JWSAlgorithm.RS256)
            .generate();

    mockJwksEndpoint(primaryPath, primaryKey);
    mockJwksEndpoint(additional1Path, additionalKey);
    mockJwksEndpoint(additional2Path, additional2Key);

    final var selector =
        factory.createJWSKeySelector(
            baseUrl() + primaryPath,
            List.of(baseUrl() + additional1Path, baseUrl() + additional2Path));

    // JWT signed with the second additional key's kid
    final var jwt = createSignedJwt(additional2Key, "additional2-kid");

    // when
    final var keys = selector.selectJWSKeys(jwt.getHeader(), (SecurityContext) null);

    // then
    assertThat(keys).isNotEmpty();
  }

  @Test
  void shouldSelectKeyFromPrimaryWhenSameKidExistsAtBothEndpoints() throws Exception {
    // given — both endpoints expose kid="shared-kid" but with different RSA key material.
    // This documents the short-circuit behavior: the primary source's key is always
    // returned when the kid matches, regardless of actual signing key.
    final var primaryKeySharedKid =
        new RSAKeyGenerator(2048)
            .keyID("shared-kid")
            .keyUse(KeyUse.SIGNATURE)
            .algorithm(JWSAlgorithm.RS256)
            .generate();
    final var additionalKeySharedKid =
        new RSAKeyGenerator(2048)
            .keyID("shared-kid")
            .keyUse(KeyUse.SIGNATURE)
            .algorithm(JWSAlgorithm.RS256)
            .generate();

    final var primaryPath = "/primary-shared-kid/.well-known/jwks.json";
    final var additionalPath = "/additional-shared-kid/.well-known/jwks.json";
    mockJwksEndpoint(primaryPath, primaryKeySharedKid);
    mockJwksEndpoint(additionalPath, additionalKeySharedKid);

    final var selector =
        factory.createJWSKeySelector(baseUrl() + primaryPath, List.of(baseUrl() + additionalPath));

    // JWT signed with the ADDITIONAL key's material, but same kid="shared-kid"
    final var jwt = createSignedJwt(additionalKeySharedKid, "shared-kid");

    // when — key selector finds kid="shared-kid" at primary (short-circuit)
    final var keys = selector.selectJWSKeys(jwt.getHeader(), (SecurityContext) null);

    // then — keys are returned (from primary), but they are the WRONG key material
    // for this token. Signature verification would fail at the JwtDecoder level.
    // The composite source does not fall through because the primary returned a non-empty result.
    assertThat(keys).hasSize(1);
  }

  @Test
  void shouldResolveDisjointKeysFromCorrectSources() throws Exception {
    // given — primary JWKS and additional JWKS hold disjoint key sets:
    // primary has kid="web-ui-kid" for Web UI tokens, additional has
    // kid="m2m-kid" for M2M tokens. The composite source must route each
    // token to the correct JWKS.
    final var webUiKey =
        new RSAKeyGenerator(2048)
            .keyID("web-ui-kid")
            .keyUse(KeyUse.SIGNATURE)
            .algorithm(JWSAlgorithm.RS256)
            .generate();
    final var m2mKey =
        new RSAKeyGenerator(2048)
            .keyID("m2m-kid")
            .keyUse(KeyUse.SIGNATURE)
            .algorithm(JWSAlgorithm.RS256)
            .generate();

    final var standardJwksPath = "/standard/.well-known/jwks.json";
    final var customJwksPath = "/custom/.well-known/jwks.json";
    mockJwksEndpoint(standardJwksPath, webUiKey);
    mockJwksEndpoint(customJwksPath, m2mKey);

    final var selector =
        factory.createJWSKeySelector(
            baseUrl() + standardJwksPath, List.of(baseUrl() + customJwksPath));

    // when — Web UI token with kid="web-ui-kid" resolves from primary
    final var webUiJwt = createSignedJwt(webUiKey, "web-ui-kid");
    final var webUiKeys = selector.selectJWSKeys(webUiJwt.getHeader(), (SecurityContext) null);

    // then
    assertThat(webUiKeys).isNotEmpty();

    // when — M2M token with kid="m2m-kid" falls through to additional
    final var m2mJwt = createSignedJwt(m2mKey, "m2m-kid");
    final var m2mKeys = selector.selectJWSKeys(m2mJwt.getHeader(), (SecurityContext) null);

    // then
    assertThat(m2mKeys).isNotEmpty();
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
