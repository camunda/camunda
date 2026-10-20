/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.it.physicaltenant;

import static org.assertj.core.api.Assertions.assertThat;

import dasniko.testcontainers.keycloak.KeycloakContainer;
import io.camunda.security.api.model.config.AuthenticationMethod;
import io.camunda.zeebe.qa.util.cluster.PhysicalTenantsITHelper;
import io.camunda.zeebe.qa.util.cluster.PhysicalTenantsITHelper.Storage;
import io.camunda.zeebe.qa.util.cluster.TestStandaloneBroker;
import io.camunda.zeebe.qa.util.junit.ZeebeIntegration;
import io.camunda.zeebe.qa.util.junit.ZeebeIntegration.TestZeebe;
import io.camunda.zeebe.test.testcontainers.DefaultTestContainers;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpRequest.BodyPublishers;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodyHandlers;
import java.time.Duration;
import java.util.List;
import java.util.regex.Pattern;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.keycloak.representations.idm.ClientRepresentation;
import org.keycloak.representations.idm.RealmRepresentation;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
@ZeebeIntegration
final class PhysicalTenantEncodedPrefixIsolationIT {

  @Container
  static final KeycloakContainer KEYCLOAK = DefaultTestContainers.createDefaultKeycloak();

  private static final Pattern ACCESS_TOKEN =
      Pattern.compile("\"access_token\"\\s*:\\s*\"([^\"]+)\"");
  private static final String MARKER_ROLE = "only-in-default-marker";

  private static final PhysicalTenantsITHelper TENANTS =
      PhysicalTenantsITHelper.builder()
          .withTenant(PhysicalTenantsITHelper.DEFAULT_TENANT_ID, Storage.rdbmsH2("default"))
          .withTenant("tenanta", Storage.rdbmsH2("tenanta"))
          .build();

  @TestZeebe(autoStart = false, purgeAfterEach = false)
  private static final TestStandaloneBroker BROKER =
      TENANTS.configure(
          new TestStandaloneBroker()
              .withAuthenticatedAccess()
              .withAuthenticationMethod(AuthenticationMethod.OIDC));

  private static String rootToken;
  private static String tenantAToken;

  @BeforeAll
  static void start() throws Exception {
    createRealm("realm-root", "client-root", "secret-root");
    createRealm("realm-a", "client-a", "secret-a");
    final var rootIssuer = KEYCLOAK.getAuthServerUrl() + "/realms/realm-root";
    final var issuerA = KEYCLOAK.getAuthServerUrl() + "/realms/realm-a";
    BROKER.withSecurityConfig(
        c -> {
          c.getAuthentication().getOidc().setIssuerUri(rootIssuer);
          c.getAuthentication().getOidc().setClientId("client-root");
          c.getAuthentication().getOidc().setRedirectUri("{baseUrl}/login/oauth2/code/oidc");
        });
    BROKER.withPtConfig(
        "tenanta",
        c -> {
          final var oidc = c.getSecurity().getAuthentication().getOidc();
          oidc.setIssuerUri(issuerA);
          oidc.setClientId("client-a");
          oidc.setRedirectUri("{baseUrl}/login/oauth2/code/oidc");
        });
    BROKER.withProperty(
        "camunda.physical-tenants.tenanta.security.authentication.providers.assigned[0]", "oidc");
    BROKER.start();
    rootToken = fetchToken("realm-root", "client-root", "secret-root");
    tenantAToken = fetchToken("realm-a", "client-a", "secret-a");

    Awaitility.await("both tenants serve")
        .atMost(Duration.ofSeconds(60))
        .ignoreExceptions()
        .untilAsserted(
            () -> {
              assertThat(send("GET", "/v2/authentication/me", rootToken, null).statusCode())
                  .isEqualTo(200);
              assertThat(
                      send(
                              "GET",
                              "/physical-tenants/tenanta/v2/authentication/me",
                              tenantAToken,
                              null)
                          .statusCode())
                  .isEqualTo(200);
            });

    final var created =
        send(
            "POST",
            "/v2/roles",
            rootToken,
            "{\"roleId\":\"" + MARKER_ROLE + "\",\"name\":\"" + MARKER_ROLE + "\"}");
    assertThat(created.statusCode()).as(created.body()).isLessThan(300);
    Awaitility.await("marker role searchable in default")
        .atMost(Duration.ofSeconds(60))
        .ignoreExceptions()
        .untilAsserted(
            () ->
                assertThat(send("POST", "/v2/roles/search", rootToken, "{}").body())
                    .contains(MARKER_ROLE));
  }

  @Test
  void shouldNotExposeDefaultTenantThroughEncodedPrefix() throws Exception {
    // given — a role that exists only in the default tenant, and a token only tenanta's IdP issued

    // when
    final var rootPath = send("POST", "/v2/roles/search", tenantAToken, "{}");
    final var plain = send("POST", "/physical-tenants/tenanta/v2/roles/search", tenantAToken, "{}");
    final var encodedPrefix =
        send("POST", "/physical%2Dtenants/tenanta/v2/roles/search", tenantAToken, "{}");
    final var encodedPrefixMe =
        send("GET", "/physical%2Dtenants/tenanta/v2/authentication/me", tenantAToken, null);

    // then
    assertThat(rootPath.statusCode())
        .as("tenanta's token is not valid for the default tenant")
        .isEqualTo(401);
    assertThat(plain.statusCode()).isEqualTo(200);
    assertThat(plain.body()).doesNotContain(MARKER_ROLE);
    assertThat(encodedPrefix.statusCode()).isEqualTo(400);
    assertThat(encodedPrefix.body()).doesNotContain(MARKER_ROLE);
    assertThat(encodedPrefixMe.statusCode()).isEqualTo(400);
  }

  @Test
  void shouldNotWriteToDefaultTenantThroughEncodedPrefix() throws Exception {
    // given
    final var roleId = "written-via-encoded-prefix";

    // when
    final var write =
        send(
            "POST",
            "/physical%2Dtenants/tenanta/v2/roles",
            tenantAToken,
            "{\"roleId\":\"" + roleId + "\",\"name\":\"x\"}");

    // then
    assertThat(write.statusCode()).isEqualTo(400);
    assertThat(send("POST", "/v2/roles/search", rootToken, "{}").body()).doesNotContain(roleId);
  }

  private static HttpResponse<String> send(
      final String method, final String rawPath, final String token, final String body)
      throws Exception {
    final var base = BROKER.restAddress().toString().replaceAll("/+$", "");
    try (final var client = HttpClient.newHttpClient()) {
      return client.send(
          HttpRequest.newBuilder(URI.create(base + rawPath))
              .header("Authorization", "Bearer " + token)
              .header("Content-Type", "application/json")
              .method(
                  method, body == null ? BodyPublishers.noBody() : BodyPublishers.ofString(body))
              .build(),
          BodyHandlers.ofString());
    }
  }

  private static String fetchToken(final String realm, final String clientId, final String secret)
      throws Exception {
    final var form =
        "grant_type=client_credentials&client_id=" + clientId + "&client_secret=" + secret;
    try (final var client = HttpClient.newHttpClient()) {
      final var response =
          client.send(
              HttpRequest.newBuilder(
                      URI.create(
                          KEYCLOAK.getAuthServerUrl()
                              + "/realms/"
                              + realm
                              + "/protocol/openid-connect/token"))
                  .header("Content-Type", "application/x-www-form-urlencoded")
                  .POST(BodyPublishers.ofString(form))
                  .build(),
              BodyHandlers.ofString());
      final var matcher = ACCESS_TOKEN.matcher(response.body());
      assertThat(matcher.find()).as("token: %s", response.body()).isTrue();
      return matcher.group(1);
    }
  }

  private static void createRealm(final String name, final String clientId, final String secret) {
    final var client = new ClientRepresentation();
    client.setClientId(clientId);
    client.setEnabled(true);
    client.setClientAuthenticatorType("client-secret");
    client.setSecret(secret);
    client.setServiceAccountsEnabled(true);
    final var realm = new RealmRepresentation();
    realm.setRealm(name);
    realm.setEnabled(true);
    realm.setClients(List.of(client));
    try (final var admin = KEYCLOAK.getKeycloakAdminClient()) {
      admin.realms().create(realm);
    }
  }
}
