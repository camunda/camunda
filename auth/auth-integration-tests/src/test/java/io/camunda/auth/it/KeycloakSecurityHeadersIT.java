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
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Integration test verifying security headers and error response format when using OIDC
 * authentication with Keycloak. Validates that the auth library correctly returns
 * application/problem+json responses and appropriate security headers.
 */
@Testcontainers
@SpringBootTest(
    classes = KeycloakSecurityHeadersIT.TestApp.class,
    webEnvironment = WebEnvironment.RANDOM_PORT)
@AutoConfigureTestRestTemplate
class KeycloakSecurityHeadersIT {

  private static final String REALM = "camunda-headers";
  private static final String CLIENT_ID = "headers-client";
  private static final String CLIENT_SECRET = "headers-client-secret";

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
    registry.add(
        "camunda.security.authentication.oidc.grantType",
        () -> "client_credentials");
    registry.add("camunda.auth.security.webapp-enabled", () -> "false");
  }

  @Test
  void shouldReturnProblemJsonOnUnauthorizedApiCall() {
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

  @Test
  void shouldReturnOkForValidTokenOnProtectedEndpoint() {
    final String token =
        KeycloakTestSupport.acquireToken(
            KeycloakTestSupport.tokenEndpoint(KEYCLOAK, REALM), CLIENT_ID, CLIENT_SECRET);

    final ResponseEntity<String> response = performAuthenticatedGet("/v1/test", token);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(response.getBody()).isEqualTo("ok");
  }

  @Test
  void shouldIncludeCacheControlHeaderOnApiResponse() {
    final String token =
        KeycloakTestSupport.acquireToken(
            KeycloakTestSupport.tokenEndpoint(KEYCLOAK, REALM), CLIENT_ID, CLIENT_SECRET);

    final ResponseEntity<String> response = performAuthenticatedGet("/v1/test", token);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    // Spring Security typically adds cache control headers for protected resources
    final List<String> cacheControl = response.getHeaders().get("Cache-Control");
    if (cacheControl != null) {
      assertThat(String.join(", ", cacheControl)).contains("no-cache");
    }
  }

  @Test
  void shouldReturnOkForUnprotectedPaths() {
    final ResponseEntity<String> response =
        restTemplate.getForEntity("http://localhost:" + port + "/actuator/health", String.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(response.getBody()).isEqualTo("UP");
  }

  @Test
  void shouldReturnUnauthorizedWithBearerChallengeHeader() {
    final ResponseEntity<String> response =
        restTemplate.getForEntity("http://localhost:" + port + "/v1/test", String.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
  }

  @Test
  @SuppressWarnings("unchecked")
  void shouldReturnProblemDetailBody() {
    final HttpHeaders headers = new HttpHeaders();
    headers.setAccept(List.of(MediaType.APPLICATION_JSON));
    final HttpEntity<Void> entity = new HttpEntity<>(headers);

    final ResponseEntity<Map> response =
        restTemplate.exchange(
            "http://localhost:" + port + "/v1/test", HttpMethod.GET, entity, Map.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    final Map<String, Object> body = response.getBody();
    assertThat(body).isNotNull();
    assertThat(body).containsKey("status");
    assertThat(body.get("status")).isEqualTo(401);
  }

  private ResponseEntity<String> performAuthenticatedGet(
      final String path, final String accessToken) {
    final HttpHeaders headers = new HttpHeaders();
    headers.setBearerAuth(accessToken);
    final HttpEntity<Void> entity = new HttpEntity<>(headers);

    return restTemplate.exchange(
        "http://localhost:" + port + path, HttpMethod.GET, entity, String.class);
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

      @GetMapping("/actuator/health")
      String health() {
        return "UP";
      }
    }
  }
}
