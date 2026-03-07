/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.auth.spring.oidc;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import com.nimbusds.jose.JOSEObjectType;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;

/**
 * Integration test for multi-issuer JWT validation. Exercises the full stack:
 * OidcAccessTokenDecoderFactory -> IssuerAwareJWSKeySelector -> JWSKeySelectorFactory ->
 * CompositeJWKSource, with real RSA keypairs and WireMock-served JWK set endpoints.
 */
class MultiIssuerJwtValidationTest {

  @RegisterExtension
  static WireMockExtension wireMock =
      WireMockExtension.newInstance()
          .options(
              com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig()
                  .dynamicPort())
          .build();

  private static RSAKey issuer1Key;
  private static RSAKey issuer2Key;
  private static RSAKey additionalKey;

  @BeforeAll
  static void generateKeys() throws Exception {
    issuer1Key = new RSAKeyGenerator(2048).keyID("key-issuer1").generate();
    issuer2Key = new RSAKeyGenerator(2048).keyID("key-issuer2").generate();
    additionalKey = new RSAKeyGenerator(2048).keyID("key-additional").generate();
  }

  @Test
  void shouldValidateJwtSignedByIssuer1() throws Exception {
    // given
    final String issuer1Uri = wireMock.baseUrl() + "/issuer1";
    final String issuer2Uri = wireMock.baseUrl() + "/issuer2";

    stubJwkSetEndpoint("/issuer1/.well-known/jwks.json", issuer1Key);
    stubJwkSetEndpoint("/issuer2/.well-known/jwks.json", issuer2Key);

    final JwtDecoder decoder = createMultiIssuerDecoder(issuer1Uri, issuer2Uri);
    final String token = createSignedJwt(issuer1Key, issuer1Uri, "user1");

    // when
    final var jwt = decoder.decode(token);

    // then
    assertThat(jwt.getSubject()).isEqualTo("user1");
    assertThat(jwt.getClaimAsString("iss")).isEqualTo(issuer1Uri);
  }

  @Test
  void shouldValidateJwtSignedByIssuer2() throws Exception {
    // given
    final String issuer1Uri = wireMock.baseUrl() + "/issuer1";
    final String issuer2Uri = wireMock.baseUrl() + "/issuer2";

    stubJwkSetEndpoint("/issuer1/.well-known/jwks.json", issuer1Key);
    stubJwkSetEndpoint("/issuer2/.well-known/jwks.json", issuer2Key);

    final JwtDecoder decoder = createMultiIssuerDecoder(issuer1Uri, issuer2Uri);
    final String token = createSignedJwt(issuer2Key, issuer2Uri, "user2");

    // when
    final var jwt = decoder.decode(token);

    // then
    assertThat(jwt.getSubject()).isEqualTo("user2");
    assertThat(jwt.getClaimAsString("iss")).isEqualTo(issuer2Uri);
  }

  @Test
  void shouldRejectJwtFromUnknownIssuer() throws Exception {
    // given
    final String issuer1Uri = wireMock.baseUrl() + "/issuer1";
    final String issuer2Uri = wireMock.baseUrl() + "/issuer2";
    final String unknownIssuerUri = wireMock.baseUrl() + "/unknown";

    stubJwkSetEndpoint("/issuer1/.well-known/jwks.json", issuer1Key);
    stubJwkSetEndpoint("/issuer2/.well-known/jwks.json", issuer2Key);

    final JwtDecoder decoder = createMultiIssuerDecoder(issuer1Uri, issuer2Uri);
    final String token = createSignedJwt(issuer1Key, unknownIssuerUri, "hacker");

    // when/then
    assertThatThrownBy(() -> decoder.decode(token)).isInstanceOf(JwtException.class);
  }

  @Test
  void shouldRejectJwtWithWrongSignature() throws Exception {
    // given — token claims issuer1 but is signed with issuer2's key
    final String issuer1Uri = wireMock.baseUrl() + "/issuer1";
    final String issuer2Uri = wireMock.baseUrl() + "/issuer2";

    stubJwkSetEndpoint("/issuer1/.well-known/jwks.json", issuer1Key);
    stubJwkSetEndpoint("/issuer2/.well-known/jwks.json", issuer2Key);

    final JwtDecoder decoder = createMultiIssuerDecoder(issuer1Uri, issuer2Uri);
    final String token = createSignedJwt(issuer2Key, issuer1Uri, "hacker");

    // when/then — issuer1's JWK set does not contain issuer2's key, so signature fails
    assertThatThrownBy(() -> decoder.decode(token)).isInstanceOf(JwtException.class);
  }

  @Test
  void shouldValidateJwtViaAdditionalJwkSetUris() throws Exception {
    // given — issuer1's primary JWK set has its own key, but the token is signed with
    // the additional key served from a separate endpoint
    final String issuer1Uri = wireMock.baseUrl() + "/issuer1";
    final String additionalJwkSetUri = wireMock.baseUrl() + "/additional/.well-known/jwks.json";

    stubJwkSetEndpoint("/issuer1/.well-known/jwks.json", issuer1Key);
    stubJwkSetEndpoint("/additional/.well-known/jwks.json", additionalKey);

    final JwtDecoder decoder =
        createSingleIssuerDecoderWithAdditionalJwkSetUris(issuer1Uri, additionalJwkSetUri);
    final String token = createSignedJwt(additionalKey, issuer1Uri, "service-account");

    // when
    final var jwt = decoder.decode(token);

    // then
    assertThat(jwt.getSubject()).isEqualTo("service-account");
  }

  @Test
  void shouldValidateTokenWithAtJwtType() throws Exception {
    // given — token with "at+jwt" type header
    final String issuer1Uri = wireMock.baseUrl() + "/issuer1";
    final String issuer2Uri = wireMock.baseUrl() + "/issuer2";

    stubJwkSetEndpoint("/issuer1/.well-known/jwks.json", issuer1Key);
    stubJwkSetEndpoint("/issuer2/.well-known/jwks.json", issuer2Key);

    final JwtDecoder decoder = createMultiIssuerDecoder(issuer1Uri, issuer2Uri);
    final String token = createSignedJwtWithType(issuer1Key, issuer1Uri, "user1", "at+jwt");

    // when
    final var jwt = decoder.decode(token);

    // then
    assertThat(jwt.getSubject()).isEqualTo("user1");
  }

  @Test
  void shouldRejectExpiredToken() throws Exception {
    // given
    final String issuer1Uri = wireMock.baseUrl() + "/issuer1";
    final String issuer2Uri = wireMock.baseUrl() + "/issuer2";

    stubJwkSetEndpoint("/issuer1/.well-known/jwks.json", issuer1Key);
    stubJwkSetEndpoint("/issuer2/.well-known/jwks.json", issuer2Key);

    final JwtDecoder decoder = createMultiIssuerDecoder(issuer1Uri, issuer2Uri);

    // Token expired 2 minutes ago (beyond default 60s clock skew)
    final var now = Instant.now();
    final var claimsSet =
        new JWTClaimsSet.Builder()
            .issuer(issuer1Uri)
            .subject("user1")
            .issueTime(Date.from(now.minusSeconds(600)))
            .expirationTime(Date.from(now.minusSeconds(120)))
            .build();
    final String token = signJwt(issuer1Key, claimsSet, JOSEObjectType.JWT);

    // when/then
    assertThatThrownBy(() -> decoder.decode(token)).isInstanceOf(JwtException.class);
  }

  // -- helper methods --

  private void stubJwkSetEndpoint(final String path, final RSAKey rsaKey) {
    final var publicJwk = rsaKey.toPublicJWK();
    final var jwkSet = new JWKSet(publicJwk);
    wireMock.stubFor(
        get(urlEqualTo(path))
            .willReturn(
                aResponse()
                    .withHeader("Content-Type", "application/json")
                    .withBody(jwkSet.toString())));
  }

  private JwtDecoder createMultiIssuerDecoder(final String issuer1Uri, final String issuer2Uri) {
    final var reg1 =
        createClientRegistration(
            "provider1", issuer1Uri, wireMock.baseUrl() + "/issuer1/.well-known/jwks.json");
    final var reg2 =
        createClientRegistration(
            "provider2", issuer2Uri, wireMock.baseUrl() + "/issuer2/.well-known/jwks.json");

    final var keySelectorFactory = new JWSKeySelectorFactory();
    final var oidcConfigRepo = createEmptyOidcConfigRepo();
    final var tokenValidatorFactory = new TokenValidatorFactory(oidcConfigRepo);
    final var decoderFactory =
        new OidcAccessTokenDecoderFactory(keySelectorFactory, tokenValidatorFactory);

    return decoderFactory.createIssuerAwareAccessTokenDecoder(List.of(reg1, reg2));
  }

  private JwtDecoder createSingleIssuerDecoderWithAdditionalJwkSetUris(
      final String issuerUri, final String additionalJwkSetUri) {
    final var reg =
        createClientRegistration(
            "provider1", issuerUri, wireMock.baseUrl() + "/issuer1/.well-known/jwks.json");

    final var keySelectorFactory = new JWSKeySelectorFactory();
    final var oidcConfigRepo = createEmptyOidcConfigRepo();
    final var tokenValidatorFactory = new TokenValidatorFactory(oidcConfigRepo);
    final var decoderFactory =
        new OidcAccessTokenDecoderFactory(keySelectorFactory, tokenValidatorFactory);

    return decoderFactory.createIssuerAwareAccessTokenDecoder(
        List.of(reg), Map.of(issuerUri, List.of(additionalJwkSetUri)));
  }

  private ClientRegistration createClientRegistration(
      final String registrationId, final String issuerUri, final String jwkSetUri) {
    return ClientRegistration.withRegistrationId(registrationId)
        .clientId("client-" + registrationId)
        .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
        .redirectUri("https://example.com/callback")
        .authorizationUri(issuerUri + "/authorize")
        .tokenUri(issuerUri + "/token")
        .jwkSetUri(jwkSetUri)
        .issuerUri(issuerUri)
        .build();
  }

  private String createSignedJwt(final RSAKey rsaKey, final String issuer, final String subject)
      throws Exception {
    final var now = Instant.now();
    final var claimsSet =
        new JWTClaimsSet.Builder()
            .issuer(issuer)
            .subject(subject)
            .issueTime(Date.from(now))
            .expirationTime(Date.from(now.plus(Duration.ofMinutes(5))))
            .build();
    return signJwt(rsaKey, claimsSet, JOSEObjectType.JWT);
  }

  private String createSignedJwtWithType(
      final RSAKey rsaKey, final String issuer, final String subject, final String type)
      throws Exception {
    final var now = Instant.now();
    final var claimsSet =
        new JWTClaimsSet.Builder()
            .issuer(issuer)
            .subject(subject)
            .issueTime(Date.from(now))
            .expirationTime(Date.from(now.plus(Duration.ofMinutes(5))))
            .build();
    return signJwt(rsaKey, claimsSet, new JOSEObjectType(type));
  }

  private String signJwt(
      final RSAKey rsaKey, final JWTClaimsSet claimsSet, final JOSEObjectType type)
      throws Exception {
    final var header =
        new JWSHeader.Builder(JWSAlgorithm.RS256).keyID(rsaKey.getKeyID()).type(type).build();
    final var signedJwt = new SignedJWT(header, claimsSet);
    signedJwt.sign(new RSASSASigner(rsaKey));
    return signedJwt.serialize();
  }

  private OidcAuthenticationConfigurationRepository createEmptyOidcConfigRepo() {
    return new OidcAuthenticationConfigurationRepository(createEmptySecurityConfiguration());
  }

  private io.camunda.auth.spring.config.SecurityConfiguration createEmptySecurityConfiguration() {
    final var secConfig = new io.camunda.auth.spring.config.SecurityConfiguration();
    final var authConfig = new io.camunda.auth.domain.config.AuthenticationConfiguration();
    secConfig.setAuthentication(authConfig);
    return secConfig;
  }
}
