/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.operate.webapp.security.oauth2;

import static io.camunda.operate.webapp.security.SecurityTestUtil.signAndSerialize;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.fail;

import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.junit5.WireMockRuntimeInfo;
import com.github.tomakehurst.wiremock.junit5.WireMockTest;
import com.github.tomakehurst.wiremock.matching.RequestPatternBuilder;
import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JOSEObjectType;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSAlgorithm.Family;
import com.nimbusds.jose.jwk.AsymmetricJWK;
import com.nimbusds.jose.jwk.Curve;
import com.nimbusds.jose.jwk.ECKey;
import com.nimbusds.jose.jwk.KeyUse;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.gen.ECKeyGenerator;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import io.camunda.identity.sdk.IdentityConfiguration;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.Mock;
import org.springframework.http.HttpStatus;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.util.ReflectionTestUtils;

@WireMockTest
public class JwtDecoderIT {

  private final MockEnvironment environment = new MockEnvironment();
  @Mock private IdentityJwt2AuthenticationTokenConverter jwtConverter;
  private final WireMockRuntimeInfo wireMockInfo;
  private final String authServerMockUrl;
  private JwtDecoder decoder;

  public JwtDecoderIT(final WireMockRuntimeInfo wireMockInfo) {
    this.wireMockInfo = wireMockInfo;
    authServerMockUrl = wireMockInfo.getHttpBaseUrl();
  }

  @BeforeEach
  public void setup() {
    final IdentityConfiguration identityConfiguration =
        new IdentityConfiguration(null, authServerMockUrl, null, null, null);
    final IdentityOAuth2WebConfigurer identityOAuth2WebConfigurer =
        new IdentityOAuth2WebConfigurer(environment, identityConfiguration, jwtConverter);

    decoder = ReflectionTestUtils.invokeMethod(identityOAuth2WebConfigurer, "jwtDecoder");
  }

  @ParameterizedTest
  @MethodSource("basicAlgTestParameters")
  public void testDecodeJwtWithNoAlgFieldInJwkResponse(
      final JWSAlgorithm algorithm, final Curve curve) throws JOSEException {
    // given
    final JwtKeys jwtKeys = new JwtKeys(algorithm, curve);
    System.out.println("publicKey=" + jwtKeys.publicKey);

    // remove alg field from mocked server response and assert it was removed to cover this use case
    final String withoutAlg = jwtKeys.publicKey.replaceFirst(",\"alg\":\"[^\"]*\"", "");
    final String authServerResponseBody = "{\"keys\":[" + withoutAlg + "]}";
    System.out.println("authServerResponse=" + authServerResponseBody);
    assertFalse(authServerResponseBody.contains("alg\":"));

    wireMockInfo
        .getWireMock()
        .register(
            WireMock.get(WireMock.urlMatching(".*/protocol/openid-connect/certs"))
                .willReturn(WireMock.jsonResponse(authServerResponseBody, HttpStatus.OK.value())));

    // when - then
    assertDoesNotThrow(() -> decoder.decode(jwtKeys.serializedJwt));
    wireMockInfo.getWireMock().verifyThat(1, RequestPatternBuilder.allRequests());
  }

  @ParameterizedTest
  @MethodSource("basicAlgTestParameters")
  public void testDecodeJwtsWithNoTypeHeader(final JWSAlgorithm algorithm, final Curve curve)
      throws JOSEException {
    // given
    final JwtKeys jwtKeys = new JwtKeys(algorithm, curve, null);
    System.out.println("publicKey=" + jwtKeys.publicKey);
    final String authServerResponseBody = "{\"keys\":[" + jwtKeys.publicKey + "]}";
    System.out.println("authServerResponse=" + authServerResponseBody);

    wireMockInfo
        .getWireMock()
        .register(
            WireMock.get(WireMock.urlMatching(".*/protocol/openid-connect/certs"))
                .willReturn(WireMock.jsonResponse(authServerResponseBody, HttpStatus.OK.value())));

    // when - then
    assertDoesNotThrow(() -> decoder.decode(jwtKeys.serializedJwt));
    wireMockInfo.getWireMock().verifyThat(1, RequestPatternBuilder.allRequests());
  }

  @ParameterizedTest
  @MethodSource("jwtTestParameters")
  public void testDecodeJwtWithVariousAlgorithms(final JWSAlgorithm algorithm, final Curve curve)
      throws JOSEException {
    // given
    final JwtKeys jwtKeys = new JwtKeys(algorithm, curve);
    System.out.println("publicKey=" + jwtKeys.publicKey);
    final String authServerResponseBody = "{\"keys\":[" + jwtKeys.publicKey + "]}";
    System.out.println("authServerResponse=" + authServerResponseBody);

    wireMockInfo
        .getWireMock()
        .register(
            WireMock.get(WireMock.urlMatching(".*/protocol/openid-connect/certs"))
                .willReturn(WireMock.jsonResponse(authServerResponseBody, HttpStatus.OK.value())));

    // when - then
    assertDoesNotThrow(() -> decoder.decode(jwtKeys.serializedJwt));
    wireMockInfo.getWireMock().verifyThat(1, RequestPatternBuilder.allRequests());
  }

  private static Stream<Arguments> jwtTestParameters() {
    return Stream.of(
        Arguments.of(JWSAlgorithm.RS256, null),
        Arguments.of(JWSAlgorithm.RS384, null),
        Arguments.of(JWSAlgorithm.RS512, null),
        Arguments.of(JWSAlgorithm.ES256, Curve.P_256),
        Arguments.of(JWSAlgorithm.ES384, Curve.P_384),
        Arguments.of(JWSAlgorithm.ES512, Curve.P_521));
  }

  private static Stream<Arguments> basicAlgTestParameters() {
    return Stream.of(
        Arguments.of(JWSAlgorithm.RS256, null), Arguments.of(JWSAlgorithm.ES256, Curve.P_256));
  }

  private static RSAKey getRsaJWK(final JWSAlgorithm alg) throws JOSEException {
    return new RSAKeyGenerator(2048)
        .keyID("123")
        .keyUse(KeyUse.SIGNATURE)
        .algorithm(alg)
        .generate();
  }

  private static ECKey getEcJWK(final JWSAlgorithm alg, final Curve curve) throws JOSEException {
    return new ECKeyGenerator(curve)
        .algorithm(alg)
        .keyUse(KeyUse.SIGNATURE)
        .keyID("345")
        .generate();
  }

  private static class JwtKeys {
    AsymmetricJWK jwk;
    String serializedJwt;
    String publicKey;

    JwtKeys(final JWSAlgorithm algorithm, final Curve curve, final JOSEObjectType type)
        throws JOSEException {
      if (Family.RSA.contains(algorithm)) {
        jwk = getRsaJWK(algorithm);
        serializedJwt = signAndSerialize((RSAKey) jwk, algorithm, type);
        publicKey = ((RSAKey) jwk).toPublicJWK().toJSONString();
      } else if (Family.EC.contains(algorithm)) {
        jwk = getEcJWK(algorithm, curve);
        serializedJwt = signAndSerialize((ECKey) jwk, algorithm, type);
        publicKey = ((ECKey) jwk).toPublicJWK().toJSONString();
      } else {
        serializedJwt = null;
        fail("unsupported algorithm type");
      }
    }

    JwtKeys(final JWSAlgorithm algorithm, final Curve curve) throws JOSEException {
      this(algorithm, curve, JOSEObjectType.JWT);
    }
  }
}
