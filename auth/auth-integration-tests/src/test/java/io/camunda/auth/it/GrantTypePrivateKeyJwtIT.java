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
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.KeyUse;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import dasniko.testcontainers.keycloak.KeycloakContainer;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.keycloak.representations.idm.ClientRepresentation;
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
 * Integration test validating private_key_jwt client authentication against a real Keycloak IDP.
 * Configures a Keycloak client to accept signed JWT assertions for client authentication (instead
 * of client_secret), then validates the flow end-to-end.
 */
@SpringBootTest(
    classes = GrantTypePrivateKeyJwtIT.TestApp.class,
    webEnvironment = WebEnvironment.RANDOM_PORT)
@AutoConfigureTestRestTemplate
class GrantTypePrivateKeyJwtIT {

  private static final String REALM = "grant-type-pkjwt";
  private static final String CLIENT_ID = "pkjwt-client";

  private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
  private static final KeycloakContainer KEYCLOAK = SharedKeycloakContainer.getInstance();
  private static final HttpClient HTTP_CLIENT =
      HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();

  private static RSAKey rsaKey;

  @LocalServerPort private int port;

  @Autowired private TestRestTemplate restTemplate;

  @BeforeAll
  static void setUpRealm() throws Exception {
    // Generate RSA key pair for JWT assertion signing
    rsaKey =
        new RSAKeyGenerator(2048)
            .keyID(UUID.randomUUID().toString())
            .keyUse(KeyUse.SIGNATURE)
            .algorithm(JWSAlgorithm.RS256)
            .generate();

    // Create client configured for private_key_jwt authentication
    final var client = new ClientRepresentation();
    client.setClientId(CLIENT_ID);
    client.setEnabled(true);
    client.setPublicClient(false);
    client.setServiceAccountsEnabled(true);
    client.setClientAuthenticatorType("client-jwt");
    client.setDirectAccessGrantsEnabled(true);
    // Set the public key as a JWKS string so Keycloak can verify JWT assertions
    final String jwksJson = new JWKSet(rsaKey.toPublicJWK()).toString();
    client.setAttributes(
        Map.of(
            "token.endpoint.auth.signing.alg", "RS256",
            "use.jwks.url", "false",
            "use.jwks.string", "true",
            "jwks.string", jwksJson));

    KeycloakTestSupport.createRealm(KEYCLOAK, REALM, List.of(client));
  }

  @DynamicPropertySource
  static void configureProperties(final DynamicPropertyRegistry registry) {
    registry.add("camunda.auth.method", () -> "oidc");
    registry.add(
        "camunda.security.authentication.oidc.issuerUri",
        () -> KeycloakTestSupport.issuerUri(KEYCLOAK, REALM));
    registry.add("camunda.security.authentication.oidc.clientId", () -> CLIENT_ID);
    registry.add("camunda.security.authentication.oidc.clientSecret", () -> "unused");
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
  void shouldAuthenticateWithPrivateKeyJwtAssertion() throws Exception {
    final String assertion =
        createClientAssertion(
            CLIENT_ID,
            KeycloakTestSupport.tokenEndpoint(KEYCLOAK, REALM),
            rsaKey,
            Instant.now().plusSeconds(300));

    final Map<String, Object> tokenResponse = requestTokenWithAssertion(assertion);

    assertThat(tokenResponse).containsKey("access_token");
    final String accessToken = (String) tokenResponse.get("access_token");
    assertThat(accessToken).isNotBlank();
  }

  @Test
  void shouldRejectExpiredJwtAssertion() throws Exception {
    final String assertion =
        createClientAssertion(
            CLIENT_ID,
            KeycloakTestSupport.tokenEndpoint(KEYCLOAK, REALM),
            rsaKey,
            Instant.now().minusSeconds(3600)); // Already expired

    final KeycloakTestSupport.TokenResponse response = postTokenRequestRaw(assertion);

    assertThat(response.statusCode()).isNotEqualTo(200);
  }

  @Test
  void shouldRejectAssertionSignedWithWrongKey() throws Exception {
    // Generate a different RSA key
    final RSAKey wrongKey =
        new RSAKeyGenerator(2048).keyID("wrong-key").algorithm(JWSAlgorithm.RS256).generate();

    final String assertion =
        createClientAssertion(
            CLIENT_ID,
            KeycloakTestSupport.tokenEndpoint(KEYCLOAK, REALM),
            wrongKey,
            Instant.now().plusSeconds(300));

    final KeycloakTestSupport.TokenResponse response = postTokenRequestRaw(assertion);

    assertThat(response.statusCode()).isNotEqualTo(200);
  }

  @Test
  void shouldAcceptResultingTokenOnProtectedEndpoint() throws Exception {
    final String assertion =
        createClientAssertion(
            CLIENT_ID,
            KeycloakTestSupport.tokenEndpoint(KEYCLOAK, REALM),
            rsaKey,
            Instant.now().plusSeconds(300));

    final Map<String, Object> tokenResponse = requestTokenWithAssertion(assertion);
    final String accessToken = (String) tokenResponse.get("access_token");

    final ResponseEntity<String> response = performAuthenticatedGet("/v1/test", accessToken);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(response.getBody()).isEqualTo("ok");
  }

  /**
   * Creates a signed JWT client assertion for private_key_jwt authentication.
   *
   * @param clientId the client ID (used as issuer and subject)
   * @param tokenEndpoint the token endpoint URL (used as audience)
   * @param key the RSA key to sign with
   * @param expiration the expiration time for the assertion
   * @return the serialized JWT assertion
   */
  private String createClientAssertion(
      final String clientId, final String tokenEndpoint, final RSAKey key, final Instant expiration)
      throws Exception {
    final JWTClaimsSet claims =
        new JWTClaimsSet.Builder()
            .issuer(clientId)
            .subject(clientId)
            .audience(tokenEndpoint)
            .jwtID(UUID.randomUUID().toString())
            .issueTime(new Date())
            .expirationTime(Date.from(expiration))
            .build();

    final SignedJWT signedJwt =
        new SignedJWT(
            new JWSHeader.Builder(JWSAlgorithm.RS256).keyID(key.getKeyID()).build(), claims);
    signedJwt.sign(new RSASSASigner(key));
    return signedJwt.serialize();
  }

  @SuppressWarnings("unchecked")
  private Map<String, Object> requestTokenWithAssertion(final String assertion) {
    final String body =
        "grant_type=client_credentials"
            + "&client_id="
            + URLEncoder.encode(CLIENT_ID, StandardCharsets.UTF_8)
            + "&client_assertion_type="
            + URLEncoder.encode(
                "urn:ietf:params:oauth:client-assertion-type:jwt-bearer", StandardCharsets.UTF_8)
            + "&client_assertion="
            + URLEncoder.encode(assertion, StandardCharsets.UTF_8);

    final HttpRequest request =
        HttpRequest.newBuilder()
            .uri(URI.create(KeycloakTestSupport.tokenEndpoint(KEYCLOAK, REALM)))
            .header("Content-Type", "application/x-www-form-urlencoded")
            .POST(HttpRequest.BodyPublishers.ofString(body))
            .timeout(Duration.ofSeconds(30))
            .build();

    try {
      final HttpResponse<String> response =
          HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofString());
      if (response.statusCode() != 200) {
        throw new IllegalStateException(
            "Token request failed with status %d: %s"
                .formatted(response.statusCode(), response.body()));
      }
      return OBJECT_MAPPER.readValue(response.body(), new TypeReference<>() {});
    } catch (final Exception e) {
      throw new IllegalStateException("Failed to request token with assertion", e);
    }
  }

  private KeycloakTestSupport.TokenResponse postTokenRequestRaw(final String assertion) {
    final String body =
        "grant_type=client_credentials"
            + "&client_id="
            + URLEncoder.encode(CLIENT_ID, StandardCharsets.UTF_8)
            + "&client_assertion_type="
            + URLEncoder.encode(
                "urn:ietf:params:oauth:client-assertion-type:jwt-bearer", StandardCharsets.UTF_8)
            + "&client_assertion="
            + URLEncoder.encode(assertion, StandardCharsets.UTF_8);

    return KeycloakTestSupport.postTokenRequest(
        KeycloakTestSupport.tokenEndpoint(KEYCLOAK, REALM), body);
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
