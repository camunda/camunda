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
import io.camunda.zeebe.qa.util.cluster.PhysicalTenantsITHelper;
import io.camunda.zeebe.qa.util.cluster.PhysicalTenantsITHelper.Storage;
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
import java.util.ArrayList;
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
 * Verifies that REST resolves the groups of a token with the groups mode of the request's physical
 * tenant and with the {@code groups-claim} of the provider that issued the token (<a
 * href="https://github.com/camunda/camunda/issues/65597">camunda/camunda#65597</a>), as gRPC does.
 * The groups are read from {@code /v2/authentication/me}, which returns the groups the membership
 * service resolved for the caller.
 *
 * <p>Each token carries a {@code groups} claim, the root {@code groups-claim}, next to the claim
 * that the tested setting selects, so a token resolved with the root setting returns other groups.
 */
@Testcontainers
@ZeebeIntegration
final class PhysicalTenantRestOidcGroupsClaimIT {

  @Container
  static final KeycloakContainer KEYCLOAK = DefaultTestContainers.createDefaultKeycloak();

  private static final ObjectMapper JSON = new ObjectMapper();
  private static final HttpClient HTTP = HttpClient.newHttpClient();

  // uses Camunda groups
  private static final String TENANT_A = "tenanta";
  // reads groups from the "teams" claim
  private static final String TENANT_B = "tenantb";

  private static final String REALM_ROOT = "realm-groups-root";
  private static final String REALM_OKTA = "realm-groups-okta";
  private static final String REALM_PLAIN = "realm-groups-plain";
  private static final String ROOT_CLIENT = "root-app";
  private static final String OKTA_CLIENT = "okta-app";
  private static final String PLAIN_CLIENT = "plain-app";

  private static final PhysicalTenantsITHelper TENANTS =
      PhysicalTenantsITHelper.builder()
          .withTenant(
              PhysicalTenantsITHelper.DEFAULT_TENANT_ID, Storage.rdbmsH2("rest-groups-default"))
          .withTenant(TENANT_A, Storage.rdbmsH2("rest-groups-tenanta"))
          .withTenant(TENANT_B, Storage.rdbmsH2("rest-groups-tenantb"))
          .build();

  @TestZeebe(autoStart = false, purgeAfterEach = false)
  private static final TestStandaloneBroker BROKER =
      TENANTS.configure(
          new TestStandaloneBroker()
              .withAuthenticatedAccess()
              .withAuthenticationMethod(AuthenticationMethod.OIDC));

  @BeforeAll
  static void startBroker() {
    createRealm(
        REALM_ROOT, ROOT_CLIENT, Map.of("groups", "[\"root-group\"]", "teams", "[\"root-team\"]"));
    createRealm(
        REALM_OKTA, OKTA_CLIENT, Map.of("groups", "[\"okta-group\"]", "roles", "[\"okta-role\"]"));
    createRealm(
        REALM_PLAIN,
        PLAIN_CLIENT,
        Map.of("groups", "[\"plain-group\"]", "teams", "[\"plain-team\"]"));

    BROKER.withSecurityConfig(
        c -> {
          final var oidc = c.getAuthentication().getOidc();
          oidc.setIssuerUri(issuerUri(REALM_ROOT));
          oidc.setClientId(ROOT_CLIENT);
          oidc.setRedirectUri("{baseUrl}/login/oauth2/code/oidc");
          oidc.setGroupsClaim("groups");
        });
    provider("okta", REALM_OKTA);
    BROKER.withProperty(
        "camunda.security.authentication.providers.oidc.okta.groups-claim", "roles");
    provider("plain", REALM_PLAIN);

    // an empty groups-claim overrides the root value and selects Camunda groups
    BROKER.withProperty(tenantAuthProperty(TENANT_A, "oidc.groups-claim"), "");
    BROKER.withProperty(tenantAuthProperty(TENANT_A, "providers.assigned[0]"), "oidc");
    BROKER.withProperty(tenantAuthProperty(TENANT_B, "oidc.groups-claim"), "teams");
    BROKER.withProperty(tenantAuthProperty(TENANT_B, "providers.assigned[0]"), "plain");

    BROKER.start();

    Awaitility.await("every physical tenant serves the current user")
        .atMost(Duration.ofSeconds(120))
        .ignoreExceptions()
        .untilAsserted(
            () -> {
              currentUserGroups("", REALM_ROOT, ROOT_CLIENT);
              currentUserGroups(tenantPath(TENANT_A), REALM_ROOT, ROOT_CLIENT);
              currentUserGroups(tenantPath(TENANT_B), REALM_PLAIN, PLAIN_CLIENT);
            });
  }

  @Test
  void shouldReadGroupsFromTokenOnTenantThatReadsGroupsFromToken() throws Exception {
    // when
    final var groups = currentUserGroups("", REALM_ROOT, ROOT_CLIENT);

    // then
    assertThat(groups).containsExactly("root-group");
  }

  @Test
  void shouldNotReadGroupsFromTokenOnTenantThatUsesCamundaGroups() throws Exception {
    // when
    final var groups = currentUserGroups(tenantPath(TENANT_A), REALM_ROOT, ROOT_CLIENT);

    // then
    // the client is a member of no Camunda group
    assertThat(groups).isEmpty();
  }

  @Test
  void shouldReadGroupsFromClaimOfIssuingProvider() throws Exception {
    // when
    final var groups = currentUserGroups("", REALM_OKTA, OKTA_CLIENT);

    // then
    assertThat(groups).containsExactly("okta-role");
  }

  @Test
  void shouldReadGroupsFromTenantClaimForProviderWithoutOwnClaim() throws Exception {
    // when
    final var groups = currentUserGroups(tenantPath(TENANT_B), REALM_PLAIN, PLAIN_CLIENT);

    // then
    assertThat(groups).containsExactly("plain-team");
  }

  private static List<String> currentUserGroups(
      final String basePath, final String realm, final String clientId) throws Exception {
    final var request =
        HttpRequest.newBuilder()
            .uri(BROKER.restAddress().resolve(basePath + "v2/authentication/me"))
            .header("Authorization", "Bearer " + accessToken(realm, clientId))
            .GET()
            .build();
    final var response = HTTP.send(request, HttpResponse.BodyHandlers.ofString());
    assertThat(response.statusCode()).as(response.body()).isEqualTo(HttpURLConnection.HTTP_OK);
    final var groups = new ArrayList<String>();
    JSON.readTree(response.body()).path("groups").forEach(group -> groups.add(group.asText()));
    return groups;
  }

  private static String accessToken(final String realm, final String clientId) throws Exception {
    final var form =
        "grant_type=client_credentials&client_id="
            + clientId
            + "&client_secret="
            + secret(clientId);
    final var request =
        HttpRequest.newBuilder()
            .uri(URI.create(issuerUri(realm) + "/protocol/openid-connect/token"))
            .header("Content-Type", "application/x-www-form-urlencoded")
            .POST(HttpRequest.BodyPublishers.ofString(form))
            .build();
    final var response = HTTP.send(request, HttpResponse.BodyHandlers.ofString());
    assertThat(response.statusCode()).isEqualTo(HttpURLConnection.HTTP_OK);
    return JSON.readTree(response.body()).get("access_token").asText();
  }

  private static void provider(final String id, final String realm) {
    final var prefix = "camunda.security.authentication.providers.oidc." + id + ".";
    BROKER
        .withProperty(prefix + "issuer-uri", issuerUri(realm))
        .withProperty(prefix + "client-id", id + "-login")
        .withProperty(prefix + "client-secret", id + "-login-secret")
        .withProperty(prefix + "redirect-uri", "{baseUrl}/sso-callback");
  }

  private static String tenantAuthProperty(final String tenantId, final String key) {
    return "camunda.physical-tenants." + tenantId + ".security.authentication." + key;
  }

  /** The path of a physical tenant relative to the REST address; the cluster surface is "". */
  private static String tenantPath(final String tenantId) {
    return "physical-tenants/" + tenantId + "/";
  }

  private static String issuerUri(final String realm) {
    return KEYCLOAK.getAuthServerUrl() + "/realms/" + realm;
  }

  private static String secret(final String clientId) {
    return clientId + "-secret";
  }

  /** A realm with one service client whose tokens carry the given JSON array claims. */
  private static void createRealm(
      final String realmName, final String clientId, final Map<String, String> arrayClaims) {
    final var mappers = new ArrayList<ProtocolMapperRepresentation>();
    arrayClaims.forEach(
        (name, value) -> {
          final var mapper = new ProtocolMapperRepresentation();
          mapper.setName(name + "-mapper");
          mapper.setProtocol("openid-connect");
          mapper.setProtocolMapper("oidc-hardcoded-claim-mapper");
          mapper.setConfig(
              Map.of(
                  "claim.name", name,
                  "claim.value", value,
                  "jsonType.label", "JSON",
                  "access.token.claim", "true",
                  "id.token.claim", "true"));
          mappers.add(mapper);
        });

    final var client = new ClientRepresentation();
    client.setClientId(clientId);
    client.setEnabled(true);
    client.setClientAuthenticatorType("client-secret");
    client.setSecret(secret(clientId));
    client.setServiceAccountsEnabled(true);
    client.setProtocolMappers(mappers);

    final var realm = new RealmRepresentation();
    realm.setRealm(realmName);
    realm.setEnabled(true);
    realm.setClients(List.of(client));
    try (final var admin = KEYCLOAK.getKeycloakAdminClient()) {
      admin.realms().create(realm);
    }
  }
}
