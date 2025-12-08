/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.it.backup;

import static java.time.Duration.ofSeconds;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Fail.fail;

import io.camunda.client.api.search.response.ProcessInstance;
import io.camunda.it.schema.strategy.ElasticsearchBackendStrategy;
import io.camunda.it.schema.strategy.OpenSearchBackendStrategy;
import io.camunda.it.schema.strategy.SearchBackendStrategy;
import io.camunda.qa.util.cluster.TestCamundaApplication;
import io.camunda.qa.util.cluster.TestStandaloneBackupManager;
import io.camunda.qa.util.cluster.TestStandaloneSchemaManager;
import io.camunda.webapps.backup.repository.WebappsSnapshotNameProvider;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.qa.util.junit.ZeebeIntegration;
import io.camunda.zeebe.qa.util.junit.ZeebeIntegration.TestZeebe;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Predicate;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.awaitility.Awaitility;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

@ZeebeIntegration
final class StandaloneBackupManagerIT {

  private static final String REPOSITORY_NAME = "backup-test";

  private static final long BACKUP_ID = 12345L;
  public static final String SNAPSHOT_NAME_PREFIX =
      new WebappsSnapshotNameProvider().getSnapshotNamePrefix(BACKUP_ID);

  // Configure the backup manager for testing
  @TestZeebe(autoStart = false)
  final TestStandaloneBackupManager backupManager = new TestStandaloneBackupManager();

  // Configure the schema manager to create indices and templates in test setup
  @TestZeebe(autoStart = false)
  final TestStandaloneSchemaManager schemaManager = new TestStandaloneSchemaManager();

  // Configure the Camunda single application with restricted access to the search backend
  @TestZeebe(autoStart = false)
  final TestCamundaApplication camunda = new TestCamundaApplication();

  static Stream<SearchBackendStrategy> strategies() {
    return Stream.of(new ElasticsearchBackendStrategy(), new OpenSearchBackendStrategy());
  }

  @ParameterizedTest
  @MethodSource("strategies")
  void canBackupRestore(final SearchBackendStrategy strategy) throws Exception {
    try (strategy) {
      // GIVEN
      // Initialize the search backend container and clients
      strategy.startContainer();
      strategy.createAdminClient();
      strategy.configureStandaloneSchemaManager(schemaManager);
      strategy.configureStandaloneBackupManager(backupManager, REPOSITORY_NAME);
      strategy.configureCamundaApplication(camunda);
      // create the schema
      schemaManager.start();
      // Create a snapshot repository to store backups
      strategy.createSnapshotRepository(REPOSITORY_NAME);
      // Start the Camunda application
      camunda.start();
      // Generate test data
      final long processInstanceKey = generateData();
      // Assert that the data is created in the search backend
      final long userTaskKey =
          assertThatDataIsPresent(processInstanceKey, isRunningProcessInstance());

      // WHEN
      // Start the backup process with a specific backup ID
      backupManager.withBackupId(BACKUP_ID).start();

      // Wait for snapshots to be completed
      final List<String> snapshots = waitForSnapshotsToBeCompleted(strategy, SNAPSHOT_NAME_PREFIX);
      // Update the current state by completing the user task and the process instance
      completeUserTask(userTaskKey);
      // Assert that the state is updated: process instance is completed
      assertThatDataIsPresent(processInstanceKey, isRunningProcessInstance().negate());

      // Stop the Camunda application
      camunda.stop();
      // Delete indices to simulate a fresh start
      deleteIndices(strategy);
      // Restore the backup to recover deleted data
      restoreBackup(strategy, snapshots);
      // Restart Camunda after restoration
      camunda.start();

      // THEN
      // Validate that the data is restored successfully at the state before the backup: process
      // instance is running
      assertThatDataIsPresent(processInstanceKey, isRunningProcessInstance());
    }
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
      final SearchBackendStrategy strategy, final String snapshotNamePrefix) {

    final List<String> completed = new ArrayList<>();
    final Pattern partPattern = Pattern.compile("part_(\\d+)_of_(\\d+)$");

    Awaitility.await("should find all snapshots completed")
        .atMost(ofSeconds(60))
        .pollDelay(ofSeconds(2))
        .untilAsserted(
            () -> {
              final var successSnapshots =
                  strategy.getSuccessSnapshots(REPOSITORY_NAME, snapshotNamePrefix);

              final int expectedTotal =
                  successSnapshots.stream()
                      .mapToInt(
                          name -> {
                            final var m = partPattern.matcher(name);
                            return m.find() ? Integer.parseInt(m.group(2)) : 0;
                          })
                      .max()
                      .orElse(0);

              assertThat(expectedTotal)
                  .withFailMessage("No snapshot parts detected yet")
                  .isGreaterThan(0);

              final var observedParts =
                  successSnapshots.stream()
                      .map(
                          name -> {
                            final var m = partPattern.matcher(name);
                            return m.find() ? Integer.parseInt(m.group(1)) : -1;
                          })
                      .filter(p -> p > 0)
                      .sorted()
                      .toList();

              assertThat(observedParts).hasSize(successSnapshots.size());
              assertThat(successSnapshots).hasSize(expectedTotal);
              assertThat(observedParts)
                  .containsExactlyInAnyOrder(
                      java.util.stream.IntStream.rangeClosed(1, expectedTotal)
                          .boxed()
                          .toArray(Integer[]::new));

              completed.clear();
              completed.addAll(successSnapshots);
            });

    return completed;
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

  private void deleteIndices(final SearchBackendStrategy strategy) throws IOException {
    strategy.deleteIndices("operate*", "tasklist*", "camunda*");
    Awaitility.await("should delete indices")
        .atMost(ofSeconds(30))
        .pollDelay(ofSeconds(2))
        .untilAsserted(
            () -> assertThat(strategy.indicesExist("operate*", "tasklist*", "camunda*")).isFalse());
  }

  private void restoreBackup(final SearchBackendStrategy strategy, final List<String> snapshots) {
    snapshots.forEach(
        snapshot -> {
          try {
            strategy.restoreBackup(REPOSITORY_NAME, snapshot);
          } catch (final IOException e) {
            fail("Exception occurred while restoring the backup: " + e.getMessage(), e);
          }
        });
  }

  private void completeUserTask(final long userTaskKey) {
    try (final var camundaClient = camunda.newClientBuilder().build()) {
      camundaClient.newCompleteUserTaskCommand(userTaskKey).execute();
    }
  }

  private static Predicate<ProcessInstance> isRunningProcessInstance() {
    return processInstance -> processInstance.getEndDate() == null;
  }
}
