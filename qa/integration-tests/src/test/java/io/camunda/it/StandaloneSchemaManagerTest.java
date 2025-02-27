/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.it;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.application.Profile;
import io.camunda.qa.util.cluster.TestStandaloneCamunda;
import io.camunda.qa.util.cluster.TestStandaloneSchemaManager;
import io.camunda.search.clients.query.SearchQueryBuilders;
import io.camunda.zeebe.broker.system.configuration.ExporterCfg;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.qa.util.junit.ZeebeIntegration;
import io.camunda.zeebe.qa.util.junit.ZeebeIntegration.TestZeebe;
import io.camunda.zeebe.test.util.asserts.EitherAssert;
import java.io.IOException;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;
import java.util.Objects;
import org.awaitility.Awaitility;
import org.elasticsearch.client.RestClient;
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
final class StandaloneSchemaManagerTest {

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
          - names: ['zeebe-*', 'operate-*', 'tasklist-*']
            privileges: ['manage', 'read', 'write']
      """;

  private static final String TEST_USER_NAME = "foo";
  private static final String TEST_USER_PASSWORD = "bar";

  @TestZeebe(autoStart = false)
  final TestStandaloneSchemaManager schemaManager =
      new TestStandaloneSchemaManager()
          .withProperty(
              "zeebe.broker.exporters.elasticsearch.className",
              "io.camunda.zeebe.exporter.ElasticsearchExporter")
          .withProperty("zeebe.broker.exporters.elasticsearch.args.index.createTemplate", "true")
          .withProperty("zeebe.broker.exporters.elasticsearch.args.retention.enabled", "true")
          .withProperty(
              "zeebe.broker.exporters.elasticsearch.args.authentication.username", ADMIN_USER)
          .withProperty(
              "zeebe.broker.exporters.elasticsearch.args.authentication.password", ADMIN_PASSWORD)
          .withProperty("camunda.operate.elasticsearch.username", ADMIN_USER)
          .withProperty("camunda.operate.elasticsearch.password", ADMIN_PASSWORD)
          .withProperty("camunda.operate.elasticsearch.healthCheckEnabled", "false")
          .withProperty("camunda.operate.archiver.ilmEnabled", "true")
          .withProperty("camunda.tasklist.database", "elasticsearch")
          .withProperty("camunda.tasklist.elasticsearch.username", ADMIN_USER)
          .withProperty("camunda.tasklist.elasticsearch.password", ADMIN_PASSWORD)
          .withProperty("camunda.tasklist.elasticsearch.healthCheckEnabled", "false")
          .withProperty("camunda.tasklist.archiver.ilmEnabled", "true");

  @Container
  private final ElasticsearchContainer es =
      new ElasticsearchContainer(
              DockerImageName.parse("docker.elastic.co/elasticsearch/elasticsearch")
                  .withTag(RestClient.class.getPackage().getImplementationVersion()))
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

  @TestZeebe(autoStart = false)
  final TestStandaloneCamunda camunda =
      new TestStandaloneCamunda(es, false)
          .withProperty("camunda.operate.migration.migrationEnabled", false)
          .withProperty("camunda.tasklist.migration.migrationEnabled", false)
          .withProperty("camunda.database.username", APP_USER)
          .withProperty("camunda.database.password", APP_PASSWORD)
          .withBrokerConfig(
              cfg -> {
                cfg.getExporters()
                    .computeIfAbsent("elasticsearch", __ -> new ExporterCfg())
                    .setArgs(
                        Map.of(
                            "index",
                            Map.of("createTemplate", false),
                            "retention",
                            Map.of("enabled", false, "managePolicy", false),
                            "authentication",
                            Map.of("username", APP_USER, "password", APP_PASSWORD)));
              })
          .withOperateConfig(
              cfg -> {
                cfg.getElasticsearch().setCreateSchema(false);
                cfg.getElasticsearch().setHealthCheckEnabled(false);
                cfg.getElasticsearch().setUsername(APP_USER);
                cfg.getElasticsearch().setPassword(APP_PASSWORD);
                cfg.getZeebeElasticsearch().setUsername(APP_USER);
                cfg.getZeebeElasticsearch().setPassword(APP_PASSWORD);
                cfg.getArchiver().setIlmEnabled(true);
              })
          .withTasklistConfig(
              cfg -> {
                cfg.getElasticsearch().setCreateSchema(false);
                cfg.getElasticsearch().setHealthCheckEnabled(false);
                cfg.getElasticsearch().setUsername(APP_USER);
                cfg.getElasticsearch().setPassword(APP_PASSWORD);
                cfg.getZeebeElasticsearch().setUsername(APP_USER);
                cfg.getZeebeElasticsearch().setPassword(APP_PASSWORD);
                cfg.getArchiver().setIlmEnabled(true);
                cfg.getArchiver().setIlmManagePolicy(false);
              });

  @BeforeEach
  void setup() throws IOException, InterruptedException {
    // setup ES users
    es.execInContainer("elasticsearch-users", "useradd", APP_USER, "-p", APP_PASSWORD);
    es.execInContainer("elasticsearch-users", "useradd", ADMIN_USER, "-p", ADMIN_PASSWORD);
    es.execInContainer("elasticsearch-users", "roles", ADMIN_USER, "-a", ADMIN_ROLE);
    es.execInContainer("elasticsearch-users", "roles", APP_USER, "-a", APP_ROLE);
    // Connect to ES
    schemaManager.withProperty(
        "zeebe.broker.exporters.elasticsearch.args.url", "http://" + es.getHttpHostAddress());
    schemaManager.withProperty(
        "camunda.operate.elasticsearch.url", "http://" + es.getHttpHostAddress());
    schemaManager.withProperty(
        "camunda.tasklist.elasticsearch.url", "http://" + es.getHttpHostAddress());
  }

  @Test
  void canRunSchemaManager() {
    schemaManager.start();
  }

  @Test
  void canStartCamundaAfterSchemaManager() {
    schemaManager.start();
    camunda.start();
  }

  @Test
  void canUseCamunda() {
    // given
    schemaManager.start();
    camunda.withAdditionalProfile(Profile.DEFAULT_AUTH_PROFILE).start();
    try (final var operateClient = camunda.newOperateClient()) {
      operateClient.createUser(TEST_USER_NAME, TEST_USER_PASSWORD);
    }

    // when -- creating a process instance with user task
    final long processInstanceKey;
    try (final var zeebeClient = camunda.newClientBuilder().build()) {
      // Deploy process with user task
      zeebeClient
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
          zeebeClient
              .newCreateInstanceCommand()
              .bpmnProcessId("process-with-user-task")
              .latestVersion()
              .send()
              .join()
              .getProcessInstanceKey();
    }

    // then -- user task exists and can be completed
    try (final var tasklistClient =
        camunda.newTasklistClient().withAuthentication(TEST_USER_NAME, TEST_USER_PASSWORD)) {
      final var userTaskKey =
          Awaitility.await("should create a user task")
              .atMost(Duration.ofSeconds(60))
              .ignoreExceptions()
              .until(
                  () ->
                      tasklistClient
                          .searchUserTasks(SearchQueryBuilders.query().build())
                          .hits()
                          .getFirst()
                          .source()
                          .getKey(),
                  Objects::nonNull);
      assertThat(tasklistClient.assignUserTask(userTaskKey, TEST_USER_NAME))
          .returns(200, HttpResponse::statusCode);
      assertThat(tasklistClient.completeUserTask(userTaskKey))
          .returns(200, HttpResponse::statusCode);
    }

    // then -- process instance is completed
    try (final var operateClient =
        camunda.newOperateClient().withAuthentication(TEST_USER_NAME, TEST_USER_PASSWORD)) {
      Awaitility.await("process instance should be completed")
          .atMost(Duration.ofSeconds(60))
          .ignoreExceptions()
          .untilAsserted(
              () -> {
                final var result = operateClient.getProcessInstanceWith(processInstanceKey);
                EitherAssert.assertThat(result).isRight();
                assertThat(result.get().processInstances().getFirst().getEndDate()).isNotNull();
              });
    }
  }
}
