/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.container;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import io.camunda.container.CamundaContainer.BrokerContainer;
import io.camunda.container.cluster.CamundaPort;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
class BrokerContainerConfigIT {

  private static final String CONFIG_PATH = "/usr/local/camunda/config/application.yml";
  private static final String CUSTOM_CLUSTER_NAME = "config-test-cluster";
  private static final int CUSTOM_PARTITION_COUNT = 3;
  private static final int CUSTOM_REPLICATION_FACTOR = 1;

  @Container
  private final BrokerContainer broker =
      new BrokerContainer(CamundaContainer.getBrokerImageName())
          .withoutTopologyCheck()
          .withUnifiedConfig(
              cfg -> {
                cfg.getCluster().setName(CUSTOM_CLUSTER_NAME);
                cfg.getCluster().setPartitionCount(CUSTOM_PARTITION_COUNT);
                cfg.getCluster().setReplicationFactor(CUSTOM_REPLICATION_FACTOR);
                cfg.getProcessing().setMaxCommandsInBatch(200);
              })
          .withEnv("CAMUNDA_SECURITY_AUTHORIZATIONS_ENABLED", "false")
          .withEnv("CAMUNDA_DATABASE_SCHEMA_MANAGER_CREATE_SCHEMA", "false")
          .withProperty("zeebe.broker.gateway.enable", true);

  @Test
  @SuppressWarnings("unchecked")
  void shouldUploadConfigWithCustomUnifiedConfigValues() throws Exception {
    // given — the container is already started with the custom config by @Container

    // when — pull the config file from the running container
    final var yamlBytes = broker.copyFileFromContainer(CONFIG_PATH, InputStream::readAllBytes);
    final var yaml = new ObjectMapper(new YAMLFactory()).readValue(yamlBytes, Map.class);

    // then — verify the camunda section contains the expected overrides
    final var camunda = (Map<String, Object>) yaml.get("camunda");
    assertThat(camunda).as("top-level 'camunda' key should be present").isNotNull();

    final var cluster = (Map<String, Object>) camunda.get("cluster");
    assertThat(cluster)
        .as("camunda.cluster section should be present")
        .isNotNull()
        .containsEntry("name", CUSTOM_CLUSTER_NAME)
        .containsEntry("partitionCount", CUSTOM_PARTITION_COUNT)
        .containsEntry("replicationFactor", CUSTOM_REPLICATION_FACTOR);

    final var processing = (Map<String, Object>) camunda.get("processing");
    assertThat(processing)
        .as("camunda.processing section should be present")
        .isNotNull()
        .containsEntry("maxCommandsInBatch", 200);

    // verify that additional zeebe properties are present
    final var zeebe = (Map<String, Object>) yaml.get("zeebe");
    assertThat(zeebe).as("zeebe section should be present").isNotNull();
    final var brokerConfig = (Map<String, Object>) zeebe.get("broker");
    final var gatewayConfig = (Map<String, Object>) brokerConfig.get("gateway");
    assertThat(gatewayConfig).containsEntry("enable", true);
  }

  @Test
  void shouldExposeActuatorEndpointsOnMonitoringPort() throws Exception {
    // given
    final int monitoringPort = broker.getMappedPort(CamundaPort.MONITORING.getPort());
    final var monitoringBaseUri = URI.create("http://" + broker.getHost() + ":" + monitoringPort);

    try (final var httpClient = HttpClient.newHttpClient()) {
      // when
      final var discoveryRequest =
          HttpRequest.newBuilder().uri(monitoringBaseUri.resolve("/actuator")).GET().build();
      final var discoveryResponse =
          httpClient.send(discoveryRequest, HttpResponse.BodyHandlers.ofString());

      // then
      assertThat(discoveryResponse.statusCode())
          .as(
              "/actuator discovery page should return HTTP 200 on monitoring port %d",
              monitoringPort)
          .isEqualTo(200);
      assertThat(discoveryResponse.body())
          .as("discovery response should contain links to actuator endpoints")
          .contains("_links");

      // when — request the health endpoint
      final var healthRequest =
          HttpRequest.newBuilder().uri(monitoringBaseUri.resolve("/actuator/health")).GET().build();
      final var healthResponse =
          httpClient.send(healthRequest, HttpResponse.BodyHandlers.ofString());

      // then — the health endpoint should be accessible
      assertThat(healthResponse.statusCode())
          .as("actuator health endpoint should return HTTP 200")
          .isEqualTo(200);
      assertThat(healthResponse.body())
          .as("health response should contain status field")
          .contains("status");
    }
  }
}
