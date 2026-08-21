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
import io.camunda.client.CamundaClient;
import io.camunda.qa.util.auth.TestUser;
import io.camunda.qa.util.auth.UserDefinition;
import io.camunda.qa.util.multidb.MultiDbTest;
import io.camunda.qa.util.multidb.MultiDbTestApplication;
import io.camunda.zeebe.qa.util.cluster.TestStandaloneBroker;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Base64;
import java.util.List;
import org.junit.jupiter.api.AutoClose;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledIfSystemProperty;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Full-stack integration tests for the cluster-admin Basic-auth chain ({@code /cluster/v2/**}),
 * exercised against the real {@link
 * io.camunda.zeebe.gateway.rest.controller.ClusterTopologyController} endpoint — plus the one
 * carve-out from that chain, the unauthenticated {@link
 * io.camunda.zeebe.gateway.rest.controller.ClusterStatusController}.
 */
@MultiDbTest
@DisabledIfSystemProperty(named = "test.integration.camunda.database.type", matches = "AWS_OS")
public class ClusterAdminBasicAuthenticationIT {

  public static final String PATH_CLUSTER_TOPOLOGY = "cluster/v2/topology";
  public static final String PATH_CLUSTER_MODE = "cluster/v2/mode?mode=RECOVERING&dryRun=true";
  public static final String PATH_CLUSTER_STATUS = "cluster/v2/status";
  public static final String PATH_CLUSTER_HISTORY_BACKUPS = "cluster/v2/backups/history";
  public static final String PATH_CLUSTER_HISTORY_BACKUP = "cluster/v2/backups/history/1";
  public static final String PATH_CLUSTER_RUNTIME_BACKUPS = "cluster/v2/backups/runtime";
  public static final String PATH_CLUSTER_RUNTIME_BACKUP = "cluster/v2/backups/runtime/1";
  public static final String PATH_CLUSTER_RUNTIME_BACKUP_STATE = "cluster/v2/backups/runtime/state";
  public static final String PATH_CLUSTER_REBALANCE = "cluster/v2/rebalance";
  public static final String PATH_V2_AUTHENTICATION_ME = "v2/authentication/me";

  private static final String CLUSTER_ADMIN_USER = "cluster-operator";
  private static final String CLUSTER_ADMIN_PASSWORD = "cluster-secret";
  private static final String CLUSTER_ADMIN_USER_2 = "cluster-operator-2";
  private static final String CLUSTER_ADMIN_PASSWORD_2 = "cluster-secret-2";

  private static final String DB_USERNAME = "db_user";
  private static final String DB_PASSWORD = "db_password";

  @MultiDbTestApplication
  private static final TestStandaloneBroker BROKER =
      new TestStandaloneBroker()
          .withBasicAuth()
          .withAuthenticatedAccess()
          .withProperty("camunda.security.cluster-admin.basic.users[0].name", CLUSTER_ADMIN_USER)
          .withProperty(
              "camunda.security.cluster-admin.basic.users[0].password", CLUSTER_ADMIN_PASSWORD)
          .withProperty("camunda.security.cluster-admin.basic.users[1].name", CLUSTER_ADMIN_USER_2)
          .withProperty(
              "camunda.security.cluster-admin.basic.users[1].password", CLUSTER_ADMIN_PASSWORD_2);

  // A real, secondary-storage-backed user — used to prove it cannot reach the cluster-admin chain.
  @UserDefinition
  private static final TestUser DB_USER = new TestUser(DB_USERNAME, DB_PASSWORD, List.of());

  private static CamundaClient camundaClient;
  @AutoClose private final HttpClient httpClient = HttpClient.newHttpClient();

  @Test
  void shouldAllowClusterAdminEndpointWithFirstConfiguredUser() throws Exception {
    // when
    final HttpResponse<String> response =
        send(
            clusterUri(PATH_CLUSTER_TOPOLOGY),
            basicAuth(CLUSTER_ADMIN_USER, CLUSTER_ADMIN_PASSWORD));

    // then — a real aggregated body, not the placeholder's empty 200
    assertThat(response.statusCode()).isEqualTo(HttpURLConnection.HTTP_OK);
    final var body = new ObjectMapper().readTree(response.body());
    assertThat(body.get("physicalTenants")).isNotNull();
    assertThat(body.get("physicalTenants"))
        .anySatisfy(
            physicalTenant ->
                assertThat(physicalTenant.get("physicalTenantId").asText()).isEqualTo("default"));
    assertThat(body.get("brokers")).isNotEmpty();
  }

  @Test
  void shouldAllowClusterAdminEndpointWithSecondConfiguredUser() throws Exception {
    // when — proves the whole users[] list is bound, not just the first entry
    final HttpResponse<String> response =
        send(
            clusterUri(PATH_CLUSTER_TOPOLOGY),
            basicAuth(CLUSTER_ADMIN_USER_2, CLUSTER_ADMIN_PASSWORD_2));

    // then
    assertThat(response.statusCode()).isEqualTo(HttpURLConnection.HTTP_OK);
  }

  @Test
  void shouldRejectClusterAdminEndpointWithBadCredentials() throws Exception {
    // when
    final HttpResponse<String> response =
        send(clusterUri(PATH_CLUSTER_TOPOLOGY), basicAuth(CLUSTER_ADMIN_USER, "wrong-password"));

    // then
    assertThat(response.statusCode()).isEqualTo(HttpURLConnection.HTTP_UNAUTHORIZED);
  }

  @Test
  void shouldRejectRealDbBackedUserOnClusterAdminEndpoint() throws Exception {
    // when — a real, secondary-storage-backed user presents valid DB credentials to the cluster API
    final HttpResponse<String> response =
        send(clusterUri(PATH_CLUSTER_TOPOLOGY), basicAuth(DB_USERNAME, DB_PASSWORD));

    // then — the cluster-admin chain has its own isolated store; a DB user is not known to it
    assertThat(response.statusCode()).isEqualTo(HttpURLConnection.HTTP_UNAUTHORIZED);
  }

  @ParameterizedTest
  @ValueSource(strings = {"GET", "POST", "DELETE"})
  void shouldRejectRebalanceWithoutCredentials(final String method) throws Exception {
    // when
    final HttpResponse<String> response = send(method, clusterUri(PATH_CLUSTER_REBALANCE), null);

    // then
    assertThat(response.statusCode()).isEqualTo(HttpURLConnection.HTTP_UNAUTHORIZED);
  }

  @Test
  void shouldRejectModeChangeWithoutCredentials() throws Exception {
    // when — the mode change is a state-changing cluster-admin endpoint, so it must sit inside the
    // protected chain rather than under a pattern the chain does not match
    final HttpRequest.Builder builder =
        HttpRequest.newBuilder(clusterUri(PATH_CLUSTER_MODE))
            .method("PATCH", HttpRequest.BodyPublishers.noBody());
    final HttpResponse<String> response =
        httpClient.send(builder.build(), HttpResponse.BodyHandlers.ofString());

    // then
    assertThat(response.statusCode()).isEqualTo(HttpURLConnection.HTTP_UNAUTHORIZED);
  }

  @Test
  void shouldAllowPublicStatusEndpointWithoutCredentials() throws Exception {
    // when — the public cluster status endpoint is hit with no credentials
    final HttpResponse<String> response = send(clusterUri(PATH_CLUSTER_STATUS), null);

    // then — reachable unauthenticated, reporting the aggregated status and nothing else, so an
    // unauthenticated caller cannot enumerate the cluster's physical tenants
    assertThat(response.statusCode()).isEqualTo(HttpURLConnection.HTTP_OK);
    assertThat(new ObjectMapper().readTree(response.body()).properties())
        .singleElement()
        .satisfies(
            entry -> {
              assertThat(entry.getKey()).isEqualTo("status");
              assertThat(entry.getValue().asText()).isIn("HEALTHY", "DEGRADED");
            });
  }

  @Test
  void shouldAllowPublicStatusEndpointWithAWrongPassword() throws Exception {
    // when — the public status endpoint is hit with a wrong password for a known cluster-admin user
    final HttpResponse<String> response =
        send(clusterUri(PATH_CLUSTER_STATUS), basicAuth(CLUSTER_ADMIN_USER, "wrong-password"));

    // then — the status endpoint has its own chain with no Basic auth filter, so the credential is
    // never verified. A health check must not fail because the caller sent a stale password.
    assertThat(response.statusCode()).isEqualTo(HttpURLConnection.HTTP_OK);
  }

  @Test
  void shouldRejectClusterAdminCredentialsOnRegularV2Endpoint() throws Exception {
    // when — cluster-admin credentials presented to the regular /v2 API
    final HttpResponse<String> response =
        send(
            apiUri(PATH_V2_AUTHENTICATION_ME),
            basicAuth(CLUSTER_ADMIN_USER, CLUSTER_ADMIN_PASSWORD));

    // then — cluster-admin users exist only for /cluster/v2/**
    assertThat(response.statusCode()).isEqualTo(HttpURLConnection.HTTP_UNAUTHORIZED);
  }

  @Test
  void shouldAllowClusterStatusWithCredentialsUnknownToTheClusterAdminStore() throws Exception {
    // when — a real DB-backed user's credentials, which the isolated cluster-admin store does not
    // know. Clients migrating here from /v2/status send whatever they are configured with.
    final HttpResponse<String> response =
        send(clusterUri(PATH_CLUSTER_STATUS), basicAuth(DB_USERNAME, DB_PASSWORD));

    // then — the status chain runs no authentication filter, so the header is never inspected
    assertThat(response.statusCode()).isEqualTo(HttpURLConnection.HTTP_OK);
  }

  /**
   * The history backup endpoints sit behind the same chain as the rest of the cluster-admin API,
   * and the chain answers before the storage gate does — so this holds on every secondary storage.
   */
  @ParameterizedTest
  @ValueSource(strings = {PATH_CLUSTER_HISTORY_BACKUPS, PATH_CLUSTER_HISTORY_BACKUP})
  void shouldRejectClusterHistoryBackupEndpointWithoutCredentials(final String path)
      throws Exception {
    // when
    final HttpResponse<String> response = send(clusterUri(path), null);

    // then
    assertThat(response.statusCode()).isEqualTo(HttpURLConnection.HTTP_UNAUTHORIZED);
  }

  /**
   * The runtime backup endpoints need no secondary storage, so unlike the history ones they have no
   * storage gate that could answer before the chain does — the chain is the only thing standing
   * between an unauthenticated caller and a cluster-wide backup.
   */
  @ParameterizedTest
  @ValueSource(
      strings = {
        PATH_CLUSTER_RUNTIME_BACKUPS,
        PATH_CLUSTER_RUNTIME_BACKUP,
        PATH_CLUSTER_RUNTIME_BACKUP_STATE
      })
  void shouldRejectClusterRuntimeBackupEndpointWithoutCredentials(final String path)
      throws Exception {
    // when
    final HttpResponse<String> response = send(clusterUri(path), null);

    // then
    assertThat(response.statusCode()).isEqualTo(HttpURLConnection.HTTP_UNAUTHORIZED);
  }

  private HttpResponse<String> send(final URI uri, final String authorizationHeader)
      throws Exception {
    return send("GET", uri, authorizationHeader);
  }

  private HttpResponse<String> send(
      final String method, final URI uri, final String authorizationHeader) throws Exception {
    final HttpRequest.Builder builder =
        HttpRequest.newBuilder(uri).method(method, HttpRequest.BodyPublishers.noBody());
    if (authorizationHeader != null) {
      builder.header("Authorization", authorizationHeader);
    }
    return httpClient.send(builder.build(), HttpResponse.BodyHandlers.ofString());
  }

  private static String basicAuth(final String user, final String password) {
    return "Basic " + Base64.getEncoder().encodeToString((user + ":" + password).getBytes());
  }

  // The cluster-admin API is cluster-wide, so it is always addressed at the gateway root — never
  // under a /physical-tenants/<id> prefix. In physical-tenant mode the client's REST address
  // carries that prefix, so strip it to reach the root.
  private static URI clusterUri(final String path) {
    final String base =
        camundaClient
            .getConfiguration()
            .getRestAddress()
            .toString()
            .replaceAll("/+$", "")
            .replaceFirst("/physical-tenants/[^/]+$", "");
    return URI.create(base + "/" + path);
  }

  // The regular v2 API is addressed through the client's configured REST address, which is
  // physical-tenant-scoped when the suite runs under a physical tenant.
  private static URI apiUri(final String path) {
    final String base = camundaClient.getConfiguration().getRestAddress().toString();
    final String separator = base.endsWith("/") ? "" : "/";
    return URI.create(base + separator + path);
  }
}
