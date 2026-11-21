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
import io.camunda.zeebe.test.util.socket.SocketUtil;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.Socket;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import org.junit.jupiter.api.AutoClose;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledIfSystemProperty;

/**
 * The cluster API ({@code /cluster/v2/**}) moved to a dedicated port with Basic cluster-admin
 * authentication: the dedicated port serves only the cluster API, the main port no longer serves it
 * (except the public status endpoint), and the port split cannot be bypassed by spoofing the {@code
 * Host} header.
 */
@MultiDbTest
@DisabledIfSystemProperty(named = "test.integration.camunda.database.type", matches = "AWS_OS")
public class ClusterApiDedicatedPortBasicAuthIT {

  private static final int CLUSTER_PORT = SocketUtil.getNextAddress().getPort();

  private static final String CLUSTER_ADMIN_USER = "cluster-operator";
  private static final String CLUSTER_ADMIN_PASSWORD = "cluster-secret";
  private static final String DB_USERNAME = "dedicated-port-db-user";
  private static final String DB_PASSWORD = "dedicated-port-db-password";

  @MultiDbTestApplication
  private static final TestStandaloneBroker BROKER =
      new TestStandaloneBroker()
          .withBasicAuth()
          .withAuthenticatedAccess()
          .withProperty("camunda.api.cluster.port", CLUSTER_PORT)
          .withProperty("camunda.security.cluster-admin.basic.users[0].name", CLUSTER_ADMIN_USER)
          .withProperty(
              "camunda.security.cluster-admin.basic.users[0].password", CLUSTER_ADMIN_PASSWORD);

  @UserDefinition
  private static final TestUser DB_USER = new TestUser(DB_USERNAME, DB_PASSWORD, List.of());

  private static CamundaClient camundaClient;
  @AutoClose private final HttpClient httpClient = HttpClient.newHttpClient();

  @Test
  void shouldServeClusterApiOnDedicatedPort() throws Exception {
    // when
    final var response =
        get(
            clusterPortUri("cluster/v2/topology"),
            basicAuth(CLUSTER_ADMIN_USER, CLUSTER_ADMIN_PASSWORD));

    // then
    assertThat(response.statusCode()).isEqualTo(HttpURLConnection.HTTP_OK);
    assertThat(new ObjectMapper().readTree(response.body()).get("brokers")).isNotEmpty();
  }

  @Test
  void shouldRequireClusterAdminCredentialsOnDedicatedPort() throws Exception {
    // when
    final var anonymous = get(clusterPortUri("cluster/v2/topology"), null);
    final var dbUser =
        get(clusterPortUri("cluster/v2/topology"), basicAuth(DB_USERNAME, DB_PASSWORD));

    // then
    assertThat(anonymous.statusCode()).isEqualTo(HttpURLConnection.HTTP_UNAUTHORIZED);
    assertThat(dbUser.statusCode()).isEqualTo(HttpURLConnection.HTTP_UNAUTHORIZED);
  }

  @Test
  void shouldNotServeClusterApiOnMainPort() throws Exception {
    // when — valid cluster-admin credentials, but on the main port
    final var response =
        get(
            mainPortUri("cluster/v2/topology"),
            basicAuth(CLUSTER_ADMIN_USER, CLUSTER_ADMIN_PASSWORD));

    // then
    assertThat(response.statusCode()).isEqualTo(HttpURLConnection.HTTP_NOT_FOUND);
  }

  @Test
  void shouldKeepPublicClusterStatusOnMainPort() throws Exception {
    // when
    final var mainPort = get(mainPortUri("cluster/v2/status"), null);
    final var clusterPort = get(clusterPortUri("cluster/v2/status"), null);

    // then — monitoring that probes the main port keeps working
    assertThat(mainPort.statusCode()).isEqualTo(HttpURLConnection.HTTP_OK);
    assertThat(clusterPort.statusCode()).isEqualTo(HttpURLConnection.HTTP_OK);
  }

  @Test
  void shouldNotServeRegularApiOnDedicatedPort() throws Exception {
    // when — a DB user's valid credentials for the regular API, sent to the cluster port
    final var response =
        get(clusterPortUri("v2/authentication/me"), basicAuth(DB_USERNAME, DB_PASSWORD));

    // then
    assertThat(response.statusCode()).isEqualTo(HttpURLConnection.HTTP_NOT_FOUND);
  }

  @Test
  void shouldServeRegularApiOnMainPort() throws Exception {
    // when
    final var response =
        get(mainPortUri("v2/authentication/me"), basicAuth(DB_USERNAME, DB_PASSWORD));

    // then
    assertThat(response.statusCode()).isEqualTo(HttpURLConnection.HTTP_OK);
  }

  @Test
  void shouldNotServeClusterApiOnMainPortWithSpoofedHostHeader() throws Exception {
    // given — a request on the main port claiming, via Host, to target the cluster port
    final URI main = mainPortUri("cluster/v2/topology");

    // when
    final int status =
        rawGetStatus(
            main.getHost(),
            main.getPort(),
            main.getRawPath(),
            main.getHost() + ":" + CLUSTER_PORT,
            basicAuth(CLUSTER_ADMIN_USER, CLUSTER_ADMIN_PASSWORD));

    // then — the split follows the socket the request arrived on, not the Host header
    assertThat(status).isEqualTo(HttpURLConnection.HTTP_NOT_FOUND);
  }

  @Test
  void shouldRejectDotSegmentPathsBeforeTheyCanCrossPorts() throws Exception {
    // given — raw paths that Tomcat would resolve into the other port's API
    final URI main = mainPortUri("");
    final String credentials = basicAuth(CLUSTER_ADMIN_USER, CLUSTER_ADMIN_PASSWORD);

    // when
    final int onMainPort =
        rawGetStatus(
            main.getHost(),
            main.getPort(),
            "/v2/../cluster/v2/topology",
            main.getHost() + ":" + main.getPort(),
            credentials);
    final int onClusterPort =
        rawGetStatus(
            main.getHost(),
            CLUSTER_PORT,
            "/cluster/v2/../../v2/authentication/me",
            main.getHost() + ":" + CLUSTER_PORT,
            basicAuth(DB_USERNAME, DB_PASSWORD));

    // then
    assertThat(onMainPort).isEqualTo(HttpURLConnection.HTTP_BAD_REQUEST);
    assertThat(onClusterPort).isEqualTo(HttpURLConnection.HTTP_BAD_REQUEST);
  }

  private HttpResponse<String> get(final URI uri, final String authorization) throws Exception {
    final HttpRequest.Builder builder = HttpRequest.newBuilder(uri).GET();
    if (authorization != null) {
      builder.header("Authorization", authorization);
    }
    return httpClient.send(builder.build(), HttpResponse.BodyHandlers.ofString());
  }

  // java.net.http forbids overriding Host, so the spoofing request is written by hand.
  static int rawGetStatus(
      final String host,
      final int port,
      final String path,
      final String hostHeader,
      final String authorization)
      throws Exception {
    try (final Socket socket = new Socket(host, port)) {
      final OutputStream out = socket.getOutputStream();
      out.write(
          ("GET %s HTTP/1.1\r\nHost: %s\r\nAuthorization: %s\r\nConnection: close\r\n\r\n")
              .formatted(path, hostHeader, authorization)
              .getBytes(StandardCharsets.US_ASCII));
      out.flush();
      final var reader =
          new BufferedReader(
              new InputStreamReader(socket.getInputStream(), StandardCharsets.US_ASCII));
      final String statusLine = reader.readLine();
      return Integer.parseInt(statusLine.split(" ")[1]);
    }
  }

  static String basicAuth(final String user, final String password) {
    return "Basic " + Base64.getEncoder().encodeToString((user + ":" + password).getBytes());
  }

  private static URI mainPortUri(final String path) {
    return URI.create(mainBase(camundaClient) + "/" + path);
  }

  private static URI clusterPortUri(final String path) {
    return clusterPortUri(camundaClient, CLUSTER_PORT, path);
  }

  // The REST address may carry a /physical-tenants/<id> prefix; the cluster API lives at the root.
  static String mainBase(final CamundaClient client) {
    return client
        .getConfiguration()
        .getRestAddress()
        .toString()
        .replaceAll("/+$", "")
        .replaceFirst("/physical-tenants/[^/]+$", "");
  }

  static URI clusterPortUri(final CamundaClient client, final int clusterPort, final String path) {
    final URI main = URI.create(mainBase(client));
    return URI.create(
        "%s://%s:%d/%s".formatted(main.getScheme(), main.getHost(), clusterPort, path));
  }
}
