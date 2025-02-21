/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.it.migration;

import io.camunda.application.commons.migration.PrefixMigrationHelper;
import io.camunda.client.CamundaClient;
import io.camunda.client.CredentialsProvider;
import io.camunda.it.utils.MultiDbConfigurator;
import io.camunda.operate.property.OperateProperties;
import io.camunda.qa.util.cluster.TestSimpleCamundaApplication;
import io.camunda.search.connect.configuration.ConnectConfiguration;
import io.camunda.tasklist.property.TasklistProperties;
import io.camunda.zeebe.test.util.testcontainers.TestSearchContainers;
import java.io.IOException;
import java.net.URI;
import java.time.Duration;
import java.util.Base64;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.Network;
import org.testcontainers.containers.wait.strategy.HttpWaitStrategy;
import org.testcontainers.elasticsearch.ElasticsearchContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

@Testcontainers
public class PrefixMigrationIT {

  private static final int SERVER_PORT = 8080;
  private static final int MANAGEMENT_PORT = 9600;
  private static final int GATEWAY_GRPC_PORT = 26500;
  private static final String ELASTIC_ALIAS = "elasticsearch";
  private static final Network NETWORK = Network.newNetwork();
  private static final String OLD_OPERATE_PREFIX = "operate-dev";
  private static final String OLD_TASKLIST_PREFIX = "tasklist-dev";
  private static final String NEW_PREFIX = "new-prefix";

  @Container
  private final ElasticsearchContainer esContainer =
      TestSearchContainers.createDefeaultElasticsearchContainer()
          .withNetwork(NETWORK)
          .withNetworkAliases(ELASTIC_ALIAS)
          .withStartupTimeout(Duration.ofMinutes(5)); // can be slow in CI

  private GenericContainer<?> createCamundaContainer(
      final String image,
      final String operatePrefix,
      final String tasklistPrefix,
      final String newPrefix) {
    final var esUrl = String.format("http://%s:%d", ELASTIC_ALIAS, 9200);

    final var container =
        new GenericContainer<>(DockerImageName.parse(image))
            .waitingFor(
                new HttpWaitStrategy()
                    .forPort(MANAGEMENT_PORT)
                    .forPath("/actuator/health")
                    .withReadTimeout(Duration.ofSeconds(30)))
            .withNetwork(NETWORK)
            .withNetworkAliases("camunda")
            .withExposedPorts(SERVER_PORT, MANAGEMENT_PORT, GATEWAY_GRPC_PORT)
            .withEnv("CAMUNDA_OPERATE_CSRFPREVENTIONENABLED", "false")
            .withEnv("CAMUNDA_TASKLIST_CSRFPREVENTIONENABLED", "false")
            .withEnv("SPRING_PROFILES_ACTIVE", "broker,operate,tasklist,identity,consolidated-auth")
            .withEnv("CAMUNDA_SECURITY_AUTHENTICATION_METHOD", "BASIC")
            .withEnv("CAMUNDA_DATABASE_URL", esUrl)
            .withEnv("CAMUNDA_OPERATE_ELASTICSEARCH_INDEX_PREFIX", operatePrefix)
            .withEnv("CAMUNDA_TASKLIST_ELASTICSEARCH_INDEX_PREFIX", tasklistPrefix)
            .withEnv(
                "ZEEBE_BROKER_EXPORTERS_ELASTICSEARCH_CLASSNAME",
                "io.camunda.zeebe.exporter.ElasticsearchExporter")
            .withEnv("CAMUNDA_OPERATE_ELASTICSEARCH_URL", esUrl)
            .withEnv("CAMUNDA_OPERATE_ZEEBEELASTICSEARCH_URL", esUrl)
            .withEnv("CAMUNDA_TASKLIST_ELASTICSEARCH_URL", esUrl)
            .withEnv("CAMUNDA_TASKLIST_ZEEBEELASTICSEARCH_URL", esUrl)
            .withEnv("ZEEBE_BROKER_EXPORTERS_ELASTICSEARCH_ARGS_URL", esUrl)
            .withEnv("CAMUNDA_DATABASE_INDEXPREFIX", newPrefix)
            .withEnv("ZEEBE_BROKER_EXPORTERS_CAMUNDAEXPORTER_ARGS_CONNECT_URL", esUrl)
            .withEnv("ZEEBE_BROKER_EXPORTERS_CAMUNDAEXPORTER_ARGS_BULK_SIZE", "1")
            .withEnv(
                "ZEEBE_BROKER_EXPORTERS_CAMUNDAEXPORTER_CLASSNAME",
                "io.camunda.exporter.CamundaExporter")
            .withEnv("ZEEBE_BROKER_EXPORTERS_CAMUNDAEXPORTER_ARGS_INDEX_PREFIX", newPrefix)
            .withEnv("ZEEBE_BROKER_EXPORTERS_CAMUNDAEXPORTER_ARGS_ELASTICSEARCH_URL", esUrl)
            .withEnv("ZEEBE_BROKER_EXPORTERS_CAMUNDAEXPORTER_ARGS_CONNECT_URL", esUrl)
            .withEnv("ZEEBE_BROKER_EXPORTERS_CAMUNDAEXPORTER_ARGS_BULK_SIZE", "1")
            .withEnv(
                "ZEEBE_BROKER_EXPORTERS_CAMUNDAEXPORTER_CLASSNAME",
                "io.camunda.exporter.CamundaExporter")
            .withEnv("ZEEBE_BROKER_EXPORTERS_CAMUNDAEXPORTER_ARGS_INDEX_PREFIX", newPrefix);

    return container;
  }

  private void prefixMigration(final String newPrefix) throws IOException {
    final var operate = new OperateProperties();
    final var tasklist = new TasklistProperties();
    final var connect = new ConnectConfiguration();

    operate.getElasticsearch().setIndexPrefix(OLD_OPERATE_PREFIX);
    tasklist.getElasticsearch().setIndexPrefix(OLD_TASKLIST_PREFIX);
    connect.setUrl("http://localhost:" + esContainer.getMappedPort(9200));
    if (!newPrefix.isBlank()) {
      connect.setIndexPrefix(newPrefix);
    }
    PrefixMigrationHelper.runPrefixMigration(operate, tasklist, connect);
  }

  private CamundaClient createCamundaClient(final GenericContainer<?> container)
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

  @Test
  void shouldReindexDocumentsDuringPrefixMigration() throws IOException, InterruptedException {
    // given
    final var camunda87 =
        createCamundaContainer(
            "camunda/camunda:8.7.0-alpha4", OLD_OPERATE_PREFIX, OLD_TASKLIST_PREFIX, NEW_PREFIX);
    camunda87.start();

    final var camunda87Client = createCamundaClient(camunda87);

    final var event =
        camunda87Client
            .newDeployResourceCommand()
            .addResourceFromClasspath("process/incident_process_v1.bpmn")
            .send()
            .join();

    // Wait for documents to be written to indices
    Thread.sleep(9000);

    camunda87.stop();

    // when
    prefixMigration(NEW_PREFIX);

    // then
    final var currentCamundaClient = startLatestCamunda();

    final var processDefinitions = currentCamundaClient.newProcessDefinitionQuery().send().join();
    Assertions.assertThat(processDefinitions.items().size()).isEqualTo(1);
    Assertions.assertThat(processDefinitions.items().getFirst().getProcessDefinitionKey())
        .isEqualTo(event.getProcesses().getFirst().getProcessDefinitionKey());
  }

  private CamundaClient startLatestCamunda() {
    final TestSimpleCamundaApplication testSimpleCamundaApplication =
        new TestSimpleCamundaApplication();
    final MultiDbConfigurator multiDbConfigurator =
        new MultiDbConfigurator(testSimpleCamundaApplication);
    final var esUrl = String.format("http://localhost:%d", esContainer.getMappedPort(9200));
    multiDbConfigurator.configureElasticsearchSupport(esUrl, NEW_PREFIX);
    testSimpleCamundaApplication.start();
    testSimpleCamundaApplication.awaitCompleteTopology();

    final var currentCamundaClient = testSimpleCamundaApplication.newClientBuilder().build();
    return currentCamundaClient;
  }
}
