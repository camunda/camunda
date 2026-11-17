/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.it.auth;

import static io.camunda.it.auth.ClusterApiDedicatedPortBasicAuthIT.clusterPortUri;
import static io.camunda.it.auth.ClusterApiDedicatedPortBasicAuthIT.mainBase;
import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.client.CamundaClient;
import io.camunda.qa.util.multidb.MultiDbTest;
import io.camunda.qa.util.multidb.MultiDbTestApplication;
import io.camunda.zeebe.qa.util.cluster.TestStandaloneBroker;
import io.camunda.zeebe.test.util.socket.SocketUtil;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import org.junit.jupiter.api.AutoClose;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledIfSystemProperty;

/**
 * The cluster API on a dedicated port with cluster-admin authentication turned off, while the
 * regular API keeps Basic authentication: access control for the cluster API is reachability of its
 * port.
 */
@MultiDbTest
@DisabledIfSystemProperty(named = "test.integration.camunda.database.type", matches = "AWS_OS")
public class ClusterApiDedicatedPortNoAuthIT {

  private static final int CLUSTER_PORT = SocketUtil.getNextAddress().getPort();

  @MultiDbTestApplication
  private static final TestStandaloneBroker BROKER =
      new TestStandaloneBroker()
          .withBasicAuth()
          .withAuthenticatedAccess()
          .withProperty("camunda.api.cluster.port", CLUSTER_PORT)
          .withProperty("camunda.security.cluster-admin.authentication.method", "none");

  private static CamundaClient camundaClient;
  @AutoClose private final HttpClient httpClient = HttpClient.newHttpClient();

  @Test
  void shouldServeClusterApiWithoutCredentialsOnDedicatedPort() throws Exception {
    // when
    final var response = get(clusterPortUri(camundaClient, CLUSTER_PORT, "cluster/v2/topology"));

    // then
    assertThat(response.statusCode()).isEqualTo(HttpURLConnection.HTTP_OK);
    assertThat(new ObjectMapper().readTree(response.body()).get("brokers")).isNotEmpty();
  }

  @Test
  void shouldNotServeClusterApiOnMainPort() throws Exception {
    // when
    final var response = get(URI.create(mainBase(camundaClient) + "/cluster/v2/topology"));

    // then
    assertThat(response.statusCode()).isEqualTo(HttpURLConnection.HTTP_NOT_FOUND);
  }

  @Test
  void shouldStillRequireCredentialsForRegularApiOnMainPort() throws Exception {
    // when
    final var response = get(URI.create(mainBase(camundaClient) + "/v2/authentication/me"));

    // then — turning off cluster-admin authentication leaves the regular API protected
    assertThat(response.statusCode()).isEqualTo(HttpURLConnection.HTTP_UNAUTHORIZED);
  }

  private HttpResponse<String> get(final URI uri) throws Exception {
    return httpClient.send(
        HttpRequest.newBuilder(uri).GET().build(), HttpResponse.BodyHandlers.ofString());
  }
}
