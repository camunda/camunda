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
import io.camunda.zeebe.qa.util.cluster.TestStandaloneBroker;
import io.camunda.zeebe.qa.util.junit.ZeebeIntegration;
import io.camunda.zeebe.qa.util.junit.ZeebeIntegration.TestZeebe;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpRequest.BodyPublishers;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodyHandlers;
import java.util.Base64;
import org.junit.jupiter.api.AutoClose;
import org.junit.jupiter.api.Test;

@ZeebeIntegration
final class ClusterAdminModeChangeIT {

  private static final String CLUSTER_ADMIN_USER = "cluster-operator";
  private static final String CLUSTER_ADMIN_PASSWORD = "cluster-secret";

  @TestZeebe(purgeAfterEach = false)
  private static final TestStandaloneBroker BROKER =
      new TestStandaloneBroker()
          .withUnauthenticatedAccess()
          .withProperty("camunda.security.cluster-admin.basic.users[0].name", CLUSTER_ADMIN_USER)
          .withProperty(
              "camunda.security.cluster-admin.basic.users[0].password", CLUSTER_ADMIN_PASSWORD);

  @AutoClose private final HttpClient httpClient = HttpClient.newHttpClient();

  @Test
  void shouldPlanModeChangeForEveryPhysicalTenant() throws Exception {
    // when — no physicalTenantId, so every physical tenant of the cluster is transitioned
    final var response =
        changeMode(
            "mode=RECOVERING&dryRun=true", basicAuth(CLUSTER_ADMIN_USER, CLUSTER_ADMIN_PASSWORD));

    // then — one change covering the whole request, with the transition planned per broker of the
    // only physical tenant this cluster has
    assertThat(response.statusCode()).isEqualTo(HttpURLConnection.HTTP_OK);
    final var body = new ObjectMapper().readTree(response.body());
    assertThat(body.get("changeId").asText()).isNotBlank();
    assertThat(body.get("plannedChanges"))
        .singleElement()
        .satisfies(
            change -> {
              assertThat(change.get("physicalTenantId").asText()).isEqualTo("default");
              assertThat(change.get("operations"))
                  .allSatisfy(
                      operation ->
                          assertThat(operation.get("mode").asText()).isEqualTo("RECOVERING"))
                  .anySatisfy(
                      operation ->
                          assertThat(operation.get("operation").asText())
                              .isEqualTo("ModeChangeOperation"))
                  .anySatisfy(
                      operation ->
                          assertThat(operation.get("operation").asText())
                              .isEqualTo("AwaitModeChangeOperation"));
            });
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
    // when — the tenant has no partition group on this cluster, so it cannot be transitioned
    final var response =
        changeMode(
            "mode=RECOVERING&physicalTenantId=does-not-exist&dryRun=true",
            basicAuth(CLUSTER_ADMIN_USER, CLUSTER_ADMIN_PASSWORD));

    // then — the tenant is reported as missing, not as an internal error
    assertThat(response.statusCode()).isEqualTo(HttpURLConnection.HTTP_NOT_FOUND);
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
    final HttpRequest.Builder builder = HttpRequest.newBuilder(clusterUri(query));
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
  // under a /physical-tenants/<id> prefix.
  private static URI clusterUri(final String query) {
    return BROKER.restAddress().resolve("cluster/v2/mode?" + query);
  }
}
