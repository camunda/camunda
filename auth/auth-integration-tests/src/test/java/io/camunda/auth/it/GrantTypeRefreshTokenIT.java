/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.auth.it;

import static org.assertj.core.api.Assertions.assertThat;

import dasniko.testcontainers.keycloak.KeycloakContainer;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration;
import org.springframework.boot.resttestclient.TestRestTemplate;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureTestRestTemplate;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Integration test validating the refresh_token grant type against a real Keycloak IDP. Uses ROPC
 * to obtain an initial token pair, then validates refresh token operations.
 */
@SpringBootTest(
    classes = GrantTypeRefreshTokenIT.TestApp.class,
    webEnvironment = WebEnvironment.RANDOM_PORT)
@AutoConfigureTestRestTemplate
class GrantTypeRefreshTokenIT {

  private static final String REALM = "grant-type-refresh";
  private static final String CLIENT_ID = "refresh-client";
  private static final String CLIENT_SECRET = "refresh-secret";
  private static final String TEST_USER = "refreshuser";
  private static final String TEST_PASSWORD = "refreshpassword";

  private static final KeycloakContainer KEYCLOAK = SharedKeycloakContainer.getInstance();

  @LocalServerPort private int port;

  @Autowired private TestRestTemplate restTemplate;

  @BeforeAll
  static void setUpRealm() {
    final var client = KeycloakTestSupport.createConfidentialClient(CLIENT_ID, CLIENT_SECRET);
    KeycloakTestSupport.createRealm(KEYCLOAK, REALM, List.of(client));
    KeycloakTestSupport.createUser(KEYCLOAK, REALM, TEST_USER, TEST_PASSWORD);
  }

  @DynamicPropertySource
  static void configureProperties(final DynamicPropertyRegistry registry) {
    registry.add("camunda.auth.method", () -> "oidc");
    registry.add(
        "camunda.security.authentication.oidc.issuerUri",
        () -> KeycloakTestSupport.issuerUri(KEYCLOAK, REALM));
    registry.add("camunda.security.authentication.oidc.clientId", () -> CLIENT_ID);
    registry.add("camunda.security.authentication.oidc.clientSecret", () -> CLIENT_SECRET);
    registry.add(
        "camunda.security.authentication.oidc.tokenUri",
        () -> KeycloakTestSupport.tokenEndpoint(KEYCLOAK, REALM));
    registry.add(
        "camunda.security.authentication.oidc.authorizationUri",
        () -> KeycloakTestSupport.authorizationUri(KEYCLOAK, REALM));
    registry.add(
        "camunda.security.authentication.oidc.jwkSetUri",
        () -> KeycloakTestSupport.jwkSetUri(KEYCLOAK, REALM));
    registry.add("camunda.security.authentication.oidc.grantType", () -> "client_credentials");
    registry.add("camunda.auth.security.webapp-enabled", () -> "false");
  }

  @Test
  void shouldRefreshAccessToken() {
    // Obtain initial token pair via ROPC
    final Map<String, Object> initialResponse =
        KeycloakTestSupport.acquireTokenWithPassword(
            KeycloakTestSupport.tokenEndpoint(KEYCLOAK, REALM),
            CLIENT_ID,
            CLIENT_SECRET,
            TEST_USER,
            TEST_PASSWORD);
    final String refreshTokenValue = (String) initialResponse.get("refresh_token");
    assertThat(refreshTokenValue).isNotBlank();

    // Refresh
    final Map<String, Object> refreshedResponse =
        KeycloakTestSupport.refreshToken(
            KeycloakTestSupport.tokenEndpoint(KEYCLOAK, REALM),
            CLIENT_ID,
            CLIENT_SECRET,
            refreshTokenValue);

    assertThat(refreshedResponse).containsKey("access_token");
    final String newAccessToken = (String) refreshedResponse.get("access_token");
    assertThat(newAccessToken).isNotBlank();
  }

  @Test
  void shouldAcceptRefreshedTokenOnProtectedEndpoint() {
    final Map<String, Object> initialResponse =
        KeycloakTestSupport.acquireTokenWithPassword(
            KeycloakTestSupport.tokenEndpoint(KEYCLOAK, REALM),
            CLIENT_ID,
            CLIENT_SECRET,
            TEST_USER,
            TEST_PASSWORD);
    final String refreshTokenValue = (String) initialResponse.get("refresh_token");

    final Map<String, Object> refreshedResponse =
        KeycloakTestSupport.refreshToken(
            KeycloakTestSupport.tokenEndpoint(KEYCLOAK, REALM),
            CLIENT_ID,
            CLIENT_SECRET,
            refreshTokenValue);
    final String newAccessToken = (String) refreshedResponse.get("access_token");

    final ResponseEntity<String> response = performAuthenticatedGet("/v1/test", newAccessToken);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(response.getBody()).isEqualTo("ok");
  }

  @Test
  void shouldRejectInvalidRefreshToken() {
    final KeycloakTestSupport.TokenResponse response =
        KeycloakTestSupport.postTokenRequest(
            KeycloakTestSupport.tokenEndpoint(KEYCLOAK, REALM),
            "grant_type=refresh_token"
                + "&client_id="
                + CLIENT_ID
                + "&client_secret="
                + CLIENT_SECRET
                + "&refresh_token=invalid-garbage-token");

    assertThat(response.statusCode()).isNotEqualTo(200);
  }

  @Test
  @SuppressWarnings("unchecked")
  void shouldReturnNewAccessTokenDifferentFromOriginal() {
    final Map<String, Object> initialResponse =
        KeycloakTestSupport.acquireTokenWithPassword(
            KeycloakTestSupport.tokenEndpoint(KEYCLOAK, REALM),
            CLIENT_ID,
            CLIENT_SECRET,
            TEST_USER,
            TEST_PASSWORD);
    final String originalAccessToken = (String) initialResponse.get("access_token");
    final String refreshTokenValue = (String) initialResponse.get("refresh_token");

    final Map<String, Object> refreshedResponse =
        KeycloakTestSupport.refreshToken(
            KeycloakTestSupport.tokenEndpoint(KEYCLOAK, REALM),
            CLIENT_ID,
            CLIENT_SECRET,
            refreshTokenValue);
    final String newAccessToken = (String) refreshedResponse.get("access_token");

    // Tokens should be different (different jti/exp)
    assertThat(newAccessToken).isNotEqualTo(originalAccessToken);

    // Verify both are valid JWTs with correct claims
    final ResponseEntity<Map> response =
        performAuthenticatedGet("/v1/user", newAccessToken, Map.class);
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    final Map<String, Object> claims = response.getBody();
    assertThat(claims).isNotNull();
    assertThat(claims).containsKey("jti");
    assertThat(claims).containsKey("exp");
  }

  private ResponseEntity<String> performAuthenticatedGet(
      final String path, final String accessToken) {
    return performAuthenticatedGet(path, accessToken, String.class);
  }

  private <T> ResponseEntity<T> performAuthenticatedGet(
      final String path, final String accessToken, final Class<T> responseType) {
    final HttpHeaders headers = new HttpHeaders();
    headers.setBearerAuth(accessToken);
    final HttpEntity<Void> entity = new HttpEntity<>(headers);
    return restTemplate.exchange(
        "http://localhost:" + port + path, HttpMethod.GET, entity, responseType);
  }

  @SpringBootApplication(exclude = {DataSourceAutoConfiguration.class})
  static class TestApp {

    @RestController
    static class TestController {

      @GetMapping("/v1/test")
      String test() {
        return "ok";
      }

      @GetMapping("/v1/user")
      Map<String, Object> user(
          @org.springframework.security.core.annotation.AuthenticationPrincipal final Jwt jwt) {
        return jwt.getClaims();
      }
    }
  }
}
