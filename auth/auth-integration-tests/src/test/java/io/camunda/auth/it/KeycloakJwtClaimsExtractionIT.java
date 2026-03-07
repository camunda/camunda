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
import org.keycloak.representations.idm.ProtocolMapperRepresentation;
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
 * Integration test verifying JWT claims extraction from Keycloak tokens. Validates that custom
 * claims (groups, azp, preferred_username) are correctly extracted and mapped to CamundaAuthentication
 * via protocol mappers configured on the Keycloak client.
 */
@Testcontainers
@SpringBootTest(
    classes = KeycloakJwtClaimsExtractionIT.TestApp.class,
    webEnvironment = WebEnvironment.RANDOM_PORT)
@AutoConfigureTestRestTemplate
class KeycloakJwtClaimsExtractionIT {

  private static final String REALM = "camunda-claims";
  private static final String CLIENT_ID = "claims-client";
  private static final String CLIENT_SECRET = "claims-client-secret";

  @Container
  private static final KeycloakContainer KEYCLOAK = KeycloakTestSupport.createKeycloak();

  @LocalServerPort private int port;

  @Autowired private TestRestTemplate restTemplate;

  @BeforeAll
  static void setUpRealm() {
    final var client = KeycloakTestSupport.createConfidentialClient(CLIENT_ID, CLIENT_SECRET);

    // Add a hardcoded "groups" claim mapper to inject groups into the access token
    final var groupsMapper = new ProtocolMapperRepresentation();
    groupsMapper.setName("groups-mapper");
    groupsMapper.setProtocol("openid-connect");
    groupsMapper.setProtocolMapper("oidc-hardcoded-claim-mapper");
    groupsMapper.setConfig(
        Map.of(
            "claim.name", "groups",
            "claim.value", "[\"admin\", \"dev\"]",
            "jsonType.label", "JSON",
            "id.token.claim", "true",
            "access.token.claim", "true",
            "userinfo.token.claim", "true"));

    // Add a hardcoded "custom-username" claim mapper for testing username extraction
    final var usernameMapper = new ProtocolMapperRepresentation();
    usernameMapper.setName("custom-username-mapper");
    usernameMapper.setProtocol("openid-connect");
    usernameMapper.setProtocolMapper("oidc-hardcoded-claim-mapper");
    usernameMapper.setConfig(
        Map.of(
            "claim.name", "preferred_username",
            "claim.value", "service-account-claims-client",
            "jsonType.label", "String",
            "id.token.claim", "true",
            "access.token.claim", "true",
            "userinfo.token.claim", "true"));

    client.setProtocolMappers(List.of(groupsMapper, usernameMapper));

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
    // Configure claim extraction
    registry.add("camunda.auth.oidc.groups-claim", () -> "groups");
    registry.add("camunda.auth.oidc.username-claim", () -> "preferred_username");
    registry.add("camunda.auth.oidc.client-id-claim", () -> "azp");
    registry.add("camunda.auth.oidc.prefer-username-claim", () -> "true");
    registry.add("camunda.auth.security.webapp-enabled", () -> "false");
  }

  @Test
  @SuppressWarnings("unchecked")
  void shouldExtractGroupsFromToken() {
    final String token =
        KeycloakTestSupport.acquireToken(
            KeycloakTestSupport.tokenEndpoint(KEYCLOAK, REALM), CLIENT_ID, CLIENT_SECRET);

    final ResponseEntity<Map> response = performAuthenticatedGet("/v1/user", token, Map.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    final Map<String, Object> claims = response.getBody();
    assertThat(claims).isNotNull();
    // The "groups" claim should be present as injected by the hardcoded mapper
    assertThat(claims).containsKey("groups");
    @SuppressWarnings("unchecked")
    final List<String> groups = (List<String>) claims.get("groups");
    assertThat(groups).containsExactlyInAnyOrder("admin", "dev");
  }

  @Test
  @SuppressWarnings("unchecked")
  void shouldExtractClientIdFromAzpClaim() {
    final String token =
        KeycloakTestSupport.acquireToken(
            KeycloakTestSupport.tokenEndpoint(KEYCLOAK, REALM), CLIENT_ID, CLIENT_SECRET);

    final ResponseEntity<Map> response = performAuthenticatedGet("/v1/user", token, Map.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    final Map<String, Object> claims = response.getBody();
    assertThat(claims).isNotNull().containsEntry("azp", CLIENT_ID);
  }

  @Test
  @SuppressWarnings("unchecked")
  void shouldExtractPreferredUsernameFromToken() {
    final String token =
        KeycloakTestSupport.acquireToken(
            KeycloakTestSupport.tokenEndpoint(KEYCLOAK, REALM), CLIENT_ID, CLIENT_SECRET);

    final ResponseEntity<Map> response = performAuthenticatedGet("/v1/user", token, Map.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    final Map<String, Object> claims = response.getBody();
    assertThat(claims).isNotNull();
    // The preferred_username should be the service account username set by the mapper
    assertThat(claims.get("preferred_username")).isNotNull();
    assertThat(claims.get("preferred_username").toString())
        .isEqualTo("service-account-claims-client");
  }

  @Test
  @SuppressWarnings("unchecked")
  void shouldExtractIssuerFromToken() {
    final String token =
        KeycloakTestSupport.acquireToken(
            KeycloakTestSupport.tokenEndpoint(KEYCLOAK, REALM), CLIENT_ID, CLIENT_SECRET);

    final ResponseEntity<Map> response = performAuthenticatedGet("/v1/user", token, Map.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    final Map<String, Object> claims = response.getBody();
    assertThat(claims).isNotNull();
    assertThat(claims.get("iss").toString())
        .isEqualTo(KeycloakTestSupport.issuerUri(KEYCLOAK, REALM));
  }

  @Test
  @SuppressWarnings("unchecked")
  void shouldContainStandardJwtClaims() {
    final String token =
        KeycloakTestSupport.acquireToken(
            KeycloakTestSupport.tokenEndpoint(KEYCLOAK, REALM), CLIENT_ID, CLIENT_SECRET);

    final ResponseEntity<Map> response = performAuthenticatedGet("/v1/user", token, Map.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    final Map<String, Object> claims = response.getBody();
    assertThat(claims).isNotNull();
    // Standard JWT claims should always be present
    assertThat(claims).containsKey("iss");
    assertThat(claims).containsKey("sub");
    assertThat(claims).containsKey("exp");
    assertThat(claims).containsKey("iat");
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
