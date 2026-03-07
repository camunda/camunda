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
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.resttestclient.TestRestTemplate;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureTestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Integration test validating client_credentials grant flow with a real Keycloak IDP. Verifies the
 * auth library correctly configures Spring Security OIDC resource server to accept Keycloak-issued
 * JWTs.
 */
@Testcontainers
@SpringBootTest(
    classes = KeycloakClientCredentialsIT.TestApp.class,
    webEnvironment = WebEnvironment.RANDOM_PORT)
@AutoConfigureTestRestTemplate
class KeycloakClientCredentialsIT {

  private static final String REALM = "camunda-test";
  private static final String CLIENT_ID = "test-api";
  private static final String CLIENT_SECRET = "test-api-secret";

  @Container
  private static final KeycloakContainer KEYCLOAK = KeycloakTestSupport.createKeycloak();

  @LocalServerPort private int port;

  @Autowired private TestRestTemplate restTemplate;

  @BeforeAll
  static void setUpRealm() {
    final var client = KeycloakTestSupport.createConfidentialClient(CLIENT_ID, CLIENT_SECRET);
    KeycloakTestSupport.createRealm(KEYCLOAK, REALM, List.of(client));
  }

  @DynamicPropertySource
  static void configureProperties(final DynamicPropertyRegistry registry) {
    registry.add("camunda.auth.method", () -> "oidc");
    registry.add(
        "camunda.security.authentication.oidc.issuerUri",
        () -> KeycloakTestSupport.issuerUri(KEYCLOAK, REALM));
    registry.add(
        "camunda.security.authentication.oidc.clientId",
        () -> CLIENT_ID);
    registry.add(
        "camunda.security.authentication.oidc.clientSecret",
        () -> CLIENT_SECRET);
    registry.add(
        "camunda.security.authentication.oidc.tokenUri",
        () -> KeycloakTestSupport.tokenEndpoint(KEYCLOAK, REALM));
    registry.add(
        "camunda.security.authentication.oidc.authorizationUri",
        () -> KeycloakTestSupport.authorizationUri(KEYCLOAK, REALM));
    registry.add(
        "camunda.security.authentication.oidc.jwkSetUri",
        () -> KeycloakTestSupport.jwkSetUri(KEYCLOAK, REALM));
    registry.add(
        "camunda.security.authentication.oidc.grantType",
        () -> "client_credentials");
    // Disable the webapp security to avoid login redirects
    registry.add("camunda.auth.security.webapp-enabled", () -> "false");
  }

  @Test
  void shouldRejectUnauthenticatedApiCall() {
    final ResponseEntity<String> response =
        restTemplate.getForEntity("http://localhost:" + port + "/v1/test", String.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
  }

  @Test
  void shouldAcceptValidClientCredentialsToken() {
    final String token =
        KeycloakTestSupport.acquireToken(
            KeycloakTestSupport.tokenEndpoint(KEYCLOAK, REALM), CLIENT_ID, CLIENT_SECRET);

    final ResponseEntity<String> response = performAuthenticatedGet("/v1/test", token);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(response.getBody()).isEqualTo("ok");
  }

  @Test
  @SuppressWarnings("unchecked")
  void shouldExtractClaimsFromToken() {
    final String token =
        KeycloakTestSupport.acquireToken(
            KeycloakTestSupport.tokenEndpoint(KEYCLOAK, REALM), CLIENT_ID, CLIENT_SECRET);

    final ResponseEntity<Map> response =
        performAuthenticatedGet("/v1/user", token, Map.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    final Map<String, Object> claims = response.getBody();
    assertThat(claims).isNotNull();
    assertThat(claims).containsEntry("azp", CLIENT_ID);
    assertThat(claims.get("iss").toString())
        .isEqualTo(KeycloakTestSupport.issuerUri(KEYCLOAK, REALM));
  }

  @Test
  void shouldAllowUnprotectedPaths() {
    final ResponseEntity<String> response =
        restTemplate.getForEntity("http://localhost:" + port + "/actuator/health", String.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
  }

  @Test
  void shouldRejectInvalidToken() {
    final ResponseEntity<String> response =
        performAuthenticatedGet("/v1/test", "this-is-not-a-valid-jwt");

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
  }

  @Test
  void shouldRejectTokenWithTamperedPayload() {
    // Acquire a valid token, then tamper with the payload to simulate an invalid token
    final String validToken =
        KeycloakTestSupport.acquireToken(
            KeycloakTestSupport.tokenEndpoint(KEYCLOAK, REALM), CLIENT_ID, CLIENT_SECRET);

    // Tamper with the middle part (payload) of the JWT
    final String[] parts = validToken.split("\\.");
    assertThat(parts).hasSize(3);
    final String tamperedToken = parts[0] + ".dGFtcGVyZWQ." + parts[2];

    final ResponseEntity<String> response = performAuthenticatedGet("/v1/test", tamperedToken);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
  }

  @Test
  void shouldReturnProblemJsonOnUnauthorized() {
    final HttpHeaders headers = new HttpHeaders();
    headers.setAccept(List.of(MediaType.APPLICATION_JSON));
    final HttpEntity<Void> entity = new HttpEntity<>(headers);

    final ResponseEntity<String> response =
        restTemplate.exchange(
            "http://localhost:" + port + "/v1/test", HttpMethod.GET, entity, String.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    assertThat(response.getHeaders().getContentType())
        .isNotNull()
        .satisfies(
            contentType ->
                assertThat(contentType.toString()).contains("application/problem+json"));
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

  @SpringBootApplication(
      exclude = {DataSourceAutoConfiguration.class})
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

      @GetMapping("/actuator/health")
      String health() {
        return "UP";
      }
    }
  }
}
