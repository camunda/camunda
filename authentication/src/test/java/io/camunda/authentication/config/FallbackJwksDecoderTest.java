/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.authentication.config;

import static com.nimbusds.jose.JOSEObjectType.JWT;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.junit5.WireMockRuntimeInfo;
import com.github.tomakehurst.wiremock.junit5.WireMockTest;
import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.KeyUse;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import io.camunda.security.configuration.OidcAuthenticationConfiguration;
import io.camunda.security.configuration.SecurityConfiguration;
import java.util.Date;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;

@WireMockTest
class FallbackJwksDecoderTest {

  @Test
  void shouldDecodeTokenWithKeyFromPrimaryJwks(final WireMockRuntimeInfo wmInfo)
      throws JOSEException {
    final var primaryKey = generateKey("primary-kid");
    final var decoder = createDecoder(wmInfo, "/primary/certs", "/secondary/certs");

    mockJwksEndpoint("/primary/certs", primaryKey.toPublicJWK().toJSONString());
    mockJwksEndpoint("/secondary/certs", "");

    final var token = signToken(primaryKey, wmInfo.getHttpBaseUrl() + "/issuer");
    assertThatCode(() -> decoder.decode(token)).doesNotThrowAnyException();
  }

  @Test
  void shouldDecodeTokenWithKeyFromSecondaryJwks(final WireMockRuntimeInfo wmInfo)
      throws JOSEException {
    final var secondaryKey = generateKey("secondary-kid");
    final var decoder = createDecoder(wmInfo, "/primary/certs", "/secondary/certs");

    mockJwksEndpoint("/primary/certs", "");
    mockJwksEndpoint("/secondary/certs", secondaryKey.toPublicJWK().toJSONString());

    final var token = signToken(secondaryKey, wmInfo.getHttpBaseUrl() + "/issuer");
    assertThatCode(() -> decoder.decode(token)).doesNotThrowAnyException();
  }

  @Test
  void shouldFailWhenKeyNotFoundInAnyJwks(final WireMockRuntimeInfo wmInfo)
      throws JOSEException {
    final var unknownKey = generateKey("unknown-kid");
    final var decoder = createDecoder(wmInfo, "/primary/certs", "/secondary/certs");

    mockJwksEndpoint("/primary/certs", "");
    mockJwksEndpoint("/secondary/certs", "");

    final var token = signToken(unknownKey, wmInfo.getHttpBaseUrl() + "/issuer");
    assertThatThrownBy(() -> decoder.decode(token)).isInstanceOf(JwtException.class);
  }

  private JwtDecoder createDecoder(
      final WireMockRuntimeInfo wmInfo,
      final String primaryPath,
      final String secondaryPath) {
    final var baseUrl = wmInfo.getHttpBaseUrl();
    final var primaryJwksUri = baseUrl + primaryPath;
    final var secondaryJwksUri = baseUrl + secondaryPath;

    final var oidcConfig =
        OidcAuthenticationConfiguration.builder()
            .clientId("test-client")
            .jwkSetUri(primaryJwksUri)
            .additionalJwkSetUris(List.of(secondaryJwksUri))
            .build();

    final var registrationId = "test";
    final var clientRegistration =
        ClientRegistration.withRegistrationId(registrationId)
            .clientId("test-client")
            .authorizationGrantType(AuthorizationGrantType.CLIENT_CREDENTIALS)
            .tokenUri(baseUrl + "/token")
            .jwkSetUri(primaryJwksUri)
            .build();

    final var configRepo = createConfigRepository(registrationId, oidcConfig);
    final var jwsKeySelectorFactory = new JWSKeySelectorFactory();
    final var securityConfig = new SecurityConfiguration();
    final var tokenValidatorFactory = new TokenValidatorFactory(securityConfig, configRepo);
    final var decoderFactory =
        new OidcAccessTokenDecoderFactory(jwsKeySelectorFactory, tokenValidatorFactory, configRepo);

    return decoderFactory.createAccessTokenDecoder(clientRegistration);
  }

  private OidcAuthenticationConfigurationRepository createConfigRepository(
      final String registrationId, final OidcAuthenticationConfiguration config) {
    final var securityConfig = new SecurityConfiguration();
    return new OidcAuthenticationConfigurationRepository(securityConfig) {
      @Override
      protected Map<String, OidcAuthenticationConfiguration> initializeProviders(
          final SecurityConfiguration ignored) {
        return Map.of(registrationId, config);
      }
    };
  }

  private RSAKey generateKey(final String keyId) throws JOSEException {
    return new RSAKeyGenerator(2048)
        .algorithm(JWSAlgorithm.RS256)
        .keyID(keyId)
        .keyUse(KeyUse.SIGNATURE)
        .generate();
  }

  private String signToken(final RSAKey key, final String issuer) throws JOSEException {
    final var jwt =
        new SignedJWT(
            new JWSHeader.Builder(JWSAlgorithm.RS256).type(JWT).keyID(key.getKeyID()).build(),
            new JWTClaimsSet.Builder()
                .subject("alice")
                .issuer(issuer)
                .expirationTime(new Date(System.currentTimeMillis() + 60_000))
                .build());
    jwt.sign(new RSASSASigner(key));
    return jwt.serialize();
  }

  private void mockJwksEndpoint(final String path, final String publicKeyJson) {
    final var body =
        publicKeyJson.isEmpty() ? "{\"keys\":[]}" : "{\"keys\":[" + publicKeyJson + "]}";
    WireMock.stubFor(
        WireMock.get(WireMock.urlEqualTo(path))
            .willReturn(WireMock.jsonResponse(body, HttpStatus.OK.value())));
  }
}
