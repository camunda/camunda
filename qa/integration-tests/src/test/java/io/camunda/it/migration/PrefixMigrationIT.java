/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.it.migration;

import static io.camunda.it.migration.util.PrefixMigrationITUtils.GATEWAY_GRPC_PORT;
import static io.camunda.it.migration.util.PrefixMigrationITUtils.MANAGEMENT_PORT;
import static io.camunda.it.migration.util.PrefixMigrationITUtils.OLD_OPERATE_PREFIX;
import static io.camunda.it.migration.util.PrefixMigrationITUtils.OLD_TASKLIST_PREFIX;
import static io.camunda.it.migration.util.PrefixMigrationITUtils.SERVER_PORT;
import static io.camunda.it.migration.util.PrefixMigrationITUtils.createCamundaClient;
import static io.camunda.it.migration.util.PrefixMigrationITUtils.requestProcessInstanceFromV1;
import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.application.commons.search.SearchEngineDatabaseConfiguration.SearchEngineConnectProperties;
import io.camunda.client.api.response.ProcessInstanceEvent;
import io.camunda.operate.property.OperateProperties;
import io.camunda.qa.util.cluster.TestSimpleCamundaApplication;
import io.camunda.qa.util.multidb.CamundaMultiDBExtension;
import io.camunda.qa.util.multidb.CamundaMultiDBExtension.DatabaseType;
import io.camunda.qa.util.multidb.MultiDbTest;
import io.camunda.qa.util.multidb.MultiDbTestApplication;
import io.camunda.search.connect.configuration.ConnectConfiguration;
import io.camunda.tasklist.property.TasklistProperties;
import io.camunda.zeebe.qa.util.cluster.TestPrefixMigrationApp;
import io.camunda.zeebe.test.util.testcontainers.TestSearchContainers;
import java.io.IOException;
import java.net.http.HttpResponse;
import java.time.Duration;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledIfSystemProperty;
import org.testcontainers.Testcontainers;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.HttpWaitStrategy;
import org.testcontainers.utility.DockerImageName;

@MultiDbTest
@DisabledIfSystemProperty(named = "test.integration.camunda.database.type", matches = "AWS_OS")
@DisabledIfSystemProperty(named = "test.integration.camunda.database.type", matches = "rdbms")
public class PrefixMigrationIT {
  private static final String DEFAULT_ES_OS_URL_FOR_MULTI_DB =
      "http://host.testcontainers.internal:9200";

  @MultiDbTestApplication(managedLifecycle = false)
  private static final TestSimpleCamundaApplication STANDALONE_CAMUNDA =
      new TestSimpleCamundaApplication().withBasicAuth().withAuthorizationsEnabled();

  @BeforeAll
  public static void beforeAll() {
    Testcontainers.exposeHostPorts(9200); // elasticsearch
  }

  private DatabaseType currentMultiDbDatabaseType() {
    final String property =
        System.getProperty(CamundaMultiDBExtension.PROP_CAMUNDA_IT_DATABASE_TYPE);
    return property == null ? DatabaseType.LOCAL : DatabaseType.valueOf(property.toUpperCase());
  }

  private GenericContainer<?> createCamundaContainer() {
    final var container =
        new GenericContainer<>(DockerImageName.parse("camunda/camunda:8.7.0-SNAPSHOT"))
            .waitingFor(
                new HttpWaitStrategy()
                    .forPort(MANAGEMENT_PORT)
                    .forPath("/actuator/health")
                    .withReadTimeout(Duration.ofSeconds(30)))
            .withAccessToHost(true)
            .withExposedPorts(SERVER_PORT, MANAGEMENT_PORT, GATEWAY_GRPC_PORT)
            .withEnv("CAMUNDA_OPERATE_CSRFPREVENTIONENABLED", "false")
            .withEnv("CAMUNDA_TASKLIST_CSRFPREVENTIONENABLED", "false")
            .withEnv("SPRING_PROFILES_ACTIVE", "broker,operate,tasklist,identity,consolidated-auth")
            .withEnv("CAMUNDA_SECURITY_AUTHENTICATION_METHOD", "BASIC");

    if (currentMultiDbDatabaseType() == DatabaseType.ES) {
      addElasticsearchConnectionDetails(container);
    } else if (currentMultiDbDatabaseType() == DatabaseType.OS) {
      addOpensearchConnectionDetails(container);
    }

    return container;
  }

  private void addOpensearchConnectionDetails(final GenericContainer<?> container) {
    container
        .withEnv("CAMUNDA_DATABASE_TYPE", "opensearch")
        .withEnv("CAMUNDA_OPERATE_DATABASE", "opensearch")
        .withEnv("CAMUNDA_TASKLIST_DATABASE", "opensearch")
        .withEnv("CAMUNDA_DATABASE_URL", DEFAULT_ES_OS_URL_FOR_MULTI_DB)
        .withEnv("CAMUNDA_OPERATE_OPENSEARCH_INDEX_PREFIX", OLD_OPERATE_PREFIX)
        .withEnv("CAMUNDA_TASKLIST_OPENSEARCH_INDEX_PREFIX", OLD_TASKLIST_PREFIX)
        .withEnv("CAMUNDA_TASKLIST_OPENSEARCH_INDEXPREFIX", OLD_TASKLIST_PREFIX)
        .withEnv(
            "ZEEBE_BROKER_EXPORTERS_OPENSEARCH_CLASSNAME",
            "io.camunda.zeebe.exporter.opensearch.OpensearchExporter")
        .withEnv("CAMUNDA_OPERATE_OPENSEARCH_URL", DEFAULT_ES_OS_URL_FOR_MULTI_DB)
        .withEnv("CAMUNDA_OPERATE_ZEEBEOPENSEARCH_URL", DEFAULT_ES_OS_URL_FOR_MULTI_DB)
        .withEnv("CAMUNDA_TASKLIST_OPENSEARCH_URL", DEFAULT_ES_OS_URL_FOR_MULTI_DB)
        .withEnv("CAMUNDA_TASKLIST_ZEEBEOPENSEARCH_URL", DEFAULT_ES_OS_URL_FOR_MULTI_DB)
        .withEnv("ZEEBE_BROKER_EXPORTERS_OPENSEARCH_ARGS_URL", DEFAULT_ES_OS_URL_FOR_MULTI_DB);
  }

  private void addElasticsearchConnectionDetails(final GenericContainer<?> container) {
    container
        .withEnv("CAMUNDA_OPERATE_ELASTICSEARCH_INDEX_PREFIX", OLD_OPERATE_PREFIX)
        .withEnv("CAMUNDA_TASKLIST_ELASTICSEARCH_INDEX_PREFIX", OLD_TASKLIST_PREFIX)
        .withEnv(
            "ZEEBE_BROKER_EXPORTERS_ELASTICSEARCH_CLASSNAME",
            "io.camunda.zeebe.exporter.ElasticsearchExporter")
        .withEnv("CAMUNDA_DATABASE_URL", DEFAULT_ES_OS_URL_FOR_MULTI_DB)
        .withEnv("CAMUNDA_OPERATE_ELASTICSEARCH_URL", DEFAULT_ES_OS_URL_FOR_MULTI_DB)
        .withEnv("CAMUNDA_OPERATE_ZEEBEELASTICSEARCH_URL", DEFAULT_ES_OS_URL_FOR_MULTI_DB)
        .withEnv("CAMUNDA_TASKLIST_ELASTICSEARCH_URL", DEFAULT_ES_OS_URL_FOR_MULTI_DB)
        .withEnv("CAMUNDA_TASKLIST_ZEEBEELASTICSEARCH_URL", DEFAULT_ES_OS_URL_FOR_MULTI_DB)
        .withEnv("ZEEBE_BROKER_EXPORTERS_ELASTICSEARCH_ARGS_URL", DEFAULT_ES_OS_URL_FOR_MULTI_DB);
  }

  private void prefixMigration() throws IOException {
    final var operate = new OperateProperties();
    final var tasklist = new TasklistProperties();
    final var connect = new SearchEngineConnectProperties();

    operate.getElasticsearch().setIndexPrefix(OLD_OPERATE_PREFIX);
    operate.getOpensearch().setIndexPrefix(OLD_OPERATE_PREFIX);

    tasklist.getElasticsearch().setIndexPrefix(OLD_TASKLIST_PREFIX);
    tasklist.getOpenSearch().setIndexPrefix(OLD_TASKLIST_PREFIX);

    connect.setIndexPrefix("prefixmigrationit");
    if (currentMultiDbDatabaseType() == DatabaseType.ES) {
      connect.setType("elasticsearch");
    } else if (currentMultiDbDatabaseType() == DatabaseType.OS) {
      connect.setType("opensearch");
    }

    try (final var app = new TestPrefixMigrationApp(connect, tasklist, operate)) {
      app.start();
    }
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
    STANDALONE_CAMUNDA.start();
    STANDALONE_CAMUNDA.awaitCompleteTopology();
    try (final var currentCamundaClient = STANDALONE_CAMUNDA.newClientBuilder().build()) {
      final var processDefinitions =
          currentCamundaClient.newProcessDefinitionSearchRequest().send().join();
      assertThat(processDefinitions.items().size()).isEqualTo(1);
      assertThat(processDefinitions.items().getFirst().getProcessDefinitionKey())
          .isEqualTo(event.getProcesses().getFirst().getProcessDefinitionKey());
    }
  }
}
