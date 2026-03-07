/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.auth.it;

import static org.assertj.core.api.Assertions.assertThat;

import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import dasniko.testcontainers.keycloak.KeycloakContainer;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
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
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Integration test validating multi-JWKS (additional JWK Set URIs) support with a real Keycloak
 * IDP. Verifies that the auth library's {@code CompositeJWKSource} and {@code
 * IssuerAwareJWSKeySelector} correctly combine Keycloak's native JWKS with additional JWKS
 * endpoints (e.g., for M2M tokens signed by a separate signing service).
 *
 * <p>Test setup:
 *
 * <ul>
 *   <li>Keycloak with two realms: {@code camunda-primary} and {@code camunda-secondary}
 *   <li>WireMock serving two additional JWKS endpoints with RSA keys for M2M token signing
 *   <li>Spring Boot test app using the auth library's OIDC auto-configuration
 * </ul>
 *
 * <p>Scenarios validated:
 *
 * <ol>
 *   <li>Keycloak token validates via standard Keycloak JWKS
 *   <li>M2M token signed with separate RSA key validates via additional JWKS endpoint
 *   <li>CompositeJWKSource falls through from primary JWKS to additional JWKS for M2M kid
 *   <li>Keycloak token accepted by protected API
 *   <li>M2M token accepted by protected API
 *   <li>Secondary realm M2M token validates via secondary additional JWKS
 *   <li>Cross-issuer rejection: token claiming unknown issuer
 *   <li>Invalid signature rejection: token signed with unregistered key
 *   <li>Expired token rejection
 *   <li>Unknown issuer rejection
 *   <li>Unprotected path accessible without token
 *   <li>Protected path returns 401 without token
 * </ol>
 */
@Testcontainers
@SpringBootTest(
    classes = MultiJwksKeycloakIT.TestApp.class,
    webEnvironment = WebEnvironment.RANDOM_PORT)
@AutoConfigureTestRestTemplate
class MultiJwksKeycloakIT {

  // -- Realm and client constants --

  private static final String PRIMARY_REALM = "camunda-primary";
  private static final String PRIMARY_CLIENT_ID = "web-ui-client";
  private static final String PRIMARY_CLIENT_SECRET = "web-ui-secret";

  private static final String SECONDARY_REALM = "camunda-secondary";
  private static final String SECONDARY_CLIENT_ID = "web-ui-secondary";
  private static final String SECONDARY_CLIENT_SECRET = "web-ui-secondary-secret";

  // -- M2M key IDs --

  private static final String M2M_PRIMARY_KID = "m2m-primary-key";
  private static final String M2M_SECONDARY_KID = "m2m-secondary-key";

  // -- Infrastructure --

  @Container
  private static final KeycloakContainer KEYCLOAK = KeycloakTestSupport.createKeycloak();

  @RegisterExtension
  static WireMockExtension wireMock =
      WireMockExtension.newInstance()
          .options(WireMockConfiguration.wireMockConfig().dynamicPort())
          .build();

  // -- M2M keypairs (generated once) --

  private static RSAKey m2mPrimaryKey;
  private static RSAKey m2mSecondaryKey;

  @LocalServerPort private int port;

  @Autowired private TestRestTemplate restTemplate;

  // -- Setup --

  @BeforeAll
  static void setUpInfrastructure() throws Exception {
    createKeycloakRealms();
    generateM2mKeyPairs();
  }

  @BeforeEach
  void setUpWireMockStubs() {
    stubWireMockJwksEndpoints();
  }

  private static void createKeycloakRealms() {
    final var primaryClient =
        KeycloakTestSupport.createConfidentialClient(PRIMARY_CLIENT_ID, PRIMARY_CLIENT_SECRET);
    KeycloakTestSupport.createRealm(KEYCLOAK, PRIMARY_REALM, List.of(primaryClient));

    final var secondaryClient =
        KeycloakTestSupport.createConfidentialClient(SECONDARY_CLIENT_ID, SECONDARY_CLIENT_SECRET);
    KeycloakTestSupport.createRealm(KEYCLOAK, SECONDARY_REALM, List.of(secondaryClient));
  }

  private static void generateM2mKeyPairs() throws Exception {
    m2mPrimaryKey =
        new RSAKeyGenerator(2048)
            .keyID(M2M_PRIMARY_KID)
            .algorithm(JWSAlgorithm.RS256)
            .generate();

    m2mSecondaryKey =
        new RSAKeyGenerator(2048)
            .keyID(M2M_SECONDARY_KID)
            .algorithm(JWSAlgorithm.RS256)
            .generate();
  }

  private static void stubWireMockJwksEndpoints() {
    // Primary additional JWKS: serves only the m2m-primary-key
    final String primaryJwksJson = new JWKSet(m2mPrimaryKey.toPublicJWK()).toString();
    wireMock.stubFor(
        WireMock.get(WireMock.urlPathEqualTo("/additional/jwks"))
            .willReturn(
                WireMock.aResponse()
                    .withHeader("Content-Type", "application/json")
                    .withBody(primaryJwksJson)));

    // Secondary additional JWKS: serves only the m2m-secondary-key
    final String secondaryJwksJson = new JWKSet(m2mSecondaryKey.toPublicJWK()).toString();
    wireMock.stubFor(
        WireMock.get(WireMock.urlPathEqualTo("/additional-secondary/jwks"))
            .willReturn(
                WireMock.aResponse()
                    .withHeader("Content-Type", "application/json")
                    .withBody(secondaryJwksJson)));
  }

  @DynamicPropertySource
  static void configureProperties(final DynamicPropertyRegistry registry) {
    registry.add("camunda.auth.method", () -> "oidc");
    registry.add("camunda.auth.security.webapp-enabled", () -> "false");

    // Primary provider via default OIDC config path
    registry.add(
        "camunda.security.authentication.oidc.issuerUri",
        () -> KeycloakTestSupport.issuerUri(KEYCLOAK, PRIMARY_REALM));
    registry.add("camunda.security.authentication.oidc.clientId", () -> PRIMARY_CLIENT_ID);
    registry.add("camunda.security.authentication.oidc.clientSecret", () -> PRIMARY_CLIENT_SECRET);
    registry.add(
        "camunda.security.authentication.oidc.tokenUri",
        () -> KeycloakTestSupport.tokenEndpoint(KEYCLOAK, PRIMARY_REALM));
    registry.add(
        "camunda.security.authentication.oidc.authorizationUri",
        () -> KeycloakTestSupport.authorizationUri(KEYCLOAK, PRIMARY_REALM));
    registry.add(
        "camunda.security.authentication.oidc.jwkSetUri",
        () -> KeycloakTestSupport.jwkSetUri(KEYCLOAK, PRIMARY_REALM));
    registry.add(
        "camunda.security.authentication.oidc.grantType", () -> "client_credentials");
    // Additional JWKS for the primary provider: both M2M keys
    registry.add(
        "camunda.security.authentication.oidc.additionalJwkSetUris[0]",
        () -> wireMock.baseUrl() + "/additional/jwks");

    // Secondary provider via providers.oidc map
    registry.add(
        "camunda.security.authentication.providers.oidc.secondary.issuerUri",
        () -> KeycloakTestSupport.issuerUri(KEYCLOAK, SECONDARY_REALM));
    registry.add(
        "camunda.security.authentication.providers.oidc.secondary.clientId",
        () -> SECONDARY_CLIENT_ID);
    registry.add(
        "camunda.security.authentication.providers.oidc.secondary.clientSecret",
        () -> SECONDARY_CLIENT_SECRET);
    registry.add(
        "camunda.security.authentication.providers.oidc.secondary.tokenUri",
        () -> KeycloakTestSupport.tokenEndpoint(KEYCLOAK, SECONDARY_REALM));
    registry.add(
        "camunda.security.authentication.providers.oidc.secondary.authorizationUri",
        () -> KeycloakTestSupport.authorizationUri(KEYCLOAK, SECONDARY_REALM));
    registry.add(
        "camunda.security.authentication.providers.oidc.secondary.jwkSetUri",
        () -> KeycloakTestSupport.jwkSetUri(KEYCLOAK, SECONDARY_REALM));
    registry.add(
        "camunda.security.authentication.providers.oidc.secondary.grantType",
        () -> "client_credentials");
    // Additional JWKS for the secondary provider
    registry.add(
        "camunda.security.authentication.providers.oidc.secondary.additionalJwkSetUris[0]",
        () -> wireMock.baseUrl() + "/additional-secondary/jwks");
  }

  // -----------------------------------------------------------------------
  // Scenario 1: Keycloak token (primary realm) validates via Keycloak JWKS
  // -----------------------------------------------------------------------

  @Test
  void shouldAcceptKeycloakTokenViaStandardJwks() {
    final String token = acquirePrimaryKeycloakToken();

    final ResponseEntity<String> response = performAuthenticatedGet("/v1/test", token);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(response.getBody()).isEqualTo("ok");
  }

  // -----------------------------------------------------------------------
  // Scenario 2: M2M token signed with separate RSA key validates via additional JWKS
  // -----------------------------------------------------------------------

  @Test
  void shouldAcceptM2mTokenViaAdditionalJwks() throws Exception {
    final String primaryIssuer = KeycloakTestSupport.issuerUri(KEYCLOAK, PRIMARY_REALM);
    final String m2mToken = createM2mToken(primaryIssuer, m2mPrimaryKey, M2M_PRIMARY_KID);

    final ResponseEntity<String> response = performAuthenticatedGet("/v1/test", m2mToken);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(response.getBody()).isEqualTo("ok");
  }

  // -----------------------------------------------------------------------
  // Scenario 3: CompositeJWKSource falls through from primary JWKS to additional
  // -----------------------------------------------------------------------

  @Test
  @SuppressWarnings("unchecked")
  void shouldFallThroughFromPrimaryJwksToAdditionalJwks() throws Exception {
    // The M2M kid is NOT in Keycloak's JWKS; the CompositeJWKSource must fall through
    // to the additional JWKS endpoint served by WireMock.
    final String primaryIssuer = KeycloakTestSupport.issuerUri(KEYCLOAK, PRIMARY_REALM);
    final String m2mToken = createM2mToken(primaryIssuer, m2mPrimaryKey, M2M_PRIMARY_KID);

    final ResponseEntity<Map> response =
        performAuthenticatedGet("/v1/claims", m2mToken, Map.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    final Map<String, Object> claims = response.getBody();
    assertThat(claims).isNotNull();
    assertThat(claims.get("sub")).isEqualTo("m2m-service");
    assertThat(claims.get("iss").toString()).isEqualTo(primaryIssuer);
  }

  // -----------------------------------------------------------------------
  // Scenario 4: Keycloak token accepted by Camunda API (via standard JWKS)
  // -----------------------------------------------------------------------

  @Test
  @SuppressWarnings("unchecked")
  void shouldExtractClaimsFromKeycloakToken() {
    final String token = acquirePrimaryKeycloakToken();

    final ResponseEntity<Map> response = performAuthenticatedGet("/v1/claims", token, Map.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    final Map<String, Object> claims = response.getBody();
    assertThat(claims).isNotNull();
    assertThat(claims.get("iss").toString())
        .isEqualTo(KeycloakTestSupport.issuerUri(KEYCLOAK, PRIMARY_REALM));
    assertThat(claims).containsEntry("azp", PRIMARY_CLIENT_ID);
  }

  // -----------------------------------------------------------------------
  // Scenario 5: M2M token accepted by Camunda API (additional-jwk-set-uris)
  // -----------------------------------------------------------------------

  @Test
  @SuppressWarnings("unchecked")
  void shouldExtractClaimsFromM2mToken() throws Exception {
    final String primaryIssuer = KeycloakTestSupport.issuerUri(KEYCLOAK, PRIMARY_REALM);
    final String m2mToken = createM2mToken(primaryIssuer, m2mPrimaryKey, M2M_PRIMARY_KID);

    final ResponseEntity<Map> response =
        performAuthenticatedGet("/v1/claims", m2mToken, Map.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    final Map<String, Object> claims = response.getBody();
    assertThat(claims).isNotNull();
    assertThat(claims.get("sub")).isEqualTo("m2m-service");
    assertThat(claims).containsKey("exp");
  }

  // -----------------------------------------------------------------------
  // Scenario 6: Secondary realm Keycloak token validates
  // -----------------------------------------------------------------------

  @Test
  void shouldAcceptSecondaryRealmKeycloakToken() {
    final String token = acquireSecondaryKeycloakToken();

    final ResponseEntity<String> response = performAuthenticatedGet("/v1/test", token);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(response.getBody()).isEqualTo("ok");
  }

  // -----------------------------------------------------------------------
  // Scenario 6b: Secondary realm M2M token validates via secondary additional JWKS
  // -----------------------------------------------------------------------

  @Test
  void shouldAcceptSecondaryRealmM2mTokenViaAdditionalJwks() throws Exception {
    final String secondaryIssuer = KeycloakTestSupport.issuerUri(KEYCLOAK, SECONDARY_REALM);
    final String m2mToken =
        createM2mToken(secondaryIssuer, m2mSecondaryKey, M2M_SECONDARY_KID);

    final ResponseEntity<String> response = performAuthenticatedGet("/v1/test", m2mToken);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(response.getBody()).isEqualTo("ok");
  }

  // -----------------------------------------------------------------------
  // Scenario 7: Cross-issuer rejection: token with completely unknown issuer
  // -----------------------------------------------------------------------

  @Test
  void shouldRejectTokenFromUnknownIssuer() throws Exception {
    final String m2mToken =
        createM2mToken("http://unknown-issuer.example.com", m2mPrimaryKey, M2M_PRIMARY_KID);

    final ResponseEntity<String> response = performAuthenticatedGet("/v1/test", m2mToken);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
  }

  // -----------------------------------------------------------------------
  // Scenario 8: Invalid signature: token signed with key not in any configured JWKS
  // -----------------------------------------------------------------------

  @Test
  void shouldRejectTokenSignedWithWrongKey() throws Exception {
    // Generate a completely unknown RSA key not registered in any JWKS
    final RSAKey unknownKey =
        new RSAKeyGenerator(2048)
            .keyID("unknown-key-" + UUID.randomUUID())
            .algorithm(JWSAlgorithm.RS256)
            .generate();

    final String primaryIssuer = KeycloakTestSupport.issuerUri(KEYCLOAK, PRIMARY_REALM);
    final String badToken = createM2mToken(primaryIssuer, unknownKey, unknownKey.getKeyID());

    final ResponseEntity<String> response = performAuthenticatedGet("/v1/test", badToken);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
  }

  // -----------------------------------------------------------------------
  // Scenario 9: Expired token rejected
  // -----------------------------------------------------------------------

  @Test
  void shouldRejectExpiredToken() throws Exception {
    final String primaryIssuer = KeycloakTestSupport.issuerUri(KEYCLOAK, PRIMARY_REALM);
    final String expiredToken = createExpiredM2mToken(primaryIssuer, m2mPrimaryKey);

    final ResponseEntity<String> response = performAuthenticatedGet("/v1/test", expiredToken);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
  }

  // -----------------------------------------------------------------------
  // Scenario 10: Unknown issuer rejected (variant: empty issuer)
  // -----------------------------------------------------------------------

  @Test
  void shouldRejectTokenWithNonMatchingIssuer() throws Exception {
    // Use a structurally valid but non-matching issuer
    final String m2mToken =
        createM2mToken(
            "http://totally-different-idp.example.org/realms/fake",
            m2mPrimaryKey,
            M2M_PRIMARY_KID);

    final ResponseEntity<String> response = performAuthenticatedGet("/v1/test", m2mToken);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
  }

  // -----------------------------------------------------------------------
  // Scenario 11: Unprotected path accessible without token
  // -----------------------------------------------------------------------

  @Test
  void shouldAllowUnprotectedPathWithoutToken() {
    final ResponseEntity<String> response =
        restTemplate.getForEntity("http://localhost:" + port + "/actuator/health", String.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(response.getBody()).isEqualTo("UP");
  }

  // -----------------------------------------------------------------------
  // Scenario 12: Protected path requires valid token (returns 401 without)
  // -----------------------------------------------------------------------

  @Test
  void shouldRequireTokenForProtectedPath() {
    final ResponseEntity<String> response =
        restTemplate.getForEntity("http://localhost:" + port + "/v1/test", String.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
  }

  // -----------------------------------------------------------------------
  // Bonus: Key rotation — update WireMock JWKS, verify new key works
  // -----------------------------------------------------------------------

  @Test
  void shouldHandleKeyRotation() throws Exception {
    // Generate a brand-new M2M key
    final RSAKey rotatedKey =
        new RSAKeyGenerator(2048)
            .keyID("m2m-rotated-key")
            .algorithm(JWSAlgorithm.RS256)
            .generate();

    // Update the WireMock stub for primary additional JWKS to include both old and new keys
    final String rotatedJwksJson =
        new JWKSet(List.of(m2mPrimaryKey.toPublicJWK(), rotatedKey.toPublicJWK())).toString();
    wireMock.stubFor(
        WireMock.get(WireMock.urlPathEqualTo("/additional/jwks"))
            .willReturn(
                WireMock.aResponse()
                    .withHeader("Content-Type", "application/json")
                    .withBody(rotatedJwksJson)));

    final String primaryIssuer = KeycloakTestSupport.issuerUri(KEYCLOAK, PRIMARY_REALM);
    final String rotatedToken =
        createM2mToken(primaryIssuer, rotatedKey, "m2m-rotated-key");

    final ResponseEntity<String> response = performAuthenticatedGet("/v1/test", rotatedToken);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(response.getBody()).isEqualTo("ok");
  }

  // =======================================================================
  // Helper methods
  // =======================================================================

  private String acquirePrimaryKeycloakToken() {
    return KeycloakTestSupport.acquireToken(
        KeycloakTestSupport.tokenEndpoint(KEYCLOAK, PRIMARY_REALM),
        PRIMARY_CLIENT_ID,
        PRIMARY_CLIENT_SECRET);
  }

  private String acquireSecondaryKeycloakToken() {
    return KeycloakTestSupport.acquireToken(
        KeycloakTestSupport.tokenEndpoint(KEYCLOAK, SECONDARY_REALM),
        SECONDARY_CLIENT_ID,
        SECONDARY_CLIENT_SECRET);
  }

  /**
   * Creates an M2M JWT signed with the given RSA key. The token claims the specified issuer (which
   * must match a configured Keycloak realm issuer) but is signed with a key from the additional
   * JWKS, not from Keycloak itself.
   */
  private static String createM2mToken(
      final String issuer, final RSAKey signingKey, final String kid) throws Exception {
    final JWTClaimsSet claims =
        new JWTClaimsSet.Builder()
            .issuer(issuer)
            .subject("m2m-service")
            .audience("camunda-api")
            .expirationTime(Date.from(Instant.now().plusSeconds(300)))
            .issueTime(new Date())
            .jwtID(UUID.randomUUID().toString())
            .build();

    final SignedJWT signedJwt =
        new SignedJWT(
            new JWSHeader.Builder(JWSAlgorithm.RS256).keyID(kid).build(), claims);
    signedJwt.sign(new RSASSASigner(signingKey));
    return signedJwt.serialize();
  }

  /**
   * Creates an M2M JWT that expired 2 hours ago. Uses the primary M2M key so the signature itself
   * is valid — only the expiration should cause rejection.
   */
  private static String createExpiredM2mToken(final String issuer, final RSAKey signingKey)
      throws Exception {
    final JWTClaimsSet claims =
        new JWTClaimsSet.Builder()
            .issuer(issuer)
            .subject("m2m-service")
            .audience("camunda-api")
            .expirationTime(Date.from(Instant.now().minusSeconds(7200)))
            .issueTime(Date.from(Instant.now().minusSeconds(14400)))
            .jwtID(UUID.randomUUID().toString())
            .build();

    final SignedJWT signedJwt =
        new SignedJWT(
            new JWSHeader.Builder(JWSAlgorithm.RS256).keyID(signingKey.getKeyID()).build(),
            claims);
    signedJwt.sign(new RSASSASigner(signingKey));
    return signedJwt.serialize();
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

  // =======================================================================
  // Test application
  // =======================================================================

  @SpringBootApplication(exclude = {DataSourceAutoConfiguration.class})
  static class TestApp {

    @RestController
    static class TestController {

      @GetMapping("/v1/test")
      String test() {
        return "ok";
      }

      @GetMapping("/v1/claims")
      Map<String, Object> claims(
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
