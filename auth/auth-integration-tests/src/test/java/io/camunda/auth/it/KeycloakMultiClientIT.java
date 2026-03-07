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
import org.springframework.http.ResponseEntity;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Integration test verifying that the auth library accepts tokens issued to different clients within
 * the same Keycloak realm. Both client-a and client-b share the same issuer, so the library's
 * single-issuer JWT decoder should validate tokens from either client.
 */
@Testcontainers
@SpringBootTest(
    classes = KeycloakMultiClientIT.TestApp.class,
    webEnvironment = WebEnvironment.RANDOM_PORT)
@AutoConfigureTestRestTemplate
class KeycloakMultiClientIT {

  private static final String REALM = "camunda-multi";
  private static final String CLIENT_A_ID = "client-a";
  private static final String CLIENT_A_SECRET = "secret-a";
  private static final String CLIENT_B_ID = "client-b";
  private static final String CLIENT_B_SECRET = "secret-b";

  @Container
  private static final KeycloakContainer KEYCLOAK = KeycloakTestSupport.createKeycloak();

  @LocalServerPort private int port;

  @Autowired private TestRestTemplate restTemplate;

  @BeforeAll
  static void setUpRealm() {
    final var clientA = KeycloakTestSupport.createConfidentialClient(CLIENT_A_ID, CLIENT_A_SECRET);
    final var clientB = KeycloakTestSupport.createConfidentialClient(CLIENT_B_ID, CLIENT_B_SECRET);
    KeycloakTestSupport.createRealm(KEYCLOAK, REALM, List.of(clientA, clientB));
  }

  @DynamicPropertySource
  static void configureProperties(final DynamicPropertyRegistry registry) {
    registry.add("camunda.auth.method", () -> "oidc");
    // Configure using the default OIDC provider (single issuer for both clients)
    registry.add(
        "camunda.security.authentication.oidc.issuerUri",
        () -> KeycloakTestSupport.issuerUri(KEYCLOAK, REALM));
    registry.add("camunda.security.authentication.oidc.clientId", () -> CLIENT_A_ID);
    registry.add("camunda.security.authentication.oidc.clientSecret", () -> CLIENT_A_SECRET);
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
    registry.add("camunda.auth.oidc.client-id-claim", () -> "azp");
    registry.add("camunda.auth.oidc.prefer-username-claim", () -> "false");
    registry.add("camunda.auth.security.webapp-enabled", () -> "false");
  }

  @Test
  void shouldAcceptTokenFromClientA() {
    final String token =
        KeycloakTestSupport.acquireToken(
            KeycloakTestSupport.tokenEndpoint(KEYCLOAK, REALM), CLIENT_A_ID, CLIENT_A_SECRET);

    final ResponseEntity<String> response = performAuthenticatedGet("/v1/test", token);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(response.getBody()).isEqualTo("ok");
  }

  @Test
  void shouldAcceptTokenFromClientB() {
    final String token =
        KeycloakTestSupport.acquireToken(
            KeycloakTestSupport.tokenEndpoint(KEYCLOAK, REALM), CLIENT_B_ID, CLIENT_B_SECRET);

    final ResponseEntity<String> response = performAuthenticatedGet("/v1/test", token);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(response.getBody()).isEqualTo("ok");
  }

  @Test
  @SuppressWarnings("unchecked")
  void shouldExtractCorrectClientId() {
    final String token =
        KeycloakTestSupport.acquireToken(
            KeycloakTestSupport.tokenEndpoint(KEYCLOAK, REALM), CLIENT_A_ID, CLIENT_A_SECRET);

    final ResponseEntity<Map> response = performAuthenticatedGet("/v1/user", token, Map.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(response.getBody()).isNotNull().containsEntry("azp", CLIENT_A_ID);
  }

  @Test
  @SuppressWarnings("unchecked")
  void shouldExtractCorrectClientIdForClientB() {
    final String token =
        KeycloakTestSupport.acquireToken(
            KeycloakTestSupport.tokenEndpoint(KEYCLOAK, REALM), CLIENT_B_ID, CLIENT_B_SECRET);

    final ResponseEntity<Map> response = performAuthenticatedGet("/v1/user", token, Map.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(response.getBody()).isNotNull().containsEntry("azp", CLIENT_B_ID);
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
    }
  }
}
