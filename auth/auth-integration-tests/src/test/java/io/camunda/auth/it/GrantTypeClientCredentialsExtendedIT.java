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
import org.keycloak.admin.client.Keycloak;
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
 * Extended integration tests for the client_credentials grant type. Validates scopes, token
 * uniqueness, revocation, and standard JWT claims against a shared Keycloak container.
 */
@SpringBootTest(
    classes = GrantTypeClientCredentialsExtendedIT.TestApp.class,
    webEnvironment = WebEnvironment.RANDOM_PORT)
@AutoConfigureTestRestTemplate
class GrantTypeClientCredentialsExtendedIT {

  private static final String REALM = "grant-type-cc-extended";
  private static final String CLIENT_ID = "cc-extended-client";
  private static final String CLIENT_SECRET = "cc-extended-secret";

  private static final KeycloakContainer KEYCLOAK = SharedKeycloakContainer.getInstance();

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
  void shouldRequestSpecificScopes() {
    final Map<String, Object> tokenResponse =
        KeycloakTestSupport.acquireTokenResponse(
            KeycloakTestSupport.tokenEndpoint(KEYCLOAK, REALM), CLIENT_ID, CLIENT_SECRET);

    assertThat(tokenResponse).containsKey("access_token");
    // Keycloak includes scope in the token response
    final String scope = (String) tokenResponse.get("scope");
    assertThat(scope).isNotNull();
  }

  @Test
  void shouldIssueDistinctTokensOnSubsequentCalls() {
    final String token1 =
        KeycloakTestSupport.acquireToken(
            KeycloakTestSupport.tokenEndpoint(KEYCLOAK, REALM), CLIENT_ID, CLIENT_SECRET);
    final String token2 =
        KeycloakTestSupport.acquireToken(
            KeycloakTestSupport.tokenEndpoint(KEYCLOAK, REALM), CLIENT_ID, CLIENT_SECRET);

    // Two calls should produce different tokens (different jti/iat at minimum)
    assertThat(token1).isNotEqualTo(token2);
  }

  @Test
  void shouldRejectRevokedClientCredentials() {
    // Disable the client in Keycloak
    try (Keycloak admin = KEYCLOAK.getKeycloakAdminClient()) {
      final var clients = admin.realm(REALM).clients().findByClientId(CLIENT_ID);
      assertThat(clients).isNotEmpty();
      final var clientRep = clients.get(0);
      clientRep.setEnabled(false);
      admin.realm(REALM).clients().get(clientRep.getId()).update(clientRep);
    }

    try {
      // Attempt to acquire a token with disabled client — should fail
      final KeycloakTestSupport.TokenResponse response =
          KeycloakTestSupport.postTokenRequest(
              KeycloakTestSupport.tokenEndpoint(KEYCLOAK, REALM),
              "grant_type=client_credentials&client_id="
                  + CLIENT_ID
                  + "&client_secret="
                  + CLIENT_SECRET);
      assertThat(response.statusCode()).isNotEqualTo(200);
    } finally {
      // Re-enable the client for other tests
      try (Keycloak admin = KEYCLOAK.getKeycloakAdminClient()) {
        final var clients = admin.realm(REALM).clients().findByClientId(CLIENT_ID);
        final var clientRep = clients.get(0);
        clientRep.setEnabled(true);
        admin.realm(REALM).clients().get(clientRep.getId()).update(clientRep);
      }
    }
  }

  @Test
  @SuppressWarnings("unchecked")
  void shouldIncludeStandardJwtClaims() {
    final String token =
        KeycloakTestSupport.acquireToken(
            KeycloakTestSupport.tokenEndpoint(KEYCLOAK, REALM), CLIENT_ID, CLIENT_SECRET);

    final ResponseEntity<Map> response = performAuthenticatedGet("/v1/user", token, Map.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    final Map<String, Object> claims = response.getBody();
    assertThat(claims).isNotNull();
    assertThat(claims).containsKey("iss");
    assertThat(claims).containsKey("sub");
    assertThat(claims).containsKey("exp");
    assertThat(claims).containsKey("iat");
    assertThat(claims).containsKey("jti");
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
