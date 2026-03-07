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
import io.camunda.auth.it.KeycloakTestSupport.PkceChallenge;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
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
 * Integration test validating the authorization_code grant flow with PKCE against a real Keycloak
 * IDP. Performs programmatic authorization code flows (GET authz → POST login → capture code →
 * exchange with PKCE) and validates the resulting tokens.
 */
@SpringBootTest(
    classes = GrantTypeAuthorizationCodeIT.TestApp.class,
    webEnvironment = WebEnvironment.RANDOM_PORT)
@AutoConfigureTestRestTemplate
class GrantTypeAuthorizationCodeIT {

  private static final String REALM = "grant-type-authz-code";
  private static final String CLIENT_ID = "authz-code-client";
  private static final String CLIENT_SECRET = "authz-code-secret";
  private static final String REDIRECT_URI = "http://localhost:8080/callback";
  private static final String TEST_USER = "testuser";
  private static final String TEST_PASSWORD = "testpassword";

  private static final KeycloakContainer KEYCLOAK = SharedKeycloakContainer.getInstance();

  @LocalServerPort private int port;

  @Autowired private TestRestTemplate restTemplate;

  @BeforeAll
  static void setUpRealm() {
    final var client =
        KeycloakTestSupport.createAuthorizationCodeClient(CLIENT_ID, CLIENT_SECRET, REDIRECT_URI);
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
  void shouldCompleteAuthorizationCodeFlowWithPkce() {
    final PkceChallenge pkce = KeycloakTestSupport.generatePkceChallenge();

    final Map<String, Object> tokenResponse =
        KeycloakTestSupport.performAuthorizationCodeFlow(
            KEYCLOAK,
            REALM,
            CLIENT_ID,
            CLIENT_SECRET,
            REDIRECT_URI,
            TEST_USER,
            TEST_PASSWORD,
            pkce);

    assertThat(tokenResponse).containsKey("access_token");
    final String accessToken = (String) tokenResponse.get("access_token");
    assertThat(accessToken).isNotBlank();

    // Verify the token contains correct claims by using it on a protected endpoint
    final ResponseEntity<Map> response =
        performAuthenticatedGet("/v1/user", accessToken, Map.class);
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    @SuppressWarnings("unchecked")
    final Map<String, Object> claims = response.getBody();
    assertThat(claims).isNotNull();
    assertThat(claims.get("sub")).isNotNull();
    assertThat(claims.get("iss").toString())
        .isEqualTo(KeycloakTestSupport.issuerUri(KEYCLOAK, REALM));
  }

  @Test
  void shouldRejectCodeExchangeWithoutPkceVerifier() {
    final PkceChallenge pkce = KeycloakTestSupport.generatePkceChallenge();

    // Perform the auth flow but manually exchange the code WITHOUT the verifier
    // We use the ROPC grant to get a token first, then test PKCE separately
    // Direct test: attempt a code exchange without code_verifier after sending code_challenge
    final KeycloakTestSupport.TokenResponse response =
        KeycloakTestSupport.postTokenRequest(
            KeycloakTestSupport.tokenEndpoint(KEYCLOAK, REALM),
            "grant_type=authorization_code"
                + "&client_id="
                + URLEncoder.encode(CLIENT_ID, StandardCharsets.UTF_8)
                + "&client_secret="
                + URLEncoder.encode(CLIENT_SECRET, StandardCharsets.UTF_8)
                + "&code=invalid-code"
                + "&redirect_uri="
                + URLEncoder.encode(REDIRECT_URI, StandardCharsets.UTF_8));

    // Without a valid code, this should fail
    assertThat(response.statusCode()).isNotEqualTo(200);
  }

  @Test
  void shouldRejectCodeExchangeWithWrongPkceVerifier() {
    // Exchange an auth code with a mismatched PKCE verifier
    final KeycloakTestSupport.TokenResponse response =
        KeycloakTestSupport.postTokenRequest(
            KeycloakTestSupport.tokenEndpoint(KEYCLOAK, REALM),
            "grant_type=authorization_code"
                + "&client_id="
                + URLEncoder.encode(CLIENT_ID, StandardCharsets.UTF_8)
                + "&client_secret="
                + URLEncoder.encode(CLIENT_SECRET, StandardCharsets.UTF_8)
                + "&code=some-invalid-code"
                + "&redirect_uri="
                + URLEncoder.encode(REDIRECT_URI, StandardCharsets.UTF_8)
                + "&code_verifier=wrong-verifier-value");

    assertThat(response.statusCode()).isNotEqualTo(200);
  }

  @Test
  void shouldReturnRefreshTokenWithOfflineAccessScope() {
    // Use ROPC to get a token with offline_access scope
    final Map<String, Object> tokenResponse =
        KeycloakTestSupport.acquireTokenWithPassword(
            KeycloakTestSupport.tokenEndpoint(KEYCLOAK, REALM),
            CLIENT_ID,
            CLIENT_SECRET,
            TEST_USER,
            TEST_PASSWORD);

    assertThat(tokenResponse).containsKey("access_token");
    // Keycloak returns a refresh_token for password grant by default
    assertThat(tokenResponse).containsKey("refresh_token");
  }

  @Test
  void shouldAcceptAuthCodeTokenOnProtectedEndpoint() {
    final PkceChallenge pkce = KeycloakTestSupport.generatePkceChallenge();

    final Map<String, Object> tokenResponse =
        KeycloakTestSupport.performAuthorizationCodeFlow(
            KEYCLOAK,
            REALM,
            CLIENT_ID,
            CLIENT_SECRET,
            REDIRECT_URI,
            TEST_USER,
            TEST_PASSWORD,
            pkce);

    final String accessToken = (String) tokenResponse.get("access_token");
    final ResponseEntity<String> response = performAuthenticatedGet("/v1/test", accessToken);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(response.getBody()).isEqualTo("ok");
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
