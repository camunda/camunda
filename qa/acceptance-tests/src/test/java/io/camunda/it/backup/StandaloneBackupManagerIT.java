/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.it.backup;

import static io.camunda.zeebe.qa.util.cluster.TestSpringApplication.setupElasticsearchUrl;
import static java.time.Duration.ofSeconds;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Fail.fail;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import io.camunda.application.Profile;
import io.camunda.client.api.search.response.ProcessInstance;
import io.camunda.exporter.CamundaExporter;
import io.camunda.qa.util.cluster.TestCamundaApplication;
import io.camunda.qa.util.cluster.TestStandaloneBackupManager;
import io.camunda.qa.util.cluster.TestStandaloneSchemaManager;
import io.camunda.search.connect.configuration.ConnectConfiguration;
import io.camunda.search.connect.es.ElasticsearchConnector;
import io.camunda.webapps.backup.repository.WebappsSnapshotNameProvider;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.qa.util.junit.ZeebeIntegration;
import io.camunda.zeebe.qa.util.junit.ZeebeIntegration.TestZeebe;
import java.io.IOException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Predicate;
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
final class StandaloneBackupManagerIT {

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

  private static final String REPOSITORY_NAME = "els-test";

  private static final long BACKUP_ID = 12345L;
  public static final String SNAPSHOT_NAME_PREFIX =
      new WebappsSnapshotNameProvider().getSnapshotNamePrefix(BACKUP_ID);

  // Configure the backup manager for testing
  @TestZeebe(autoStart = false)
  final TestStandaloneBackupManager backupManager =
      new TestStandaloneBackupManager()
          .withProperty("camunda.database.username", ADMIN_USER)
          .withProperty("camunda.database.password", ADMIN_PASSWORD)
          .withProperty("camunda.backup.webapps.repositoryName", "els-test");

  // Configure the schema manager to create indices and templates in test setup
  @TestZeebe(autoStart = false)
  final TestStandaloneSchemaManager schemaManager =
      new TestStandaloneSchemaManager()
          .withProperty("camunda.database.username", ADMIN_USER)
          .withProperty("camunda.database.password", ADMIN_PASSWORD)
          .withProperty("camunda.database.retention.enabled", "true");

  // Configure the Camunda single application with restricted access to the Elasticsearch
  @TestZeebe(autoStart = false)
  final TestCamundaApplication camunda =
      new TestCamundaApplication()
          .withAdditionalProfile(Profile.CONSOLIDATED_AUTH)
          .withCreateSchema(false)
          .withUnauthenticatedAccess()
          .withProperty("camunda.database.username", APP_USER)
          .withProperty("camunda.database.password", APP_PASSWORD)
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
                        false));
              });

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
          // Configure with allowed repository storage path
          .withEnv("path.repo", "~/")
          // to be able to delete indices with wildcards
          .withEnv("action.destructive_requires_name", "false")
          .withCopyToContainer(
              Transferable.of(APP_ROLE_DEFINITION), "/usr/share/elasticsearch/config/roles.yml")
          .withClasspathResourceMapping(
              "elasticsearch-fast-startup.options",
              "/usr/share/elasticsearch/config/jvm.options.d/elasticsearch-fast-startup.options",
              BindMode.READ_ONLY);

  // Elasticsearch client with admin access to apply some manual operations
  private ElasticsearchClient adminElasticsearchClient;

  @BeforeEach
  void setup() throws IOException, InterruptedException {
    // setup ES admin user
    es.execInContainer("elasticsearch-users", "useradd", ADMIN_USER, "-p", ADMIN_PASSWORD);
    es.execInContainer("elasticsearch-users", "roles", ADMIN_USER, "-a", ADMIN_ROLE);

    adminElasticsearchClient = createAdminElasticsearchClient("http://" + es.getHttpHostAddress());

    // create app user with APP_ROLE role
    Awaitility.await()
        .atMost(Duration.ofSeconds(10))
        .pollInterval(Duration.ofSeconds(1))
        .ignoreExceptions()
        .until(
            () ->
                adminElasticsearchClient
                    .security()
                    .putUser(r -> r.username(APP_USER).password(APP_PASSWORD).roles(APP_ROLE))
                    .created());

    final String esUrl = "http://" + es.getHttpHostAddress();

    // Connect to ES in Standalone Schema Manager
    setupElasticsearchUrl(schemaManager, esUrl);

    // Connect to ES in Camunda
    setupElasticsearchUrl(camunda, esUrl);
    camunda
        .withProperty("camunda.data.secondary-storage.type", "elasticsearch")
        .updateExporterArgs(
            CamundaExporter.class.getSimpleName(),
            args -> ((Map) args.get("connect")).put("url", esUrl));

    // Connect to ES in Backup Manager
    setupElasticsearchUrl(backupManager, esUrl);
  }

  @Test
  void canBackupRestore() throws Exception {
    // GIVEN
    // create the schema
    schemaManager.start();
    // Create a snapshot repository to store backups
    createSnapshotRepository();
    // Start the Camunda application
    camunda.start();
    // Generate test data
    final long processInstanceKey = generateData();
    // Assert that the data is created in Elasticsearch
    final long userTaskKey =
        assertThatDataIsPresent(processInstanceKey, isRunningProcessInstance());

    // WHEN
    // Start the backup process with a specific backup ID
    backupManager.withBackupId(BACKUP_ID).start();

    // Wait for snapshots to be completed
    final List<String> snapshots = waitForSnapshotsToBeCompleted(SNAPSHOT_NAME_PREFIX, 7);
    // Update the current state by completing the user task and the process instance
    completeUserTask(userTaskKey);
    // Assert that the state is updated: process instance is completed
    assertThatDataIsPresent(processInstanceKey, isRunningProcessInstance().negate());

    // Stop the Camunda application
    camunda.stop();
    // Delete indices to simulate a fresh start
    deleteIndices();
    // Restore the backup to recover deleted data
    restoreBackup(snapshots);
    // Restart Camunda after restoration
    camunda.start();

    // THEN
    // Validate that the data is restored successfully at the state before the backup: process
    // instance is running
    assertThatDataIsPresent(processInstanceKey, isRunningProcessInstance());
  }

  private long generateData() {
    // creating a process instance with user task
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
                  .zeebeAssignee("demo")
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
    return processInstanceKey;
  }

  private List<String> waitForSnapshotsToBeCompleted(
      final String snapshotNamePrefix, final int count) {
    final var snapshots = new ArrayList<String>();
    Awaitility.await("should find all snapshots completed")
        .atMost(ofSeconds(30))
        .pollDelay(ofSeconds(2))
        .untilAsserted(
            () -> {
              final var response =
                  adminElasticsearchClient
                      .snapshot()
                      .get(r -> r.repository(REPOSITORY_NAME).snapshot(snapshotNamePrefix + "*"))
                      .snapshots();
              assertThat(response).hasSize(count);
              assertThat(response.stream().map(snapshot -> snapshot.state()))
                  .allMatch(state -> "SUCCESS".equals(state));
              snapshots.addAll(response.stream().map(snapshot -> snapshot.snapshot()).toList());
            });
    return snapshots;
  }

  /**
   * Asserts that the data is present in Operate and Tasklist.
   *
   * <ul>
   *   <li>Asserts that the process instance is present in Operate and verifies the check.
   *   <li>Asserts that one user task is present in Tasklist and returns its key.
   * </ul>
   */
  private long assertThatDataIsPresent(
      final long processInstanceKey, final Predicate<ProcessInstance> processInstanceCheck) {
    final var userTaskKey = new AtomicReference<Long>();
    try (final var camundaClient = camunda.newClientBuilder().build()) {
      Awaitility.await("should find a process instance")
          .atMost(ofSeconds(30))
          .ignoreExceptions()
          .untilAsserted(
              () -> {
                final var processInstance =
                    camundaClient.newProcessInstanceGetRequest(processInstanceKey).execute();
                assertThat(processInstance).matches(processInstanceCheck);
              });
      Awaitility.await("should find a user task")
          .atMost(ofSeconds(30))
          .ignoreExceptions()
          .untilAsserted(
              () -> {
                final var hits = camundaClient.newUserTaskSearchRequest().execute().items();
                assertThat(hits).hasSize(1);
                userTaskKey.set(hits.get(0).getUserTaskKey());
              });
    }
    return userTaskKey.get();
  }

  private void deleteIndices() throws IOException {
    adminElasticsearchClient
        .indices()
        .delete(r -> r.index("operate*", "tasklist*", "camunda*").ignoreUnavailable(true));
    Awaitility.await("should delete indices")
        .atMost(ofSeconds(30))
        .pollDelay(ofSeconds(2))
        .untilAsserted(
            () ->
                assertThat(
                        adminElasticsearchClient
                            .indices()
                            .exists(
                                r ->
                                    r.index("operate*", "tasklist*", "camunda*")
                                        .allowNoIndices(false))
                            .value())
                    .isFalse());
  }

  /**
   * As documented in
   * https://docs.camunda.io/docs/self-managed/operational-guides/backup-restore/backup-and-restore/#restore
   */
  private void restoreBackup(final List<String> snapshots) {
    snapshots.stream()
        .forEach(
            snapshot -> {
              try {
                adminElasticsearchClient
                    .snapshot()
                    .restore(
                        r ->
                            r.repository(REPOSITORY_NAME)
                                .snapshot(snapshot)
                                .waitForCompletion(true));
              } catch (final IOException e) {
                fail("Exception occurred while restoring the backup: " + e.getMessage(), e);
              }
            });
  }

  private void createSnapshotRepository() throws IOException {
    adminElasticsearchClient
        .snapshot()
        .createRepository(
            q ->
                q.name(REPOSITORY_NAME)
                    .repository(r -> r.fs(fs -> fs.settings(s -> s.location(REPOSITORY_NAME)))));
  }

  private void completeUserTask(final long userTaskKey) {
    try (final var camundaClient = camunda.newClientBuilder().build()) {
      camundaClient.newUserTaskCompleteCommand(userTaskKey).execute();
    }
  }

  private ElasticsearchClient createAdminElasticsearchClient(final String elasticSearchUrl) {
    final var connectConfiguration = new ConnectConfiguration();
    connectConfiguration.setUrl(elasticSearchUrl);
    connectConfiguration.setUsername(ADMIN_USER);
    connectConfiguration.setPassword(ADMIN_PASSWORD);
    return new ElasticsearchConnector(connectConfiguration).createClient();
  }

  private static Predicate<ProcessInstance> isRunningProcessInstance() {
    return processInstance -> processInstance.getEndDate() == null;
  }
}
