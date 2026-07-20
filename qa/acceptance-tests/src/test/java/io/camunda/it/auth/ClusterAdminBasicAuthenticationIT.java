/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.it.auth;

import static org.assertj.core.api.Assertions.assertThat;

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

/**
 * Full-stack integration tests for the cluster-admin Basic-auth chain ({@code /cluster/v2/**}),
 * exercised against the real {@link
 * io.camunda.zeebe.gateway.rest.controller.DummyClusterTopologyController} endpoint.
 */
@MultiDbTest
@DisabledIfSystemProperty(named = "test.integration.camunda.database.type", matches = "AWS_OS")
public class ClusterAdminBasicAuthenticationIT {

  public static final String PATH_CLUSTER_TOPOLOGY = "cluster/v2/topology";
  public static final String PATH_CLUSTER_STATUS = "cluster/v2/status";
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

    // then
    assertThat(response.statusCode()).isEqualTo(HttpURLConnection.HTTP_OK);
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

  @Test
  void shouldAllowPublicStatusEndpointWithoutCredentials() throws Exception {
    // when — the public cluster status endpoint is hit with no credentials
    final HttpResponse<String> response = send(clusterUri(PATH_CLUSTER_STATUS), null);

    // then — permitAll: reachable unauthenticated, healthy 204 with no body (the cluster
    // equivalent of /v2/status), so no topology/membership detail is exposed to anonymous callers
    assertThat(response.statusCode()).isEqualTo(HttpURLConnection.HTTP_NO_CONTENT);
    assertThat(response.body()).isEmpty();
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

  private HttpResponse<String> send(final URI uri, final String authorizationHeader)
      throws Exception {
    final HttpRequest.Builder builder = HttpRequest.newBuilder().uri(uri);
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
