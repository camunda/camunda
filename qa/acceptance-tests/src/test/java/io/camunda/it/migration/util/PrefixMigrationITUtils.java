/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.it.migration.util;

import io.camunda.client.CamundaClient;
import io.camunda.client.CredentialsProvider;
import io.camunda.qa.util.cluster.TestCamundaApplication;
import io.camunda.qa.util.multidb.MultiDbConfigurator;
import java.io.IOException;
import java.net.CookieManager;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Base64;
import java.util.Optional;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.Network;

public final class PrefixMigrationITUtils {
  public static final int SERVER_PORT = 8080;
  public static final int MANAGEMENT_PORT = 9600;
  public static final int GATEWAY_GRPC_PORT = 26500;
  public static final String ELASTIC_ALIAS = "elasticsearch";
  public static final String OPENSEARCH_ALIAS = "opensearch";
  public static final Network NETWORK = Network.newNetwork();
  public static final String OLD_OPERATE_PREFIX = "operate-dev";
  public static final String OLD_TASKLIST_PREFIX = "tasklist-dev";
  public static final String NEW_PREFIX = "new-prefix";

  private PrefixMigrationITUtils() {}

  public static CamundaClient createCamundaClient(final GenericContainer<?> container)
      throws IOException {

    return CamundaClient.newClientBuilder()
        .grpcAddress(URI.create("http://localhost:" + container.getMappedPort(GATEWAY_GRPC_PORT)))
        .restAddress(URI.create("http://localhost:" + container.getMappedPort(SERVER_PORT)))
        .usePlaintext()
        .credentialsProvider(
            new CredentialsProvider() {
              @Override
              public void applyCredentials(final CredentialsApplier applier) {
                applier.put(
                    "Authorization",
                    "Basic %s"
                        .formatted(Base64.getEncoder().encodeToString("demo:demo".getBytes())));
              }

              @Override
              public boolean shouldRetryRequest(final StatusCode statusCode) {
                return false;
              }
            })
        .build();
  }

  public static CamundaClient startLatestCamunda(
      final String hostAddress, final String indexPrefix, final boolean isElasticsearch) {

    final TestCamundaApplication testCamundaApplication = new TestCamundaApplication();
    final MultiDbConfigurator multiDbConfigurator = new MultiDbConfigurator(testCamundaApplication);

    if (isElasticsearch) {
      multiDbConfigurator.configureElasticsearchSupport("http://" + hostAddress, indexPrefix);
    } else {
      multiDbConfigurator.configureOpenSearchSupport(hostAddress, indexPrefix, "admin", "admin");
    }

    testCamundaApplication.start();
    testCamundaApplication.awaitCompleteTopology();

    return testCamundaApplication.newClientBuilder().build();
  }

  public static HttpResponse<String> requestProcessInstanceFromV1(
      final String endpoint, final long processInstanceKey) {

    try (final HttpClient httpClient =
        HttpClient.newBuilder().cookieHandler(new CookieManager()).build(); ) {
      sendPOSTRequest(
          httpClient,
          String.format("%sapi/login?username=%s&password=%s", endpoint, "demo", "demo"),
          null);

      return sendPOSTRequest(
          httpClient,
          String.format("%sv1/process-instances/search", endpoint),
          String.format(
              "{\"filter\":{\"key\":%d},\"sort\":[{\"field\":\"endDate\",\"order\":\"ASC\"}],\"size\":20}",
              processInstanceKey));
    }
  }

  private static HttpResponse<String> sendPOSTRequest(
      final HttpClient httpClient, final String path, final String body) {
    try {
      final var requestBody = Optional.ofNullable(body).orElse("{}");
      final var requestBuilder =
          HttpRequest.newBuilder()
              .uri(new URI(path))
              .header("content-type", "application/json")
              .method("POST", HttpRequest.BodyPublishers.ofString(requestBody));

      final var request = requestBuilder.build();

      return httpClient.send(request, HttpResponse.BodyHandlers.ofString());
    } catch (final Exception e) {
      throw new RuntimeException("Failed to send request", e);
    }
  }
}
