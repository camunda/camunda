/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.application;

import static io.camunda.webapps.schema.SupportedVersions.SUPPORTED_ELASTICSEARCH_VERSION;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.fail;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;
import org.apache.hc.client5.http.classic.methods.HttpGet;
import org.apache.hc.client5.http.impl.classic.CloseableHttpResponse;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.core5.http.io.entity.EntityUtils;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.Network;
import org.testcontainers.containers.wait.strategy.HttpWaitStrategy;
import org.testcontainers.elasticsearch.ElasticsearchContainer;

@EnabledIfSystemProperty(named = "camunda.docker.test.enabled", matches = "true")
public class CamundaDockerIT {

  private static final int SERVER_PORT = 8080;
  private static final int MANAGEMENT_PORT = 9600;
  private static final int GATEWAY_GRPC_PORT = 26500;
  private static final int ELASTICSEARCH_PORT = 9200;

  private static final String CAMUNDA_NETWORK_ALIAS = "camunda";
  private static final String ELASTICSEARCH_NETWORK_ALIAS = "elasticsearch";

  private static final String CAMUNDA_TEST_DOCKER_IMAGE =
      System.getProperty("camunda.docker.test.image", "camunda/camunda:SNAPSHOT");
  private static final String ELASTICSEARCH_DOCKER_IMAGE =
      System.getProperty(
          "camunda.docker.test.elasticsearch.image",
          "docker.elastic.co/elasticsearch/elasticsearch:" + SUPPORTED_ELASTICSEARCH_VERSION);
  private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

  private final List<GenericContainer> createdContainers = new ArrayList<>();

  @Test
  public void testStartCamundaDocker() throws Exception {
    // given
    // create and start Elasticsearch container
    final ElasticsearchContainer elasticsearchContainer =
        createContainer(this::createElasticsearchContainer);
    elasticsearchContainer.start();
    // create camunda container
    final GenericContainer camundaContainer = createContainer(this::createCamundaContainer);

    // when
    startContainer(camundaContainer);
    // and when performing health check
    try (final CloseableHttpResponse healthCheckResponse =
        HttpClients.createDefault()
            .execute(
                new HttpGet(
                    String.format(
                        "http://%s:%d%s",
                        camundaContainer.getHost(),
                        camundaContainer.getMappedPort(MANAGEMENT_PORT),
                        "/actuator/health")))) {

      // then - convert the response and expected response to intermediate JSON representation
      // this will allow us to compare without worrying about the ordering of the values, and just
      // checking that they are logically equivalent
      assertThat(healthCheckResponse.getCode()).isEqualTo(200);
      final String expectedHealthCheckResponse =
          """
            {
              "status": "UP",
              "components": {
                "brokerReady": {"status": "UP"},
                "brokerStartup": {"status": "UP"},
                "brokerStatus": {"status": "UP"},
                "indicesCheck": {"status": "UP"},
                "livenessState": {"status": "UP"},
                "readinessState": {"status": "UP"},
                "searchEngineCheck": {"status": "UP"}
              },
              "groups": ["liveness", "readiness", "startup", "status"]
            }
            """;
      final var expectedJson = OBJECT_MAPPER.readTree(expectedHealthCheckResponse);
      final var actualJson =
          OBJECT_MAPPER.readTree(EntityUtils.toString(healthCheckResponse.getEntity()));

      assertThat(actualJson).isEqualTo(expectedJson);
    }
  }

  private void startContainer(final GenericContainer container) {
    try {
      container.start();
    } catch (final Exception e) {
      fail(
          String.format(
              "Failed to start container.\n" + "Exception message: %s.\n" + "Container Logs:\n%s",
              e.getMessage(), container.getLogs()));
    }
  }

  private <T extends GenericContainer> T createContainer(final Supplier<T> containerSupplier) {
    final T container = containerSupplier.get();
    createdContainers.add(container);
    return container;
  }

  private ElasticsearchContainer createElasticsearchContainer() {
    return new ElasticsearchContainer(ELASTICSEARCH_DOCKER_IMAGE)
        .withNetwork(Network.SHARED)
        .withNetworkAliases(ELASTICSEARCH_NETWORK_ALIAS)
        .withEnv("xpack.security.enabled", "false")
        .withExposedPorts(ELASTICSEARCH_PORT);
  }

  private GenericContainer createCamundaContainer() {
    return new GenericContainer<>(CAMUNDA_TEST_DOCKER_IMAGE)
        .withExposedPorts(SERVER_PORT, MANAGEMENT_PORT, GATEWAY_GRPC_PORT)
        .withNetwork(Network.SHARED)
        .withNetworkAliases(CAMUNDA_NETWORK_ALIAS)
        .waitingFor(
            new HttpWaitStrategy()
                .forPort(MANAGEMENT_PORT)
                .forPath("/actuator/health")
                .withReadTimeout(Duration.ofSeconds(120)))
        .withStartupTimeout(Duration.ofSeconds(300))
        .withEnv(
            "ZEEBE_BROKER_EXPORTERS_ELASTICSEARCH_CLASSNAME",
            "io.camunda.zeebe.exporter.ElasticsearchExporter")
        .withEnv("ZEEBE_BROKER_EXPORTERS_ELASTICSEARCH_ARGS_URL", elasticsearchUrl())
        .withEnv("ZEEBE_BROKER_EXPORTERS_ELASTICSEARCH_ARGS_BULK_SIZE", "1")
        .withEnv("CAMUNDA_TASKLIST_ELASTICSEARCH_URL", elasticsearchUrl())
        .withEnv("CAMUNDA_TASKLIST_ZEEBEELASTICSEARCH_URL", elasticsearchUrl())
        .withEnv("CAMUNDA_TASKLIST_ZEEBE_GATEWAYADDRESS", gatewayAddress())
        .withEnv("CAMUNDA_TASKLIST_ZEEBE_RESTADDRESS", httpUrl())
        .withEnv("CAMUNDA_OPERATE_ELASTICSEARCH_URL", elasticsearchUrl())
        .withEnv("CAMUNDA_OPERATE_ZEEBEELASTICSEARCH_URL", elasticsearchUrl())
        .withEnv("CAMUNDA_OPERATE_ZEEBE_GATEWAYADDRESS", gatewayAddress())
        .withEnv("CAMUNDA_DATABASE_URL", elasticsearchUrl())
        .withEnv("ZEEBE_BROKER_GATEWAY_ENABLE", "true");
  }

  private static String httpUrl() {
    return String.format("http://%s:%d", CAMUNDA_NETWORK_ALIAS, SERVER_PORT);
  }

  private static String gatewayAddress() {
    return String.format("%s:%d", CAMUNDA_NETWORK_ALIAS, GATEWAY_GRPC_PORT);
  }

  private static String elasticsearchUrl() {
    return String.format("http://%s:%d", ELASTICSEARCH_NETWORK_ALIAS, ELASTICSEARCH_PORT);
  }

  @AfterEach
  public void stopContainers() {
    createdContainers.forEach(GenericContainer::stop);
  }
}
