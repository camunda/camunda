/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.it.migration;

import static io.camunda.it.migration.util.PrefixMigrationHelper.GATEWAY_GRPC_PORT;
import static io.camunda.it.migration.util.PrefixMigrationHelper.MANAGEMENT_PORT;
import static io.camunda.it.migration.util.PrefixMigrationHelper.NETWORK;
import static io.camunda.it.migration.util.PrefixMigrationHelper.NEW_PREFIX;
import static io.camunda.it.migration.util.PrefixMigrationHelper.OLD_OPERATE_PREFIX;
import static io.camunda.it.migration.util.PrefixMigrationHelper.OLD_TASKLIST_PREFIX;
import static io.camunda.it.migration.util.PrefixMigrationHelper.OPENSEARCH_ALIAS;
import static io.camunda.it.migration.util.PrefixMigrationHelper.SERVER_PORT;
import static io.camunda.it.migration.util.PrefixMigrationHelper.createCamundaClient;

import io.camunda.application.commons.migration.PrefixMigrationHelper;
import io.camunda.client.CamundaClient;
import io.camunda.it.utils.MultiDbConfigurator;
import io.camunda.operate.property.OperateProperties;
import io.camunda.qa.util.cluster.TestSimpleCamundaApplication;
import io.camunda.search.connect.configuration.ConnectConfiguration;
import io.camunda.tasklist.property.TasklistProperties;
import io.camunda.zeebe.test.util.testcontainers.TestSearchContainers;
import java.io.IOException;
import java.time.Duration;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import org.opensearch.testcontainers.OpensearchContainer;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.HttpWaitStrategy;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

@Testcontainers
public class PrefixMigrationOSIT {

  @Container
  private final OpensearchContainer osContainer =
      TestSearchContainers.createDefaultOpensearchContainer()
          .withNetwork(NETWORK)
          .withNetworkAliases(OPENSEARCH_ALIAS);

  private GenericContainer<?> createCamundaContainer() {
    final var osUrl = String.format("http://%s:%d", OPENSEARCH_ALIAS, 9200);

    return new GenericContainer<>(DockerImageName.parse("camunda/camunda:8.7.0-alpha4"))
        .waitingFor(
            new HttpWaitStrategy()
                .forPort(MANAGEMENT_PORT)
                .forPath("/actuator/health")
                .withReadTimeout(Duration.ofSeconds(30)))
        .withNetwork(NETWORK)
        .withNetworkAliases("camunda")
        .withExposedPorts(SERVER_PORT, MANAGEMENT_PORT, GATEWAY_GRPC_PORT)
        .withEnv("CAMUNDA_DATABASE_TYPE", "opensearch")
        .withEnv("CAMUNDA_OPERATE_DATABASE", "opensearch")
        .withEnv("CAMUNDA_TASKLIST_DATABASE", "opensearch")
        .withEnv("CAMUNDA_DATABASE_INDEXPREFIX", NEW_PREFIX)
        .withEnv("CAMUNDA_OPERATE_CSRFPREVENTIONENABLED", "false")
        .withEnv("CAMUNDA_TASKLIST_CSRFPREVENTIONENABLED", "false")
        .withEnv("SPRING_PROFILES_ACTIVE", "broker,operate,tasklist,identity,consolidated-auth")
        .withEnv("CAMUNDA_SECURITY_AUTHENTICATION_METHOD", "BASIC")
        .withEnv("CAMUNDA_DATABASE_URL", osUrl)
        .withEnv("CAMUNDA_OPERATE_OPENSEARCH_INDEX_PREFIX", OLD_OPERATE_PREFIX)
        .withEnv("CAMUNDA_TASKLIST_OPENSEARCH_INDEX_PREFIX", OLD_TASKLIST_PREFIX)
        .withEnv("CAMUNDA_TASKLIST_OPENSEARCH_INDEXPREFIX", OLD_TASKLIST_PREFIX)
        .withEnv(
            "ZEEBE_BROKER_EXPORTERS_OPENSEARCH_CLASSNAME",
            "io.camunda.zeebe.exporter.opensearch.OpensearchExporter")
        .withEnv("ZEEBE_BROKER_EXPORTERS_OPENSEARCH_ARGS_URL", osUrl)
        .withEnv("CAMUNDA_OPERATE_OPENSEARCH_URL", osUrl)
        .withEnv("CAMUNDA_OPERATE_ZEEBEOPENSEARCH_URL", osUrl)
        .withEnv("CAMUNDA_TASKLIST_OPENSEARCH_URL", osUrl)
        .withEnv("CAMUNDA_TASKLIST_ZEEBEOPENSEARCH_URL", osUrl);
  }

  private void prefixMigration() throws IOException {
    final var operate = new OperateProperties();
    final var tasklist = new TasklistProperties();
    final var connect = new ConnectConfiguration();

    operate.getOpensearch().setIndexPrefix(OLD_OPERATE_PREFIX);
    tasklist.getOpenSearch().setIndexPrefix(OLD_TASKLIST_PREFIX);
    connect.setType("opensearch");
    connect.setUrl(osContainer.getHttpHostAddress());
    connect.setIndexPrefix(NEW_PREFIX);
    PrefixMigrationHelper.runPrefixMigration(operate, tasklist, connect);
  }

  @Test
  void shouldReindexDocumentsDuringPrefixMigration() throws IOException, InterruptedException {
    // given
    final var camunda87 = createCamundaContainer();
    camunda87.start();

    final var camunda87Client = createCamundaClient(camunda87);

    final var event =
        camunda87Client
            .newDeployResourceCommand()
            .addResourceFromClasspath("process/incident_process_v1.bpmn")
            .send()
            .join();

    // Wait for documents to be written to indices
    Thread.sleep(10000);

    camunda87.stop();

    // when
    prefixMigration();

    // then
    try (final var currentCamundaClient = startLatestCamunda()) {
      final var processDefinitions = currentCamundaClient.newProcessDefinitionQuery().send().join();
      Assertions.assertThat(processDefinitions.items().size()).isEqualTo(1);
      Assertions.assertThat(processDefinitions.items().getFirst().getProcessDefinitionKey())
          .isEqualTo(event.getProcesses().getFirst().getProcessDefinitionKey());
    }
  }

  private CamundaClient startLatestCamunda() {
    final TestSimpleCamundaApplication testSimpleCamundaApplication =
        new TestSimpleCamundaApplication();
    final MultiDbConfigurator multiDbConfigurator =
        new MultiDbConfigurator(testSimpleCamundaApplication);
    multiDbConfigurator.configureOpenSearchSupport(
        osContainer.getHttpHostAddress(), NEW_PREFIX, "admin", "admin");
    testSimpleCamundaApplication.start();
    testSimpleCamundaApplication.awaitCompleteTopology();

    final var currentCamundaClient = testSimpleCamundaApplication.newClientBuilder().build();
    return currentCamundaClient;
  }
}
