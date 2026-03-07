/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.auth.it;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import dasniko.testcontainers.keycloak.KeycloakContainer;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.keycloak.admin.client.Keycloak;
import org.keycloak.representations.idm.ClientRepresentation;
import org.keycloak.representations.idm.ProtocolMapperRepresentation;
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
 * Integration test validating RFC 8693 token exchange against a real Keycloak IDP. Uses two
 * clients: a subject-client that acquires the initial token and an exchange-client that performs
 * the token exchange.
 */
@SpringBootTest(
    classes = GrantTypeTokenExchangeIT.TestApp.class,
    webEnvironment = WebEnvironment.RANDOM_PORT)
@AutoConfigureTestRestTemplate
class GrantTypeTokenExchangeIT {

  private static final String REALM = "grant-type-token-exchange";
  private static final String SUBJECT_CLIENT_ID = "subject-client";
  private static final String SUBJECT_CLIENT_SECRET = "subject-secret";
  private static final String EXCHANGE_CLIENT_ID = "exchange-client";
  private static final String EXCHANGE_CLIENT_SECRET = "exchange-secret";

  private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
  private static final KeycloakContainer KEYCLOAK = SharedKeycloakContainer.getInstance();

  @LocalServerPort private int port;

  @Autowired private TestRestTemplate restTemplate;

  @BeforeAll
  static void setUpRealm() {
    final var subjectClient =
        KeycloakTestSupport.createTokenExchangeClient(SUBJECT_CLIENT_ID, SUBJECT_CLIENT_SECRET);

    // Add audience mapper so subject-client tokens include exchange-client in their audience
    final var audienceMapper = new ProtocolMapperRepresentation();
    audienceMapper.setName("exchange-client-audience");
    audienceMapper.setProtocol("openid-connect");
    audienceMapper.setProtocolMapper("oidc-audience-mapper");
    audienceMapper.setConfig(
        Map.of(
            "included.client.audience", EXCHANGE_CLIENT_ID,
            "id.token.claim", "false",
            "access.token.claim", "true",
            "lightweight.claim", "false",
            "introspection.token.claim", "true"));
    subjectClient.setProtocolMappers(List.of(audienceMapper));

    final var exchangeClient =
        KeycloakTestSupport.createTokenExchangeClient(EXCHANGE_CLIENT_ID, EXCHANGE_CLIENT_SECRET);
    KeycloakTestSupport.createRealm(KEYCLOAK, REALM, List.of(subjectClient, exchangeClient));

    // Configure token exchange permissions in Keycloak
    configureTokenExchangePermissions();
  }

  /**
   * Configures Keycloak's token exchange permissions so exchange-client can exchange tokens from
   * subject-client. Uses the HTTP admin REST API to enable management permissions on the
   * subject-client, which is required for RFC 8693 token exchange.
   */
  private static void configureTokenExchangePermissions() {
    try (Keycloak admin = KEYCLOAK.getKeycloakAdminClient()) {
      // Find the subject-client's internal ID
      final List<ClientRepresentation> subjectClients =
          admin.realm(REALM).clients().findByClientId(SUBJECT_CLIENT_ID);
      assertThat(subjectClients).isNotEmpty();
      final String subjectClientUuid = subjectClients.get(0).getId();

      // Enable management permissions via the HTTP admin REST API
      final String adminToken = admin.tokenManager().getAccessTokenString();
      final String url =
          KEYCLOAK.getAuthServerUrl()
              + "/admin/realms/"
              + REALM
              + "/clients/"
              + subjectClientUuid
              + "/management/permissions";

      final var httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
      final var request =
          HttpRequest.newBuilder()
              .uri(URI.create(url))
              .header("Content-Type", "application/json")
              .header("Authorization", "Bearer " + adminToken)
              .PUT(HttpRequest.BodyPublishers.ofString("{\"enabled\": true}"))
              .timeout(Duration.ofSeconds(30))
              .build();

      final var response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
      if (response.statusCode() != 200) {
        // Token exchange permissions may not be supported in this Keycloak version —
        // the test will still attempt the exchange and assert the result
        System.err.println(
            "Warning: Could not enable management permissions (status="
                + response.statusCode()
                + "): "
                + response.body());
      }
    } catch (final Exception e) {
      System.err.println("Warning: Failed to configure token exchange permissions: " + e);
    }
  }

  @DynamicPropertySource
  static void configureProperties(final DynamicPropertyRegistry registry) {
    registry.add("camunda.auth.method", () -> "oidc");
    registry.add(
        "camunda.security.authentication.oidc.issuerUri",
        () -> KeycloakTestSupport.issuerUri(KEYCLOAK, REALM));
    registry.add("camunda.security.authentication.oidc.clientId", () -> SUBJECT_CLIENT_ID);
    registry.add("camunda.security.authentication.oidc.clientSecret", () -> SUBJECT_CLIENT_SECRET);
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
  @SuppressWarnings("unchecked")
  void shouldExchangeTokenForDifferentAudience() {
    // Acquire initial token as subject-client
    final String subjectToken =
        KeycloakTestSupport.acquireToken(
            KeycloakTestSupport.tokenEndpoint(KEYCLOAK, REALM),
            SUBJECT_CLIENT_ID,
            SUBJECT_CLIENT_SECRET);

    // Exchange the token using exchange-client (no audience restriction)
    final Map<String, Object> exchangeResponse =
        KeycloakTestSupport.exchangeToken(
            KeycloakTestSupport.tokenEndpoint(KEYCLOAK, REALM),
            EXCHANGE_CLIENT_ID,
            EXCHANGE_CLIENT_SECRET,
            subjectToken,
            null);

    assertThat(exchangeResponse).containsKey("access_token");
    final String exchangedToken = (String) exchangeResponse.get("access_token");

    // Decode the exchanged token and verify azp
    final Map<String, Object> claims = decodeJwtPayload(exchangedToken);
    assertThat(claims.get("azp")).isEqualTo(EXCHANGE_CLIENT_ID);
  }

  @Test
  @SuppressWarnings("unchecked")
  void shouldPreserveSubjectClaimsDuringExchange() {
    final String subjectToken =
        KeycloakTestSupport.acquireToken(
            KeycloakTestSupport.tokenEndpoint(KEYCLOAK, REALM),
            SUBJECT_CLIENT_ID,
            SUBJECT_CLIENT_SECRET);

    final Map<String, Object> subjectClaims = decodeJwtPayload(subjectToken);
    final String originalSub = (String) subjectClaims.get("sub");

    final Map<String, Object> exchangeResponse =
        KeycloakTestSupport.exchangeToken(
            KeycloakTestSupport.tokenEndpoint(KEYCLOAK, REALM),
            EXCHANGE_CLIENT_ID,
            EXCHANGE_CLIENT_SECRET,
            subjectToken,
            null);

    final String exchangedToken = (String) exchangeResponse.get("access_token");
    final Map<String, Object> exchangedClaims = decodeJwtPayload(exchangedToken);

    // The subject should be preserved during the exchange
    assertThat(exchangedClaims.get("sub")).isEqualTo(originalSub);
  }

  @Test
  void shouldRejectExchangeWithInvalidSubjectToken() {
    final KeycloakTestSupport.TokenResponse response =
        KeycloakTestSupport.postTokenRequest(
            KeycloakTestSupport.tokenEndpoint(KEYCLOAK, REALM),
            "grant_type=urn%3Aietf%3Aparams%3Aoauth%3Agrant-type%3Atoken-exchange"
                + "&client_id="
                + EXCHANGE_CLIENT_ID
                + "&client_secret="
                + EXCHANGE_CLIENT_SECRET
                + "&subject_token=invalid-expired-token"
                + "&subject_token_type=urn%3Aietf%3Aparams%3Aoauth%3Atoken-type%3Aaccess_token");

    assertThat(response.statusCode()).isNotEqualTo(200);
  }

  @Test
  void shouldAcceptExchangedTokenOnProtectedEndpoint() {
    final String subjectToken =
        KeycloakTestSupport.acquireToken(
            KeycloakTestSupport.tokenEndpoint(KEYCLOAK, REALM),
            SUBJECT_CLIENT_ID,
            SUBJECT_CLIENT_SECRET);

    final Map<String, Object> exchangeResponse =
        KeycloakTestSupport.exchangeToken(
            KeycloakTestSupport.tokenEndpoint(KEYCLOAK, REALM),
            EXCHANGE_CLIENT_ID,
            EXCHANGE_CLIENT_SECRET,
            subjectToken,
            null);

    final String exchangedToken = (String) exchangeResponse.get("access_token");
    final ResponseEntity<String> response = performAuthenticatedGet("/v1/test", exchangedToken);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(response.getBody()).isEqualTo("ok");
  }

  @SuppressWarnings("unchecked")
  private Map<String, Object> decodeJwtPayload(final String jwt) {
    final String[] parts = jwt.split("\\.");
    assertThat(parts).hasSize(3);
    final byte[] payload = Base64.getUrlDecoder().decode(parts[1]);
    try {
      return OBJECT_MAPPER.readValue(payload, new TypeReference<>() {});
    } catch (final Exception e) {
      throw new IllegalStateException("Failed to decode JWT payload", e);
    }
  }

  private ResponseEntity<String> performAuthenticatedGet(
      final String path, final String accessToken) {
    final HttpHeaders headers = new HttpHeaders();
    headers.setBearerAuth(accessToken);
    final HttpEntity<Void> entity = new HttpEntity<>(headers);
    return restTemplate.exchange(
        "http://localhost:" + port + path, HttpMethod.GET, entity, String.class);
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
