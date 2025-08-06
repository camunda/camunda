/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.it.schema;

import static io.camunda.application.commons.search.SearchEngineDatabaseConfiguration.SearchEngineSchemaManagerProperties.CREATE_SCHEMA_PROPERTY;
import static io.camunda.webapps.schema.SupportedVersions.SUPPORTED_ELASTICSEARCH_VERSION;
import static org.assertj.core.api.Assertions.assertThat;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import io.camunda.application.Profile;
import io.camunda.exporter.CamundaExporter;
import io.camunda.operate.webapp.api.v1.entities.ProcessInstance;
import io.camunda.qa.util.cluster.TestCamundaApplication;
import io.camunda.qa.util.cluster.TestStandaloneSchemaManager;
import io.camunda.search.connect.configuration.ConnectConfiguration;
import io.camunda.search.connect.es.ElasticsearchConnector;
import io.camunda.zeebe.exporter.ElasticsearchExporter;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.qa.util.junit.ZeebeIntegration;
import io.camunda.zeebe.qa.util.junit.ZeebeIntegration.TestZeebe;
import java.io.IOException;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.AutoClose;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.BindMode;
import org.testcontainers.elasticsearch.ElasticsearchContainer;
import org.testcontainers.images.builder.Transferable;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

@ZeebeIntegration
@Testcontainers
final class StandaloneSchemaManagerIT {

  public static final String ADMIN_USER = "camunda-admin";
  public static final String ADMIN_PASSWORD = "admin123";
  public static final String ADMIN_ROLE = "superuser";

  public static final String APP_USER = "camunda-app";
  public static final String APP_PASSWORD = "app123";
  public static final String APP_ROLE = "camunda_app_role";
  public static final String APP_ROLE_DEFINITION =
      // language=yaml
      """
          camunda_app_role:
            indices:
              - names: ['zeebe-*', 'operate-*', 'tasklist-*', 'camunda-*']
                privileges: ['manage', 'read', 'write']
          """;

  @TestZeebe(autoStart = false)
  final TestStandaloneSchemaManager schemaManager =
      new TestStandaloneSchemaManager()
          .withProperty(
              "zeebe.broker.exporters.elasticsearch.className",
              ElasticsearchExporter.class.getName())
          .withProperty("zeebe.broker.exporters.elasticsearch.args.index.createTemplate", "true")
          .withProperty("zeebe.broker.exporters.elasticsearch.args.retention.enabled", "true")
          .withProperty(
              "zeebe.broker.exporters.elasticsearch.args.authentication.username", ADMIN_USER)
          .withProperty(
              "zeebe.broker.exporters.elasticsearch.args.authentication.password", ADMIN_PASSWORD)
          .withProperty("camunda.database.username", ADMIN_USER)
          .withProperty("camunda.database.password", ADMIN_PASSWORD)
          .withProperty("camunda.database.retention.enabled", "true");

  @TestZeebe(autoStart = false)
  final TestCamundaApplication camunda =
      new TestCamundaApplication()
          .withAdditionalProfile(Profile.CONSOLIDATED_AUTH)
          .withUnauthenticatedAccess()
          .withProperty("camunda.database.username", APP_USER)
          .withProperty("camunda.database.password", APP_PASSWORD)
          .withProperty(CREATE_SCHEMA_PROPERTY, "false")
          .withProperty("camunda.operate.elasticsearch.healthCheckEnabled", "false")
          .withProperty("camunda.tasklist.elasticsearch.healthCheckEnabled", "false")
          .withProperty(
              "zeebe.broker.exporters.elasticsearch.className",
              ElasticsearchExporter.class.getName())
          .withProperty("camunda.operate.elasticsearch.username", APP_USER)
          .withProperty("camunda.operate.elasticsearch.password", APP_PASSWORD)
          .withProperty("camunda.operate.zeebeelasticsearch.username", APP_USER)
          .withProperty("camunda.operate.zeebeelasticsearch.password", APP_PASSWORD)
          .withProperty("camunda.operate.elasticsearch.healthCheckEnabled", "false")
          .withProperty("camunda.tasklist.elasticsearch.username", APP_USER)
          .withProperty("camunda.tasklist.elasticsearch.password", APP_PASSWORD)
          .withProperty("camunda.tasklist.zeebeelasticsearch.username", APP_USER)
          .withProperty("camunda.tasklist.zeebeelasticsearch.password", APP_PASSWORD)
          .withProperty("camunda.tasklist.elasticsearch.healthCheckEnabled", "false")
          .withExporter(
              CamundaExporter.class.getSimpleName(),
              cfg -> {
                cfg.setClassName(CamundaExporter.class.getName());
                cfg.setArgs(
                    Map.of(
                        "connect",
                        Map.of("username", APP_USER, "password", APP_PASSWORD),
                        "createSchema",
                        false,
                        "history",
                        Map.of("retention", Map.of("enabled", true))));
              })
          .withExporter(
              ElasticsearchExporter.class.getSimpleName(),
              cfg -> {
                cfg.setClassName(ElasticsearchExporter.class.getName());
                cfg.setArgs(
                    Map.of(
                        "index",
                        Map.of("createTemplate", false),
                        "retention",
                        Map.of("enabled", false, "managePolicy", false),
                        "authentication",
                        Map.of("username", APP_USER, "password", APP_PASSWORD)));
              });

  @Container
  private final ElasticsearchContainer es =
      new ElasticsearchContainer(
              DockerImageName.parse("docker.elastic.co/elasticsearch/elasticsearch")
                  .withTag(SUPPORTED_ELASTICSEARCH_VERSION))
          // Enable security features
          .withEnv("xpack.security.enabled", "true")
          // Ensure a fast and reliable startup
          .withStartupTimeout(Duration.ofMinutes(5))
          .withStartupAttempts(3)
          .withEnv("xpack.security.enabled", "true")
          .withEnv("xpack.watcher.enabled", "false")
          .withEnv("xpack.ml.enabled", "false")
          .withCopyToContainer(
              Transferable.of(APP_ROLE_DEFINITION), "/usr/share/elasticsearch/config/roles.yml")
          .withClasspathResourceMapping(
              "elasticsearch-fast-startup.options",
              "/usr/share/elasticsearch/config/jvm.options.d/elasticsearch-fast-startup.options",
              BindMode.READ_ONLY);

  @AutoClose private ElasticsearchClient esAdminClient;

  @BeforeEach
  void setup() throws IOException, InterruptedException {
    // setup ES admin user
    es.execInContainer("elasticsearch-users", "useradd", ADMIN_USER, "-p", ADMIN_PASSWORD);
    es.execInContainer("elasticsearch-users", "roles", ADMIN_USER, "-a", ADMIN_ROLE);

    // Create ES client for admin user
    final var config = new ConnectConfiguration();
    config.setUrl("http://" + es.getHttpHostAddress());
    config.setUsername(ADMIN_USER);
    config.setPassword(ADMIN_PASSWORD);
    esAdminClient = new ElasticsearchConnector(config).createClient();

    // create app user with APP_ROLE role
    Awaitility.await()
        .atMost(Duration.ofSeconds(10))
        .pollInterval(Duration.ofSeconds(1))
        .ignoreExceptions()
        .until(
            () ->
                esAdminClient
                    .security()
                    .putUser(r -> r.username(APP_USER).password(APP_PASSWORD).roles(APP_ROLE))
                    .created());

    // Connect to ES in Standalone Schema Manager
    schemaManager
        .withProperty("camunda.database.url", "http://" + es.getHttpHostAddress())
        .withProperty(
            "zeebe.broker.exporters.elasticsearch.args.url", "http://" + es.getHttpHostAddress());
    // Connect to ES in Camunda
    camunda
        .withProperty("camunda.database.url", "http://" + es.getHttpHostAddress())
        .withProperty("camunda.operate.elasticsearch.url", "http://" + es.getHttpHostAddress())
        .withProperty("camunda.operate.zeebeelasticsearch.url", "http://" + es.getHttpHostAddress())
        .withProperty("camunda.tasklist.elasticsearch.url", "http://" + es.getHttpHostAddress())
        .withProperty(
            "camunda.tasklist.zeebeelasticsearch.url", "http://" + es.getHttpHostAddress())
        .updateExporterArgs(
            CamundaExporter.class.getSimpleName(),
            args -> ((Map) args.get("connect")).put("url", "http://" + es.getHttpHostAddress()))
        .updateExporterArgs(
            ElasticsearchExporter.class.getSimpleName(),
            args -> args.put("url", "http://" + es.getHttpHostAddress()));
  }

  @Test
  void canUseCamunda() {
    // given
    schemaManager.start();
    camunda.start();

    // when -- creating a process instance with user task
    final long processInstanceKey;
    try (final var camundaClient = camunda.newClientBuilder().build()) {
      // Deploy process with user task
      camundaClient
          .newDeployResourceCommand()
          .addProcessModel(
              Bpmn.createExecutableProcess("process-with-user-task")
                  .startEvent()
                  .userTask("user-task")
                  .zeebeUserTask()
                  .endEvent()
                  .done(),
              "process-with-user-task.bpmn")
          .send()
          .join();
      processInstanceKey =
          camundaClient
              .newCreateInstanceCommand()
              .bpmnProcessId("process-with-user-task")
              .latestVersion()
              .send()
              .join()
              .getProcessInstanceKey();

      // then -- user task exists and can be completed
      final var userTaskKey =
          Awaitility.await("should create a user task")
              .atMost(Duration.ofSeconds(60))
              .ignoreExceptions()
              .until(
                  () ->
                      camundaClient
                          .newUserTaskSearchRequest()
                          .send()
                          .join()
                          .items()
                          .getFirst()
                          .getUserTaskKey(),
                  Objects::nonNull);
      camundaClient.newUserTaskAssignCommand(userTaskKey).assignee("demo").send().join();
      camundaClient.newUserTaskCompleteCommand(userTaskKey).send().join();

      // then -- process instance is completed
      Awaitility.await("process instance should be completed")
          .atMost(Duration.ofSeconds(60))
          .ignoreExceptions()
          .untilAsserted(
              () -> {
                final var result =
                    camundaClient.newProcessInstanceGetRequest(processInstanceKey).send().join();
                assertThat(result.getEndDate()).isNotNull();
              });
    }

    Awaitility.await("Zeebe records templates exist and records are exported to Elasticsearch")
        .atMost(Duration.ofSeconds(5))
        .ignoreExceptions()
        .untilAsserted(
            () -> {
              assertThat(
                      esAdminClient
                          .indices()
                          .getIndexTemplate(r -> r.name("zeebe-record*"))
                          .indexTemplates())
                  .hasSizeGreaterThan(0);
              assertThat(esAdminClient.count(r -> r.index("zeebe-record*")).count())
                  .isGreaterThan(0);
            });
  }

  @Test
  void canArchiveProcessInstances() {
    // given
    schemaManager.start();
    // Configure archiver to run frequently
    camunda.updateExporterArgs(
        CamundaExporter.class.getSimpleName(),
        args ->
            ((Map) args.computeIfAbsent("history", k -> new HashMap<>()))
                .put("waitPeriodBeforeArchiving", "1s"));
    camunda.start();

    // when - a self-completing process instance is created
    final long processInstanceKey;
    try (final var camundaClient = camunda.newClientBuilder().build()) {
      camundaClient
          .newDeployResourceCommand()
          .addProcessModel(
              Bpmn.createExecutableProcess("simple-process").startEvent().endEvent().done(),
              "simple-process.bpmn")
          .send()
          .join();

      processInstanceKey =
          camundaClient
              .newCreateInstanceCommand()
              .bpmnProcessId("simple-process")
              .latestVersion()
              .send()
              .join()
              .getProcessInstanceKey();
    }

    // then - process instance is archived
    Awaitility.await("process instance should be archived")
        .atMost(Duration.ofSeconds(60))
        .pollInterval(Duration.ofSeconds(1))
        .ignoreExceptions()
        .untilAsserted(
            () -> {
              // Verify that instance is in archived index
              final var searchResponseFromArchive =
                  esAdminClient.search(
                      s ->
                          s.index("operate-list-view-8.3.0_*-*-*")
                              .query(q -> q.term(t -> t.field("key").value(processInstanceKey))),
                      ProcessInstance.class);
              assertThat(searchResponseFromArchive.hits().total().value()).isEqualTo(1);

              // Verify it's not in the live index
              final var searchResponseFromLive =
                  esAdminClient.search(
                      s ->
                          s.index("operate-list-view-8.3.0_")
                              .query(q -> q.term(t -> t.field("key").value(processInstanceKey))),
                      ProcessInstance.class);
              assertThat(searchResponseFromLive.hits().total().value()).isEqualTo(0);
            });
  }
}
