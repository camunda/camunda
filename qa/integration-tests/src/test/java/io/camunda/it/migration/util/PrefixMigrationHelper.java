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
import java.io.IOException;
import java.net.URI;
import java.util.Base64;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.Network;

public class PrefixMigrationHelper {
  public static final int SERVER_PORT = 8080;
  public static final int MANAGEMENT_PORT = 9600;
  public static final int GATEWAY_GRPC_PORT = 26500;
  public static final String ELASTIC_ALIAS = "elasticsearch";
  public static final String OPENSEARCH_ALIAS = "opensearch";
  public static final Network NETWORK = Network.newNetwork();
  public static final String OLD_OPERATE_PREFIX = "operate-dev";
  public static final String OLD_TASKLIST_PREFIX = "tasklist-dev";
  public static final String NEW_PREFIX = "new-prefix";

  private PrefixMigrationHelper() {}

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
}
