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
import io.camunda.auth.domain.config.AuthenticationConfiguration;
import io.camunda.auth.domain.config.OidcAuthenticationConfiguration;
import io.camunda.auth.spring.config.SecurityConfiguration;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;

/**
 * Integration test for {@link OidcAccessTokenDecoderFactory}. Tests the factory methods for
 * creating single-issuer and multi-issuer JWT decoders, including token type validation, expiration,
 * and audience validation.
 */
class OidcAccessTokenDecoderFactoryTest {

  @RegisterExtension
  static WireMockExtension wireMock =
      WireMockExtension.newInstance()
          .options(
              com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig()
                  .dynamicPort())
          .build();

  private static RSAKey rsaKey;

  @BeforeAll
  static void generateKey() throws Exception {
    rsaKey = new RSAKeyGenerator(2048).keyID("test-key-1").generate();
  }

  @Test
  void createAccessTokenDecoderShouldDecodeValidJwt() throws Exception {
    // given
    final String issuerUri = wireMock.baseUrl() + "/issuer";
    final String jwkSetUri = wireMock.baseUrl() + "/issuer/.well-known/jwks.json";
    stubJwkSet("/issuer/.well-known/jwks.json", rsaKey);

    final JwtDecoder decoder = createSingleIssuerDecoder(issuerUri, jwkSetUri, null);
    final String token = createSignedJwt(issuerUri, "user-1", JOSEObjectType.JWT);

    // when
    final var jwt = decoder.decode(token);

    // then
    assertThat(jwt.getSubject()).isEqualTo("user-1");
    assertThat(jwt.getClaimAsString("iss")).isEqualTo(issuerUri);
  }

  @Test
  void createIssuerAwareDecoderShouldDecodeValidJwt() throws Exception {
    // given
    final String issuerUri = wireMock.baseUrl() + "/issuer";
    final String jwkSetUri = wireMock.baseUrl() + "/issuer/.well-known/jwks.json";
    stubJwkSet("/issuer/.well-known/jwks.json", rsaKey);

    final var reg = createClientRegistration("reg1", issuerUri, jwkSetUri);
    final JwtDecoder decoder = createMultiIssuerDecoder(List.of(reg));
    final String token = createSignedJwt(issuerUri, "user-1", JOSEObjectType.JWT);

    // when
    final var jwt = decoder.decode(token);

    // then
    assertThat(jwt.getSubject()).isEqualTo("user-1");
  }

  @Test
  void decoderShouldAcceptJwtTokenType() throws Exception {
    // given
    final String issuerUri = wireMock.baseUrl() + "/issuer";
    stubJwkSet("/issuer/.well-known/jwks.json", rsaKey);

    final JwtDecoder decoder =
        createSingleIssuerDecoder(
            issuerUri, wireMock.baseUrl() + "/issuer/.well-known/jwks.json", null);
    final String token = createSignedJwt(issuerUri, "user-jwt", JOSEObjectType.JWT);

    // when
    final var jwt = decoder.decode(token);

    // then
    assertThat(jwt.getSubject()).isEqualTo("user-jwt");
  }

  @Test
  void decoderShouldAcceptAtJwtTokenType() throws Exception {
    // given
    final String issuerUri = wireMock.baseUrl() + "/issuer";
    stubJwkSet("/issuer/.well-known/jwks.json", rsaKey);

    final JwtDecoder decoder =
        createSingleIssuerDecoder(
            issuerUri, wireMock.baseUrl() + "/issuer/.well-known/jwks.json", null);
    final String token =
        createSignedJwt(issuerUri, "user-at-jwt", new JOSEObjectType("at+jwt"));

    // when
    final var jwt = decoder.decode(token);

    // then
    assertThat(jwt.getSubject()).isEqualTo("user-at-jwt");
  }

  @Test
  void decoderShouldRejectExpiredToken() throws Exception {
    // given
    final String issuerUri = wireMock.baseUrl() + "/issuer";
    stubJwkSet("/issuer/.well-known/jwks.json", rsaKey);

    final JwtDecoder decoder =
        createSingleIssuerDecoder(
            issuerUri, wireMock.baseUrl() + "/issuer/.well-known/jwks.json", null);

    // Token expired 2 minutes ago (beyond default 60s clock skew)
    final var now = Instant.now();
    final var claimsSet =
        new JWTClaimsSet.Builder()
            .issuer(issuerUri)
            .subject("expired-user")
            .issueTime(Date.from(now.minusSeconds(600)))
            .expirationTime(Date.from(now.minusSeconds(120)))
            .build();
    final String token = signJwt(claimsSet, JOSEObjectType.JWT);

    // when/then
    assertThatThrownBy(() -> decoder.decode(token)).isInstanceOf(JwtException.class);
  }

  @Test
  void decoderShouldRejectTokenWithWrongAudience() throws Exception {
    // given
    final String issuerUri = wireMock.baseUrl() + "/issuer";
    final String jwkSetUri = wireMock.baseUrl() + "/issuer/.well-known/jwks.json";
    stubJwkSet("/issuer/.well-known/jwks.json", rsaKey);

    // Create decoder with audience validation
    final JwtDecoder decoder =
        createSingleIssuerDecoderWithAudience(issuerUri, jwkSetUri, Set.of("expected-audience"));

    final var now = Instant.now();
    final var claimsSet =
        new JWTClaimsSet.Builder()
            .issuer(issuerUri)
            .subject("user-wrong-aud")
            .audience("wrong-audience")
            .issueTime(Date.from(now))
            .expirationTime(Date.from(now.plusSeconds(300)))
            .build();
    final String token = signJwt(claimsSet, JOSEObjectType.JWT);

    // when/then
    assertThatThrownBy(() -> decoder.decode(token)).isInstanceOf(JwtException.class);
  }

  @Test
  void decoderShouldAcceptTokenWithCorrectAudience() throws Exception {
    // given
    final String issuerUri = wireMock.baseUrl() + "/issuer";
    final String jwkSetUri = wireMock.baseUrl() + "/issuer/.well-known/jwks.json";
    stubJwkSet("/issuer/.well-known/jwks.json", rsaKey);

    final JwtDecoder decoder =
        createSingleIssuerDecoderWithAudience(issuerUri, jwkSetUri, Set.of("my-audience"));

    final var now = Instant.now();
    final var claimsSet =
        new JWTClaimsSet.Builder()
            .issuer(issuerUri)
            .subject("user-correct-aud")
            .audience("my-audience")
            .issueTime(Date.from(now))
            .expirationTime(Date.from(now.plusSeconds(300)))
            .build();
    final String token = signJwt(claimsSet, JOSEObjectType.JWT);

    // when
    final var jwt = decoder.decode(token);

    // then
    assertThat(jwt.getSubject()).isEqualTo("user-correct-aud");
  }

  @Test
  void shouldRejectClientRegistrationsWithMissingIssuer() {
    // given
    final var factory = createDecoderFactory(Map.of());
    final var reg =
        ClientRegistration.withRegistrationId("no-issuer")
            .clientId("client")
            .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
            .redirectUri("https://example.com/callback")
            .authorizationUri("https://example.com/authorize")
            .tokenUri("https://example.com/token")
            .jwkSetUri("https://example.com/.well-known/jwks.json")
            .build();

    // when/then
    assertThatThrownBy(() -> factory.createIssuerAwareAccessTokenDecoder(List.of(reg)))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("missing 'issuerUri'");
  }

  @Test
  void shouldRejectClientRegistrationWithMissingJwkSetUri() {
    // given
    final var factory = createDecoderFactory(Map.of());
    final var reg =
        ClientRegistration.withRegistrationId("no-jwk")
            .clientId("client")
            .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
            .redirectUri("https://example.com/callback")
            .authorizationUri("https://example.com/authorize")
            .tokenUri("https://example.com/token")
            .issuerUri("https://issuer.example.com")
            .build();

    // when/then
    assertThatThrownBy(() -> factory.createAccessTokenDecoder(reg))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("missing a valid 'jwk-set-uri'");
  }

  @Test
  void createAccessTokenDecoderWithAdditionalJwkSetUrisShouldWork() throws Exception {
    // given
    final RSAKey additionalKey = new RSAKeyGenerator(2048).keyID("additional-key").generate();
    final String issuerUri = wireMock.baseUrl() + "/issuer";
    final String jwkSetUri = wireMock.baseUrl() + "/issuer/.well-known/jwks.json";
    final String additionalJwkSetUri = wireMock.baseUrl() + "/additional/.well-known/jwks.json";

    stubJwkSet("/issuer/.well-known/jwks.json", rsaKey);
    stubJwkSet("/additional/.well-known/jwks.json", additionalKey);

    final JwtDecoder decoder =
        createSingleIssuerDecoder(issuerUri, jwkSetUri, List.of(additionalJwkSetUri));

    // Sign with the additional key
    final var now = Instant.now();
    final var claimsSet =
        new JWTClaimsSet.Builder()
            .issuer(issuerUri)
            .subject("additional-user")
            .issueTime(Date.from(now))
            .expirationTime(Date.from(now.plusSeconds(300)))
            .build();
    final var header =
        new JWSHeader.Builder(JWSAlgorithm.RS256)
            .keyID(additionalKey.getKeyID())
            .type(JOSEObjectType.JWT)
            .build();
    final var signedJwt = new SignedJWT(header, claimsSet);
    signedJwt.sign(new RSASSASigner(additionalKey));

    // when
    final var jwt = decoder.decode(signedJwt.serialize());

    // then
    assertThat(jwt.getSubject()).isEqualTo("additional-user");
  }

  // -- Helper methods --

  private void stubJwkSet(final String path, final RSAKey key) {
    final var publicJwk = key.toPublicJWK();
    final var jwkSet = new JWKSet(publicJwk);
    wireMock.stubFor(
        get(urlEqualTo(path))
            .willReturn(
                aResponse()
                    .withHeader("Content-Type", "application/json")
                    .withBody(jwkSet.toString())));
  }

  private JwtDecoder createSingleIssuerDecoder(
      final String issuerUri, final String jwkSetUri, final List<String> additionalJwkSetUris) {
    final var factory = createDecoderFactory(Map.of());
    final var reg = createClientRegistration("test-reg", issuerUri, jwkSetUri);
    return factory.createAccessTokenDecoder(reg, additionalJwkSetUris);
  }

  private JwtDecoder createSingleIssuerDecoderWithAudience(
      final String issuerUri, final String jwkSetUri, final Set<String> audiences) {
    final var config = new OidcAuthenticationConfiguration();
    config.setClientId("my-client");
    config.setAudiences(audiences);

    // Use "camunda" as registration ID because OidcAuthenticationConfigurationRepository
    // stores the main OIDC config under the key "camunda"
    final var factory = createDecoderFactory(Map.of("camunda", config));
    final var reg = createClientRegistration("camunda", issuerUri, jwkSetUri);
    return factory.createAccessTokenDecoder(reg);
  }

  private JwtDecoder createMultiIssuerDecoder(final List<ClientRegistration> registrations) {
    final var factory = createDecoderFactory(Map.of());
    return factory.createIssuerAwareAccessTokenDecoder(registrations);
  }

  private OidcAccessTokenDecoderFactory createDecoderFactory(
      final Map<String, OidcAuthenticationConfiguration> oidcConfigs) {
    final var keySelectorFactory = new JWSKeySelectorFactory();
    final var oidcConfigRepo = createOidcConfigRepo(oidcConfigs);
    final var tokenValidatorFactory = new TokenValidatorFactory(oidcConfigRepo);
    return new OidcAccessTokenDecoderFactory(keySelectorFactory, tokenValidatorFactory);
  }

  private OidcAuthenticationConfigurationRepository createOidcConfigRepo(
      final Map<String, OidcAuthenticationConfiguration> configs) {
    final var secConfig = new SecurityConfiguration();
    final var authConfig = new AuthenticationConfiguration();

    if (!configs.isEmpty()) {
      // Set the main OIDC config if keyed as "camunda" (the default registration ID)
      final var mainConfig = configs.get("camunda");
      if (mainConfig != null) {
        authConfig.setOidc(mainConfig);
      }
    }

    secConfig.setAuthentication(authConfig);
    return new OidcAuthenticationConfigurationRepository(secConfig);
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

  private String createSignedJwt(
      final String issuer, final String subject, final JOSEObjectType type) throws Exception {
    final var now = Instant.now();
    final var claimsSet =
        new JWTClaimsSet.Builder()
            .issuer(issuer)
            .subject(subject)
            .issueTime(Date.from(now))
            .expirationTime(Date.from(now.plus(Duration.ofMinutes(5))))
            .build();
    return signJwt(claimsSet, type);
  }

  private String signJwt(final JWTClaimsSet claimsSet, final JOSEObjectType type)
      throws Exception {
    final var header =
        new JWSHeader.Builder(JWSAlgorithm.RS256)
            .keyID(rsaKey.getKeyID())
            .type(type)
            .build();
    final var signedJwt = new SignedJWT(header, claimsSet);
    signedJwt.sign(new RSASSASigner(rsaKey));
    return signedJwt.serialize();
  }
}
