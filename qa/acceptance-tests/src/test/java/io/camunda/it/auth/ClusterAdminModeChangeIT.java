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
import java.net.http.HttpRequest.BodyPublishers;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodyHandlers;
import java.util.Base64;
import java.util.List;
import org.junit.jupiter.api.AutoClose;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledIfSystemProperty;

@MultiDbTest
@DisabledIfSystemProperty(named = "test.integration.camunda.database.type", matches = "AWS_OS")
class ClusterAdminModeChangeIT {

  private static final String CLUSTER_ADMIN_USER = "cluster-operator";
  private static final String CLUSTER_ADMIN_PASSWORD = "cluster-secret";
  private static final String DB_USERNAME = "db_user";
  private static final String DB_PASSWORD = "db_password";

  @MultiDbTestApplication
  private static final TestStandaloneBroker BROKER =
      new TestStandaloneBroker()
          .withBasicAuth()
          .withAuthenticatedAccess()
          .withProperty("camunda.security.cluster-admin.basic.users[0].name", CLUSTER_ADMIN_USER)
          .withProperty(
              "camunda.security.cluster-admin.basic.users[0].password", CLUSTER_ADMIN_PASSWORD);

  // A real, secondary-storage-backed user — used to prove it cannot reach the cluster-admin chain.
  @UserDefinition
  private static final TestUser DB_USER = new TestUser(DB_USERNAME, DB_PASSWORD, List.of());

  private static CamundaClient camundaClient;
  @AutoClose private final HttpClient httpClient = HttpClient.newHttpClient();

  @Test
  void shouldRejectModeChangeWithoutCredentials() throws Exception {
    // when
    final var response = changeMode("mode=RECOVERING&dryRun=true", null);

    // then
    assertThat(response.statusCode()).isEqualTo(HttpURLConnection.HTTP_UNAUTHORIZED);
  }

  @Test
  void shouldRejectModeChangeWithWrongClusterAdminPassword() throws Exception {
    // when
    final var response =
        changeMode("mode=RECOVERING&dryRun=true", basicAuth(CLUSTER_ADMIN_USER, "wrong-password"));

    // then
    assertThat(response.statusCode()).isEqualTo(HttpURLConnection.HTTP_UNAUTHORIZED);
  }

  @Test
  void shouldRejectModeChangeForSecondaryStorageBackedUser() throws Exception {
    // when — a real DB-backed user presents valid credentials to the cluster-admin API
    final var response =
        changeMode("mode=RECOVERING&dryRun=true", basicAuth(DB_USERNAME, DB_PASSWORD));

    // then — the cluster-admin store is isolated, so a DB user is not known to this chain
    assertThat(response.statusCode()).isEqualTo(HttpURLConnection.HTTP_UNAUTHORIZED);
  }

  @Test
  void shouldPlanModeChangeForEveryPhysicalTenant() throws Exception {
    // when — no physicalTenantId, so every physical tenant of the cluster is transitioned
    final var response =
        changeMode(
            "mode=RECOVERING&dryRun=true", basicAuth(CLUSTER_ADMIN_USER, CLUSTER_ADMIN_PASSWORD));

    // then — one change covering the whole request, with the transition planned per broker
    assertThat(response.statusCode()).isEqualTo(HttpURLConnection.HTTP_OK);
    final var body = new ObjectMapper().readTree(response.body());
    assertThat(body.get("changeId").asText()).isNotBlank();
    assertThat(body.get("plannedChanges")).isNotEmpty();
    assertThat(body.get("plannedChanges"))
        .allSatisfy(change -> assertThat(change.get("mode").asText()).isEqualTo("RECOVERING"));
    assertThat(body.get("plannedChanges"))
        .anySatisfy(
            change -> assertThat(change.get("operation").asText()).isEqualTo("ModeChangeOperation"))
        .anySatisfy(
            change ->
                assertThat(change.get("operation").asText()).isEqualTo("AwaitModeChangeOperation"));
  }

  @Test
  void shouldPlanModeChangeForTheDefaultPhysicalTenant() throws Exception {
    // when — the default tenant is addressable on every cluster
    final var response =
        changeMode(
            "mode=RECOVERING&physicalTenantId=default&dryRun=true",
            basicAuth(CLUSTER_ADMIN_USER, CLUSTER_ADMIN_PASSWORD));

    // then
    assertThat(response.statusCode()).isEqualTo(HttpURLConnection.HTTP_OK);
  }

  @Test
  void shouldRejectModeChangeScopedToAnUnknownPhysicalTenant() throws Exception {
    // when — a cluster with a single partition group shares one mode across all tenants, so it
    // cannot transition another tenant on its own
    final var response =
        changeMode(
            "mode=RECOVERING&physicalTenantId=does-not-exist&dryRun=true",
            basicAuth(CLUSTER_ADMIN_USER, CLUSTER_ADMIN_PASSWORD));

    // then — a bad request, not an internal error
    assertThat(response.statusCode()).isEqualTo(HttpURLConnection.HTTP_BAD_REQUEST);
    assertThat(response.body()).contains("does-not-exist");
  }

  @Test
  void shouldRejectAnUnknownMode() throws Exception {
    // when
    final var response =
        changeMode(
            "mode=NOT_A_MODE&dryRun=true", basicAuth(CLUSTER_ADMIN_USER, CLUSTER_ADMIN_PASSWORD));

    // then
    assertThat(response.statusCode()).isEqualTo(HttpURLConnection.HTTP_BAD_REQUEST);
  }

  private HttpResponse<String> changeMode(final String query, final String authorizationHeader)
      throws Exception {
    final HttpRequest.Builder builder =
        HttpRequest.newBuilder(clusterUri("cluster/v2/mode?" + query));
    if (authorizationHeader != null) {
      builder.header("Authorization", authorizationHeader);
    }
    builder.method("PATCH", BodyPublishers.noBody());
    return httpClient.send(builder.build(), BodyHandlers.ofString());
  }

  private static String basicAuth(final String user, final String password) {
    return "Basic " + Base64.getEncoder().encodeToString((user + ":" + password).getBytes());
  }

  // The cluster-admin API is cluster-wide, so it is always addressed at the gateway root — never
  // under a /physical-tenants/<id> prefix that the client's REST address may carry.
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
}
