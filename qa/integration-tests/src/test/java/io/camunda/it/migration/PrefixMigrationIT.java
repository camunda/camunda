/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.it.migration;

import static io.camunda.it.migration.util.PrefixMigrationITUtils.ELASTIC_ALIAS;
import static io.camunda.it.migration.util.PrefixMigrationITUtils.GATEWAY_GRPC_PORT;
import static io.camunda.it.migration.util.PrefixMigrationITUtils.MANAGEMENT_PORT;
import static io.camunda.it.migration.util.PrefixMigrationITUtils.NETWORK;
import static io.camunda.it.migration.util.PrefixMigrationITUtils.NEW_PREFIX;
import static io.camunda.it.migration.util.PrefixMigrationITUtils.OLD_OPERATE_PREFIX;
import static io.camunda.it.migration.util.PrefixMigrationITUtils.OLD_TASKLIST_PREFIX;
import static io.camunda.it.migration.util.PrefixMigrationITUtils.SERVER_PORT;
import static io.camunda.it.migration.util.PrefixMigrationITUtils.createCamundaClient;
import static io.camunda.it.migration.util.PrefixMigrationITUtils.requestProcessInstanceFromV1;
import static io.camunda.it.migration.util.PrefixMigrationITUtils.startLatestCamunda;
import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.application.commons.migration.PrefixMigrationHelper;
import io.camunda.client.api.response.ProcessInstanceEvent;
import io.camunda.operate.property.OperateProperties;
import io.camunda.search.connect.configuration.ConnectConfiguration;
import io.camunda.tasklist.property.TasklistProperties;
import io.camunda.zeebe.test.util.testcontainers.TestSearchContainers;
import java.io.IOException;
import java.net.http.HttpResponse;
import java.time.Duration;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.output.Slf4jLogConsumer;
import org.testcontainers.containers.wait.strategy.HttpWaitStrategy;
import org.testcontainers.elasticsearch.ElasticsearchContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

@Testcontainers
public class PrefixMigrationIT {

  private static final Logger LOGGER = LoggerFactory.getLogger(PrefixMigrationIT.class);

  @Container
  private final ElasticsearchContainer esContainer =
      TestSearchContainers.createDefeaultElasticsearchContainer()
          .withNetwork(NETWORK)
          .withNetworkAliases(ELASTIC_ALIAS)
          .withStartupTimeout(Duration.ofMinutes(5)); // can be slow in CI

  @BeforeEach
  public void setup() {
    esContainer.followOutput(new Slf4jLogConsumer(LOGGER));
  }

  private GenericContainer<?> createCamundaContainer() {
    final var esUrl = String.format("http://%s:%d", ELASTIC_ALIAS, 9200);

    final var container =
        new GenericContainer<>(DockerImageName.parse("camunda/camunda:8.7.0-SNAPSHOT"))
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
            .withEnv("CAMUNDA_OPERATE_ELASTICSEARCH_INDEX_PREFIX", OLD_OPERATE_PREFIX)
            .withEnv("CAMUNDA_TASKLIST_ELASTICSEARCH_INDEX_PREFIX", OLD_TASKLIST_PREFIX)
            .withEnv(
                "ZEEBE_BROKER_EXPORTERS_ELASTICSEARCH_CLASSNAME",
                "io.camunda.zeebe.exporter.ElasticsearchExporter")
            .withEnv("CAMUNDA_OPERATE_ELASTICSEARCH_URL", esUrl)
            .withEnv("CAMUNDA_OPERATE_ZEEBEELASTICSEARCH_URL", esUrl)
            .withEnv("CAMUNDA_TASKLIST_ELASTICSEARCH_URL", esUrl)
            .withEnv("CAMUNDA_TASKLIST_ZEEBEELASTICSEARCH_URL", esUrl)
            .withEnv("ZEEBE_BROKER_EXPORTERS_ELASTICSEARCH_ARGS_URL", esUrl);

    return container;
  }

  private void prefixMigration() throws IOException {
    final var operate = new OperateProperties();
    final var tasklist = new TasklistProperties();
    final var connect = new ConnectConfiguration();

    operate.getElasticsearch().setIndexPrefix(OLD_OPERATE_PREFIX);
    tasklist.getElasticsearch().setIndexPrefix(OLD_TASKLIST_PREFIX);
    connect.setUrl(esContainer.getHttpHostAddress());
    connect.setIndexPrefix(NEW_PREFIX);
    PrefixMigrationHelper.runPrefixMigration(operate, tasklist, connect);
  }

  @Test
  void shouldReindexDocumentsDuringPrefixMigration() throws IOException {
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

    final long processDefinitionKey = event.getProcesses().getFirst().getProcessDefinitionKey();
    final ProcessInstanceEvent processInstanceEvent =
        camunda87Client
            .newCreateInstanceCommand()
            .processDefinitionKey(processDefinitionKey)
            .send()
            .join();

    // Wait for documents to be written to indices
    Awaitility.await("document should be written")
        .atMost(Duration.ofSeconds(30))
        .untilAsserted(
            () -> {
              final long processInstanceKey = processInstanceEvent.getProcessInstanceKey();
              final HttpResponse<String> processInstanceResponse =
                  requestProcessInstanceFromV1(
                      String.format("http://localhost:%d/", camunda87.getMappedPort(SERVER_PORT)),
                      processInstanceKey);

              assertThat(processInstanceResponse.statusCode()).isEqualTo(200);
              assertThat(processInstanceResponse.body())
                  .contains(Long.toString(processInstanceKey));
            });

    camunda87.stop();

    // when
    prefixMigration();

    // then
    final var currentCamundaClient =
        startLatestCamunda(esContainer.getHttpHostAddress(), NEW_PREFIX, true);

    final var processDefinitions = currentCamundaClient.newProcessDefinitionQuery().send().join();
    assertThat(processDefinitions.items().size()).isEqualTo(1);
    assertThat(processDefinitions.items().getFirst().getProcessDefinitionKey())
        .isEqualTo(event.getProcesses().getFirst().getProcessDefinitionKey());
  }
}
