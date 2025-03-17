/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.junit5.WireMockRuntimeInfo;
import com.github.tomakehurst.wiremock.junit5.WireMockTest;
import com.github.tomakehurst.wiremock.matching.RequestPatternBuilder;
import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JOSEObjectType;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.JWSSigner;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.KeyUse;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import io.camunda.optimize.AbstractCCSMIT;
import io.camunda.optimize.rest.security.AbstractSecurityConfigurerAdapter;
import io.camunda.optimize.rest.security.ccsm.CCSMSecurityConfigurerAdapter;
import io.camunda.optimize.rest.security.cloud.CCSaaSSecurityConfigurerAdapter;
import java.util.Date;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.jwt.BadJwtException;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtValidationException;
import org.springframework.test.util.ReflectionTestUtils;

@WireMockTest
public class JwtDecoderIT extends AbstractCCSMIT {

  private final WireMockRuntimeInfo wireMockInfo;
  private final String authServerMockUrl;

  public JwtDecoderIT(final WireMockRuntimeInfo wireMockInfo) {
    this.wireMockInfo = wireMockInfo;
    authServerMockUrl = wireMockInfo.getHttpBaseUrl();
  }

  @BeforeEach
  public void setup() {
    embeddedOptimizeExtension
        .getConfigurationService()
        .getOptimizeApiConfiguration()
        .setJwtSetUri(authServerMockUrl + "/protocol/openid-connect/certs");
    embeddedOptimizeExtension
        .getConfigurationService()
        .getAuthConfiguration()
        .getCloudAuthConfiguration()
        .setAudience("optimize");
    embeddedOptimizeExtension
        .getConfigurationService()
        .getAuthConfiguration()
        .getCloudAuthConfiguration()
        .setClusterId("456");
    embeddedOptimizeExtension
        .getConfigurationService()
        .getAuthConfiguration()
        .getCcsmAuthConfiguration()
        .setAudience("optimize");
  }

  @ParameterizedTest
  @MethodSource("decoderProvider")
  public void testDecodeRs256JwtWithNoAlgFieldInJwkResponse(
      final String type, final String methodName) throws JOSEException {
    // given
    final RSAKey rsaJWK = getRsaJWK(JWSAlgorithm.RS256);
    final String serializedJwt = signAndSerialize(rsaJWK, JWSAlgorithm.RS256);
    final String publicKey = rsaJWK.toPublicJWK().toJSONString();

    final JwtDecoder decoder = createDecoder(methodName, type);

    // remove alg field from mocked server response and assert it was removed to cover this use case
    final String withoutAlg = publicKey.replaceFirst("\"alg\":\".*\",", "");
    final String authServerResponseBody = "{\"keys\":[" + withoutAlg + "]}";
    assertFalse(authServerResponseBody.contains("alg\":"));
    wireMockInfo
        .getWireMock()
        .register(
            WireMock.get(WireMock.urlMatching(".*/protocol/openid-connect/certs"))
                .willReturn(WireMock.jsonResponse(authServerResponseBody, HttpStatus.OK.value())));

    // when - then
    assertDoesNotThrow(() -> decoder.decode(serializedJwt));
    wireMockInfo.getWireMock().verifyThat(1, RequestPatternBuilder.allRequests());
  }

  @ParameterizedTest
  @MethodSource("decoderProvider")
  public void testDecodeRs256Jwt(final String type, final String methodName) throws JOSEException {
    // given
    final RSAKey rsaJWK = getRsaJWK(JWSAlgorithm.RS256);
    final String serializedJwt = signAndSerialize(rsaJWK, JWSAlgorithm.RS256);
    final String publicKey = rsaJWK.toPublicJWK().toJSONString();

    final JwtDecoder decoder = createDecoder(methodName, type);

    final String authServerResponseBody = "{\"keys\":[" + publicKey + "]}";

    wireMockInfo
        .getWireMock()
        .register(
            WireMock.get(WireMock.urlMatching(".*/protocol/openid-connect/certs"))
                .willReturn(WireMock.jsonResponse(authServerResponseBody, HttpStatus.OK.value())));

    // when - then
    assertDoesNotThrow(() -> decoder.decode(serializedJwt));
    wireMockInfo.getWireMock().verifyThat(1, RequestPatternBuilder.allRequests());
  }

  @ParameterizedTest
  @MethodSource("decoderProvider")
  public void testDecodeRs256JwtExpired(final String type, final String methodName)
      throws JOSEException {
    // given
    final RSAKey rsaJWK = getRsaJWK(JWSAlgorithm.RS256);
    final String serializedJwt =
        signAndSerialize(rsaJWK, JWSAlgorithm.RS256, getExpiredClaimsSet());
    final String publicKey = rsaJWK.toPublicJWK().toJSONString();
    final JwtDecoder decoder = createDecoder(methodName, type);

    final String authServerResponseBody = "{\"keys\":[" + publicKey + "]}";

    wireMockInfo
        .getWireMock()
        .register(
            WireMock.get(WireMock.urlMatching(".*/protocol/openid-connect/certs"))
                .willReturn(WireMock.jsonResponse(authServerResponseBody, HttpStatus.OK.value())));

    // when - then
    final Exception exception =
        assertThrows(JwtValidationException.class, () -> decoder.decode(serializedJwt));
    wireMockInfo.getWireMock().verifyThat(1, RequestPatternBuilder.allRequests());
    assertThat(exception.getMessage()).containsIgnoringCase("Jwt expired");
  }

  @ParameterizedTest
  @MethodSource("decoderProvider")
  public void testDecodeRs256JwtInvalidSignature(final String type, final String methodName)
      throws JOSEException {
    // given
    final RSAKey rsaJWK = getRsaJWK(JWSAlgorithm.RS256);
    final String serializedJwt = signAndSerialize(rsaJWK, JWSAlgorithm.RS256);
    final String publicKey = rsaJWK.toPublicJWK().toJSONString();

    final JwtDecoder decoder = createDecoder(methodName, type);

    final String authServerResponseBody = "{\"keys\":[" + publicKey + "]}";

    wireMockInfo
        .getWireMock()
        .register(
            WireMock.get(WireMock.urlMatching(".*/protocol/openid-connect/certs"))
                .willReturn(WireMock.jsonResponse(authServerResponseBody, HttpStatus.OK.value())));

    final String[] jwtParts = serializedJwt.split("\\.");
    if (jwtParts.length == 3) {
      final String unsignedJwt = jwtParts[0] + "." + jwtParts[1] + ".anySignature";
      // when - then
      final Exception exception =
          assertThrows(BadJwtException.class, (() -> decoder.decode(unsignedJwt)));
      wireMockInfo.getWireMock().verifyThat(1, RequestPatternBuilder.allRequests());
      assertThat(exception.getMessage()).containsIgnoringCase("Invalid Signature");
    } else {
      throw new RuntimeException("Invalid JWT token format");
    }
  }

  private RSAKey getRsaJWK(final JWSAlgorithm alg) throws JOSEException {
    return new RSAKeyGenerator(2048)
        .keyID("123")
        .keyUse(KeyUse.SIGNATURE)
        .algorithm(alg)
        .generate();
  }

  public static String signAndSerialize(
      final RSAKey rsaKey, final JWSAlgorithm alg, final JWTClaimsSet claimsSet)
      throws JOSEException {
    // Create RSA-signer with the private key
    final JWSSigner rsaSigner = new RSASSASigner(rsaKey);

    final SignedJWT rsaSignedJWT =
        new SignedJWT(
            new JWSHeader.Builder(alg).type(JOSEObjectType.JWT).keyID(rsaKey.getKeyID()).build(),
            claimsSet);

    rsaSignedJWT.sign(rsaSigner);
    return rsaSignedJWT.serialize();
  }

  public static String signAndSerialize(final RSAKey rsaKey, final JWSAlgorithm alg)
      throws JOSEException {
    return signAndSerialize(rsaKey, alg, getDefaultClaimsSet());
  }

  public static JWTClaimsSet getDefaultClaimsSet() {
    // prepare default JWT claims set
    return new JWTClaimsSet.Builder()
        .subject("alice")
        .audience("optimize")
        .issuer("http://localhost")
        .claim("https://camunda.com/clusterId", "456")
        .expirationTime(new Date(new Date().getTime() + 60 * 1000))
        .build();
  }

  public static JWTClaimsSet getExpiredClaimsSet() {
    return new JWTClaimsSet.Builder()
        .subject("alice")
        .audience("optimize")
        .issuer("http://localhost")
        .claim("https://camunda.com/clusterId", "456")
        .expirationTime(new Date(System.currentTimeMillis() - 100000))
        .build();
  }

  private static JwtDecoder createDecoder(final String methodName, final String type) {
    final AbstractSecurityConfigurerAdapter configurerAdapter;
    if ("SaaS".equalsIgnoreCase(type)) {
      configurerAdapter =
          new CCSaaSSecurityConfigurerAdapter(
              embeddedOptimizeExtension.getConfigurationService(), null, null, null, null, null);
    } else if ("CCSM".equalsIgnoreCase(type)) {
      configurerAdapter =
          new CCSMSecurityConfigurerAdapter(
              embeddedOptimizeExtension.getConfigurationService(), null, null, null, null);
    } else {
      throw new IllegalArgumentException("Invalid type: " + type);
    }
    return ReflectionTestUtils.invokeMethod(configurerAdapter, methodName);
  }

  static Stream<Arguments> decoderProvider() {
    return Stream.of(
        Arguments.of("SaaS", "publicApiJwtDecoder"),
        Arguments.of("CCSM", "publicApiJwtDecoder"),
        Arguments.of("SaaS", "publicApiJwtDecoder"));
  }
}
