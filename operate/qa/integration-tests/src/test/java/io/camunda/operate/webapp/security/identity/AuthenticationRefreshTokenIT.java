/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.operate.webapp.security.identity;

import static io.camunda.operate.OperateProfileService.IDENTITY_AUTH_PROFILE;
import static io.camunda.operate.webapp.security.SecurityTestUtil.getRsaJWK;
import static io.camunda.operate.webapp.security.SecurityTestUtil.signAndSerialize;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.junit5.WireMockRuntimeInfo;
import com.github.tomakehurst.wiremock.junit5.WireMockTest;
import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jwt.JWTClaimsSet;
import io.camunda.application.commons.security.CamundaSecurityConfiguration.CamundaSecurityProperties;
import io.camunda.identity.sdk.Identity;
import io.camunda.identity.sdk.IdentityConfiguration;
import io.camunda.identity.sdk.IdentityConfiguration.Type;
import io.camunda.identity.sdk.authentication.Tokens;
import io.camunda.identity.sdk.authentication.exception.TokenExpiredException;
import io.camunda.identity.sdk.impl.dto.AccessTokenDto;
import io.camunda.operate.util.SpringContextHolder;
import io.camunda.operate.util.apps.nobeans.TestApplicationWithNoBeans;
import java.util.Date;
import net.minidev.json.JSONArray;
import net.minidev.json.JSONObject;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.util.ReflectionTestUtils;

@SpringBootTest(
    classes = {
      TestApplicationWithNoBeans.class,
      IdentityAuthentication.class,
      CamundaSecurityProperties.class,
      PermissionConverter.class,
      IdentityRetryService.class
    },
    properties = {
      "camunda.identity.audience=test-client",
      "camunda.identity.clientId=test-client",
      "camunda.identity.clientSecret=secret",
    },
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles({IDENTITY_AUTH_PROFILE, "test"})
@WireMockTest
public class AuthenticationRefreshTokenIT {

  @Autowired private ApplicationContext applicationContext;
  @Mock private Identity identity;
  private final WireMockRuntimeInfo wireMockInfo;
  private final ObjectMapper objectMapper = new ObjectMapper();
  @InjectMocks private IdentityAuthentication identityAuthentication;

  public AuthenticationRefreshTokenIT(final WireMockRuntimeInfo wireMockRuntimeInfo) {
    wireMockInfo = wireMockRuntimeInfo;
  }

  @BeforeEach
  public void setup() {
    new SpringContextHolder().setApplicationContext(applicationContext);
    final IdentityConfiguration identityConfiguration =
        applicationContext.getBean(IdentityConfiguration.class);
    // setting properties with reflection because we need the wiremock server url here
    ReflectionTestUtils.setField(
        identityConfiguration, "issuerBackendUrl", wireMockInfo.getHttpBaseUrl());
    ReflectionTestUtils.setField(identityConfiguration, "issuer", wireMockInfo.getHttpBaseUrl());
    identityAuthentication = new IdentityAuthentication();
  }

  @Test
  public void shouldSuccessfullyRequestNewAccessTokenViaRefreshToken()
      throws JOSEException, JsonProcessingException {
    // given

    // create expired access token and non-JWT refresh token
    final RSAKey rsaJWK = getRsaJWK(JWSAlgorithm.RS256);
    final String expiredToken =
        signAndSerialize(
            rsaJWK, JWSAlgorithm.RS256, getClaimsSet(new Date(new Date().getTime() - 60 * 1000)));

    final String refreshToken = "abcdef";
    final String tokenType = Type.MICROSOFT.name();
    final Tokens tokens = new Tokens(expiredToken, refreshToken, 5000L, "", tokenType);

    // mock public key response
    final String publicKey = rsaJWK.toPublicJWK().toJSONString();
    final String authServerResponseBody = "{\"keys\":[" + publicKey + "]}";
    wireMockInfo
        .getWireMock()
        .register(
            WireMock.get(WireMock.urlMatching(".*/protocol/openid-connect/certs"))
                .willReturn(WireMock.jsonResponse(authServerResponseBody, HttpStatus.OK.value())));

    // mock renew token via refresh token server response (new valid tokens)
    final String validToken =
        signAndSerialize(
            rsaJWK, JWSAlgorithm.RS256, getClaimsSet(new Date(new Date().getTime() + 60 * 1000)));
    final AccessTokenDto newAccessToken =
        new AccessTokenDto(validToken, "ghijkl", "id", 50000L, null, null);
    wireMockInfo
        .getWireMock()
        .register(
            WireMock.post(WireMock.urlMatching(".*/protocol/openid-connect/token"))
                .willReturn(
                    WireMock.jsonResponse(
                        objectMapper.writeValueAsString(newAccessToken), HttpStatus.OK.value())));

    // when - then
    // throws token expired exception but does not log out
    Assertions.assertThrows(
        TokenExpiredException.class, () -> identityAuthentication.authenticate(tokens));
    // methode will request new access token via the refresh token successfully
    assertTrue(identityAuthentication.isAuthenticated());
  }

  private JWTClaimsSet getClaimsSet(final Date expirationTime) {
    final var permissionsClaim = new JSONObject();
    final String[] permissions = new String[] {"read:*"};
    permissionsClaim.put("test-client", permissions);

    final var audClaim = new JSONArray();
    final String aud = "test-client";
    audClaim.add(aud);

    return new JWTClaimsSet.Builder()
        .subject("alice")
        .audience("test-client")
        .claim("permissions", permissionsClaim)
        .claim("aud", audClaim)
        .issuer("http://localhost")
        .expirationTime(expirationTime)
        .build();
  }
}
