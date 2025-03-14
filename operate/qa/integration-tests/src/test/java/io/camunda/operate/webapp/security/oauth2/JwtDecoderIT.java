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
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.junit5.WireMockRuntimeInfo;
import com.github.tomakehurst.wiremock.junit5.WireMockTest;
import com.github.tomakehurst.wiremock.matching.RequestPatternBuilder;
import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.jwk.Curve;
import com.nimbusds.jose.jwk.ECKey;
import com.nimbusds.jose.jwk.KeyUse;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.gen.ECKeyGenerator;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import io.camunda.identity.sdk.IdentityConfiguration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
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

  @Test
  public void testDecodeRs256JwtWithNoAlgFieldInJwkResponse() throws JOSEException {
    // given
    final RSAKey rsaJWK = getRsaJWK(JWSAlgorithm.RS256);
    final String serializedJwt = signAndSerialize(rsaJWK, JWSAlgorithm.RS256);
    final String publicKey = rsaJWK.toPublicJWK().toJSONString();

    // remove alg field from mocked server response and assert it was removed to cover this use case
    final String withoutAlg = publicKey.replaceFirst("\"alg\":\".*\",", "");
    final String authServerResponseBody = "{\"keys\":[" + withoutAlg + "]}";
    System.out.println("authServerResponse=" + authServerResponseBody);
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

  @Test
  public void testDecodeES256JwtWithNoAlgFieldInJwkResponse() throws JOSEException {
    // given
    final ECKey ecJWK = getEcJWK(JWSAlgorithm.ES256, Curve.P_256);
    final String ecSerializedJwt = signAndSerialize(ecJWK, JWSAlgorithm.ES256);
    final String publicKey = ecJWK.toPublicJWK().toJSONString();
    System.out.println("publicKey=" + publicKey);

    // remove alg field from mocked server response and assert it was removed to cover this use case
    final String withoutAlg = publicKey.replaceFirst(",\"alg\":\".*\"", "");
    final String authServerResponseBody = "{\"keys\":[" + withoutAlg + "]}";
    System.out.println("authServerResponse=" + authServerResponseBody);
    assertFalse(authServerResponseBody.contains("alg\":"));

    wireMockInfo
        .getWireMock()
        .register(
            WireMock.get(WireMock.urlMatching(".*/protocol/openid-connect/certs"))
                .willReturn(WireMock.jsonResponse(authServerResponseBody, HttpStatus.OK.value())));

    // when - then
    assertDoesNotThrow(() -> decoder.decode(ecSerializedJwt));
    wireMockInfo.getWireMock().verifyThat(1, RequestPatternBuilder.allRequests());
  }

  @Test
  public void testDecodeRS384Jwt() throws JOSEException {
    // given
    final RSAKey rsaJWK = getRsaJWK(JWSAlgorithm.RS384);
    final String serializedJwt = signAndSerialize(rsaJWK, JWSAlgorithm.RS384);
    final String publicKey = rsaJWK.toPublicJWK().toJSONString();

    final String authServerResponseBody = "{\"keys\":[" + publicKey + "]}";
    System.out.println("authServerResponse=" + authServerResponseBody);
    assertTrue(authServerResponseBody.contains("alg\":"));

    wireMockInfo
        .getWireMock()
        .register(
            WireMock.get(WireMock.urlMatching(".*/protocol/openid-connect/certs"))
                .willReturn(WireMock.jsonResponse(authServerResponseBody, HttpStatus.OK.value())));

    // when - then
    assertDoesNotThrow(() -> decoder.decode(serializedJwt));
    wireMockInfo.getWireMock().verifyThat(1, RequestPatternBuilder.allRequests());
  }

  @Test
  public void testDecodeRS512Jwt() throws JOSEException {
    // given
    final RSAKey rsaJWK = getRsaJWK(JWSAlgorithm.RS512);
    final String serializedJwt = signAndSerialize(rsaJWK, JWSAlgorithm.RS512);
    final String publicKey = rsaJWK.toPublicJWK().toJSONString();

    final String authServerResponseBody = "{\"keys\":[" + publicKey + "]}";
    System.out.println("authServerResponse=" + authServerResponseBody);

    wireMockInfo
        .getWireMock()
        .register(
            WireMock.get(WireMock.urlMatching(".*/protocol/openid-connect/certs"))
                .willReturn(WireMock.jsonResponse(authServerResponseBody, HttpStatus.OK.value())));

    // when - then
    assertDoesNotThrow(() -> decoder.decode(serializedJwt));
    wireMockInfo.getWireMock().verifyThat(1, RequestPatternBuilder.allRequests());
  }

  @Test
  public void testDecodeES384Jwt() throws JOSEException {
    // given
    final ECKey ecJWK = getEcJWK(JWSAlgorithm.ES384, Curve.P_384);
    final String ecSerializedJwt = signAndSerialize(ecJWK, JWSAlgorithm.ES384);
    final String publicKey = ecJWK.toPublicJWK().toJSONString();
    System.out.println("publicKey=" + publicKey);

    final String authServerResponseBody = "{\"keys\":[" + publicKey + "]}";
    System.out.println("authServerResponse=" + authServerResponseBody);

    wireMockInfo
        .getWireMock()
        .register(
            WireMock.get(WireMock.urlMatching(".*/protocol/openid-connect/certs"))
                .willReturn(WireMock.jsonResponse(authServerResponseBody, HttpStatus.OK.value())));

    // when - then
    assertDoesNotThrow(() -> decoder.decode(ecSerializedJwt));
    wireMockInfo.getWireMock().verifyThat(1, RequestPatternBuilder.allRequests());
  }

  @Test
  public void testDecodeES512Jwt() throws JOSEException {
    // given
    final ECKey ecJWK = getEcJWK(JWSAlgorithm.ES512, Curve.P_521);
    final String ecSerializedJwt = signAndSerialize(ecJWK, JWSAlgorithm.ES512);
    final String publicKey = ecJWK.toPublicJWK().toJSONString();
    System.out.println("publicKey=" + publicKey);

    final String authServerResponseBody = "{\"keys\":[" + publicKey + "]}";
    System.out.println("authServerResponse=" + authServerResponseBody);

    wireMockInfo
        .getWireMock()
        .register(
            WireMock.get(WireMock.urlMatching(".*/protocol/openid-connect/certs"))
                .willReturn(WireMock.jsonResponse(authServerResponseBody, HttpStatus.OK.value())));

    // when - then
    assertDoesNotThrow(() -> decoder.decode(ecSerializedJwt));
    wireMockInfo.getWireMock().verifyThat(1, RequestPatternBuilder.allRequests());
  }

  private RSAKey getRsaJWK(final JWSAlgorithm alg) throws JOSEException {
    return new RSAKeyGenerator(2048)
        .keyID("123")
        .keyUse(KeyUse.SIGNATURE)
        .algorithm(alg)
        .generate();
  }

  private ECKey getEcJWK(final JWSAlgorithm alg, final Curve curve) throws JOSEException {
    return new ECKeyGenerator(curve)
        .algorithm(alg)
        .keyUse(KeyUse.SIGNATURE)
        .keyID("345")
        .generate();
  }
}
