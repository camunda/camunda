/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.it.auth;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import dasniko.testcontainers.keycloak.KeycloakContainer;
import io.camunda.security.api.model.config.AuthenticationMethod;
import io.camunda.zeebe.qa.util.cluster.TestStandaloneBroker;
import io.camunda.zeebe.qa.util.junit.ZeebeIntegration;
import io.camunda.zeebe.qa.util.junit.ZeebeIntegration.TestZeebe;
import io.camunda.zeebe.test.testcontainers.DefaultTestContainers;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.keycloak.representations.idm.ClientRepresentation;
import org.keycloak.representations.idm.ProtocolMapperRepresentation;
import org.keycloak.representations.idm.RealmRepresentation;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Full-stack integration tests for the cluster-admin OIDC chain ({@code /cluster/v2/**}), exercised
 * against the real {@link io.camunda.zeebe.gateway.rest.controller.DummyClusterTopologyController}
 * endpoint with raw bearer tokens minted by Keycloak.
 */
@Testcontainers
@ZeebeIntegration
public class ClusterAdminOidcAuthenticationIT {

  private static final String PATH_CLUSTER_TOPOLOGY = "cluster/v2/topology";
  private static final String REALM = "camunda";

  // The provider claims the cluster-admin matcher reads from the token. "azp" is set by Keycloak to
  // the authorized party (the client id) on every client_credentials token, so no mapper is needed
  // for the client-id dimension.
  private static final String CLIENT_ID_CLAIM = "azp";
  private static final String GROUPS_CLAIM = "groups";
  private static final String GENERIC_CLAIM_NAME = "roles";
  private static final String GENERIC_CLAIM_VALUE = "cluster-admin";

  // Keycloak clients. Each grants cluster-admin via exactly one dimension, plus one that matches
  // nothing.
  private static final String CLIENT_BY_ID = "cluster-admin-by-id";
  private static final String CLIENT_BY_GROUP = "cluster-admin-by-group";
  private static final String CLIENT_BY_CLAIM = "cluster-admin-by-claim";
  private static final String CLIENT_STRANGER = "stranger";
  private static final String CONFIGURED_GROUP = "cluster-admins";

  private static final ObjectMapper JSON = new ObjectMapper();
  private static final HttpClient HTTP = HttpClient.newHttpClient();

  @Container
  private static final KeycloakContainer KEYCLOAK = DefaultTestContainers.createDefaultKeycloak();

  // purgeAfterEach = false: the tests only issue HTTP calls to the dummy endpoint and create no
  // process data, so the inter-test cluster purge is unnecessary
  @TestZeebe(autoStart = false, purgeAfterEach = false)
  private static final TestStandaloneBroker BROKER =
      new TestStandaloneBroker()
          .withAuthenticatedAccess()
          .withAuthenticationMethod(AuthenticationMethod.OIDC)
          .withSecurityConfig(
              c -> {
                c.getAuthentication().getOidc().setClientIdClaim(CLIENT_ID_CLAIM);
                c.getAuthentication().getOidc().setGroupsClaim(GROUPS_CLAIM);
              })
          // Only these three matchers grant cluster-admin; CLIENT_STRANGER matches none of them.
          .withProperty("camunda.security.cluster-admin.oidc.clients[0]", CLIENT_BY_ID)
          .withProperty("camunda.security.cluster-admin.oidc.groups[0]", CONFIGURED_GROUP)
          .withProperty("camunda.security.cluster-admin.oidc.claims[0].name", GENERIC_CLAIM_NAME)
          .withProperty("camunda.security.cluster-admin.oidc.claims[0].value", GENERIC_CLAIM_VALUE);

  @BeforeAll
  static void setUp() {
    configureRealm();
    final String issuerUri = KEYCLOAK.getAuthServerUrl() + "/realms/" + REALM;
    // redirectUri is a mandatory field of Spring's OAuth2 client registration even in
    // resource-server mode where no browser redirect occurs.
    BROKER.withSecurityConfig(
        c -> {
          c.getAuthentication().getOidc().setIssuerUri(issuerUri);
          c.getAuthentication().getOidc().setClientId("example");
          c.getAuthentication().getOidc().setRedirectUri("https://example.com");
        });
    BROKER.start();

    Awaitility.await("cluster-admin endpoint is serving")
        .atMost(Duration.ofSeconds(30))
        .ignoreExceptions()
        .untilAsserted(
            () ->
                assertThat(get(clusterUri(), null).statusCode())
                    .isEqualTo(HttpURLConnection.HTTP_UNAUTHORIZED));
  }

  @Test
  void shouldAllowWhenClientIdMatches() throws Exception {
    // when — a valid token whose client id (azp) matches the configured cluster-admin client
    final HttpResponse<String> response = get(clusterUri(), bearer(accessToken(CLIENT_BY_ID)));

    // then
    assertThat(response.statusCode()).isEqualTo(HttpURLConnection.HTTP_OK);
  }

  @Test
  void shouldAllowWhenGroupMatches() throws Exception {
    // when — a token carrying the configured group
    final HttpResponse<String> response = get(clusterUri(), bearer(accessToken(CLIENT_BY_GROUP)));

    // then
    assertThat(response.statusCode()).isEqualTo(HttpURLConnection.HTTP_OK);
  }

  @Test
  void shouldAllowWhenGenericClaimMatches() throws Exception {
    // when — a token carrying the configured generic claim; neither its client id nor group matches
    final HttpResponse<String> response = get(clusterUri(), bearer(accessToken(CLIENT_BY_CLAIM)));

    // then
    assertThat(response.statusCode()).isEqualTo(HttpURLConnection.HTTP_OK);
  }

  @Test
  void shouldForbidValidTokenThatMatchesNothing() throws Exception {
    // when — a valid token that matches no configured client, group, or claim
    final HttpResponse<String> response = get(clusterUri(), bearer(accessToken(CLIENT_STRANGER)));

    // then — authenticated, but without ROLE_CLUSTER_ADMIN the chain denies with 403 via the
    // shared AuthFailureHandler (problem+json), matching the Basic chain
    assertThat(response.statusCode()).isEqualTo(HttpURLConnection.HTTP_FORBIDDEN);
    assertThat(response.headers().firstValue("content-type").orElse(""))
        .contains("application/problem+json");
    assertThat(response.body()).contains("\"status\":403");
  }

  @Test
  void shouldRejectMissingToken() throws Exception {
    // when
    final HttpResponse<String> response = get(clusterUri(), null);

    // then — 401 via the shared handler (problem+json), not the default bearer challenge
    assertThat(response.statusCode()).isEqualTo(HttpURLConnection.HTTP_UNAUTHORIZED);
    assertThat(response.headers().firstValue("content-type").orElse(""))
        .contains("application/problem+json");
    assertThat(response.body()).contains("\"status\":401");
  }

  @Test
  void shouldRejectMalformedToken() throws Exception {
    // when
    final HttpResponse<String> response = get(clusterUri(), "Bearer not-a-valid-jwt");

    // then — 401 via the shared handler (problem+json), not the default bearer challenge
    assertThat(response.statusCode()).isEqualTo(HttpURLConnection.HTTP_UNAUTHORIZED);
    assertThat(response.headers().firstValue("content-type").orElse(""))
        .contains("application/problem+json");
    assertThat(response.body()).contains("\"status\":401");
  }

  private static void configureRealm() {
    final RealmRepresentation realm = new RealmRepresentation();
    realm.setRealm(REALM);
    realm.setEnabled(true);
    realm.setClients(
        List.of(
            client(CLIENT_BY_ID),
            client(CLIENT_BY_GROUP, hardcodedClaim(GROUPS_CLAIM, CONFIGURED_GROUP)),
            client(CLIENT_BY_CLAIM, hardcodedClaim(GENERIC_CLAIM_NAME, GENERIC_CLAIM_VALUE)),
            client(CLIENT_STRANGER)));
    try (final var admin = KEYCLOAK.getKeycloakAdminClient()) {
      admin.realms().create(realm);
    }
  }

  private static ClientRepresentation client(
      final String clientId, final ProtocolMapperRepresentation... mappers) {
    final ClientRepresentation client = new ClientRepresentation();
    client.setClientId(clientId);
    client.setEnabled(true);
    client.setClientAuthenticatorType("client-secret");
    client.setSecret(clientId); // secret == clientId, mirroring the shared Keycloak test fixtures
    client.setServiceAccountsEnabled(true); // enables the client_credentials grant
    if (mappers.length > 0) {
      client.setProtocolMappers(List.of(mappers));
    }
    return client;
  }

  private static ProtocolMapperRepresentation hardcodedClaim(
      final String claimName, final String claimValue) {
    final ProtocolMapperRepresentation mapper = new ProtocolMapperRepresentation();
    mapper.setName(claimName + "-mapper");
    mapper.setProtocol("openid-connect");
    mapper.setProtocolMapper("oidc-hardcoded-claim-mapper");
    mapper.setConfig(
        Map.of(
            "claim.name", claimName,
            "claim.value", claimValue,
            "jsonType.label", "String",
            "access.token.claim", "true",
            "id.token.claim", "true"));
    return mapper;
  }

  private static String accessToken(final String clientId) throws Exception {
    final String form =
        "grant_type=client_credentials&client_id=" + clientId + "&client_secret=" + clientId;
    final HttpRequest request =
        HttpRequest.newBuilder()
            .uri(
                URI.create(
                    KEYCLOAK.getAuthServerUrl()
                        + "/realms/"
                        + REALM
                        + "/protocol/openid-connect/token"))
            .header("Content-Type", "application/x-www-form-urlencoded")
            .POST(HttpRequest.BodyPublishers.ofString(form))
            .build();
    final HttpResponse<String> response = HTTP.send(request, HttpResponse.BodyHandlers.ofString());
    assertThat(response.statusCode()).isEqualTo(HttpURLConnection.HTTP_OK);
    return JSON.readTree(response.body()).get("access_token").asText();
  }

  private static HttpResponse<String> get(final URI uri, final String authorizationHeader)
      throws Exception {
    final HttpRequest.Builder builder = HttpRequest.newBuilder().uri(uri).GET();
    if (authorizationHeader != null) {
      builder.header("Authorization", authorizationHeader);
    }
    return HTTP.send(builder.build(), HttpResponse.BodyHandlers.ofString());
  }

  private static String bearer(final String token) {
    return "Bearer " + token;
  }

  // The cluster-admin API is cluster-wide and always addressed at the gateway root. restAddress()
  // is that root (no physical-tenant prefix), so no stripping is needed here.
  private static URI clusterUri() {
    return BROKER.restAddress().resolve(PATH_CLUSTER_TOPOLOGY);
  }
}
