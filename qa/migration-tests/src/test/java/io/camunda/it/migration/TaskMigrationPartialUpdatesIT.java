/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.it.migration;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;

import io.camunda.it.migration.usertask.UserTaskMigrationHelper;
import io.camunda.it.migration.util.CamundaMigrator;
import io.camunda.it.migration.util.MigrationITExtension;
import io.camunda.migration.commons.configuration.ConfigurationType;
import io.camunda.migration.commons.configuration.MigrationConfiguration;
import io.camunda.migration.commons.configuration.MigrationProperties;
import io.camunda.migration.task.TaskMigrator;
import io.camunda.migration.task.adapter.TaskLegacyIndex;
import io.camunda.search.clients.core.SearchQueryHit;
import io.camunda.search.clients.core.SearchQueryRequest;
import io.camunda.search.clients.query.SearchQueryBuilders;
import io.camunda.search.connect.configuration.ConnectConfiguration;
import io.camunda.search.schema.SchemaManager;
import io.camunda.search.schema.config.RetentionConfiguration;
import io.camunda.webapps.schema.descriptors.index.TasklistImportPositionIndex;
import io.camunda.webapps.schema.descriptors.template.TaskTemplate;
import io.camunda.webapps.schema.entities.usertask.TaskEntity;
import io.camunda.webapps.schema.entities.usertask.TaskEntity.TaskImplementation;
import io.camunda.zeebe.model.bpmn.builder.AbstractUserTaskBuilder;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse.BodyHandlers;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicReference;
import org.apache.commons.lang3.exception.UncheckedException;
import org.apache.commons.lang3.tuple.Pair;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.MethodOrderer.OrderAnnotation;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.api.condition.DisabledIfSystemProperty;
import org.junit.jupiter.api.extension.RegisterExtension;

@Tag("multi-db-test")
@DisabledIfSystemProperty(named = "test.integration.camunda.database.type", matches = "rdbms")
@DisabledIfSystemProperty(named = "test.integration.camunda.database.type", matches = "AWS_OS")
@TestMethodOrder(OrderAnnotation.class)
public class TaskMigrationPartialUpdatesIT extends UserTaskMigrationHelper {
  private static final Map<Long, TaskData> PI_TASKS = new HashMap<>();
  private static final List<Long> ARCHIVING_BACKLOG = new ArrayList<>();
  private static final long ONE_DAY_MILLIS = ChronoUnit.DAYS.getDuration().toMillis();
  private static final long WAIT_PERIOD_BEFORE_ARCHIVING_SECONDS = 5;
  private static final long CLOCK_STEP_MILLIS = (WAIT_PERIOD_BEFORE_ARCHIVING_SECONDS + 1) * 1000;
  private static final int TASK_COUNT = 20;
  private static Instant clock =
      Instant.now().minus(TASK_COUNT, ChronoUnit.DAYS).truncatedTo(ChronoUnit.MILLIS);
  private static final HttpClient HTTP_CLIENT = HttpClient.newHttpClient();
  private static TaskLegacyIndex legacyIndex;
  private static TaskTemplate taskIndex;

  @RegisterExtension
  static final MigrationITExtension PROVIDER =
      new MigrationITExtension()
          .withBeforeUpgradeConsumer((db, migrator) -> preconditions(migrator))
          .withCamundaExporterArgsOverride(
              args -> {
                ((Map) args.get("history"))
                    .put("waitPeriodBeforeArchiving", WAIT_PERIOD_BEFORE_ARCHIVING_SECONDS + "s");
                ((Map) args.get("history")).put("delayBetweenRuns", "1000");
              })
          .withUpgradeSystemPropertyOverrides(Map.of("zeebe.clock.controlled", "true"))
          .withInitialEnvOverrides(
              Map.of(
                  "ZEEBE_CLOCK_CONTROLLED",
                  "true",
                  "CAMUNDA_TASKLIST_ARCHIVER_WAITPERIODBEFOREARCHIVING",
                  WAIT_PERIOD_BEFORE_ARCHIVING_SECONDS + "s"));

  @Test
  void shouldCompleteMigrationWithPartialData(final CamundaMigrator migrator)
      throws IOException, InterruptedException {

    // Archiver should be blocked on 8.8 startup
    final var tasklistImportPositionIndex =
        new TasklistImportPositionIndex(migrator.getIndexPrefix(), migrator.isElasticsearch());
    assertArchiverState(tasklistImportPositionIndex, true);

    // Pin clock to last known time
    pinClock(migrator.getActuatorUrl() + "/clock", clock.toEpochMilli());

    // Perform Assignment and Completion of User Tasks - Resulting in partial documents
    PI_TASKS.entrySet().stream()
        .filter(e -> !e.getValue().completed)
        .filter(e -> e.getValue().implementation.equals(TaskImplementation.ZEEBE_USER_TASK))
        .forEach(e -> tryAssignAndCompleteTask(migrator, e.getValue()));

    // Start Task Migration
    startTaskMigration(migrator);

    // Archiver should be unblocked when migration completes
    assertArchiverState(tasklistImportPositionIndex, false);

    waitForTasksToBeArchived(taskIndex.getFullQualifiedName(), migrator, ARCHIVING_BACKLOG, 60);
    assertTasksAreInCorrectIndices(migrator);

    generateState(migrator, migrator.getActuatorUrl() + "/clock");
    // Ensure that all other tasks can be completed and archived normally
    PI_TASKS.values().stream()
        .filter(task -> !task.completed)
        .forEach(task -> tryAssignAndCompleteTask(migrator, task));
    waitForTasksToBeArchived(
        taskIndex.getFullQualifiedName(), migrator, PI_TASKS.keySet().stream().toList(), 60);
  }

  private void startTaskMigration(final CamundaMigrator migrator) {
    final var connectConfiguration = new ConnectConfiguration();
    connectConfiguration.setUrl(PROVIDER.getDatabaseUrl());
    connectConfiguration.setType(migrator.isElasticsearch() ? "elasticsearch" : "opensearch");
    connectConfiguration.setIndexPrefix(migrator.getIndexPrefix());

    final var migrationConfiguration = new MigrationConfiguration();
    migrationConfiguration.setBatchSize(1);

    final var migrationProperties = new MigrationProperties();
    migrationProperties.setMigration(Map.of(ConfigurationType.TASKS, migrationConfiguration));

    // Run the Task Migration
    Executors.newSingleThreadExecutor()
        .submit(
            () ->
                new TaskMigrator(
                        connectConfiguration,
                        migrationProperties,
                        new SimpleMeterRegistry(),
                        new RetentionConfiguration())
                    .call());
  }

  private static void preconditions(final CamundaMigrator migrator) {
    try {
      final String actuatorUrl = migrator.getActuatorUrl() + "/clock";
      legacyIndex = new TaskLegacyIndex(migrator.getIndexPrefix(), migrator.isElasticsearch());
      taskIndex = new TaskTemplate(migrator.getIndexPrefix(), migrator.isElasticsearch());
      pinClock(actuatorUrl, clock.toEpochMilli());
      dataSetup(migrator);
    } catch (final IOException | InterruptedException ignored) {
      // ignored
    }
  }

  private static void dataSetup(final CamundaMigrator migrator)
      throws IOException, InterruptedException {

    final String actuatorUrl = migrator.getActuatorUrl() + "/clock";

    final var jobWorkerProcessDefinitionKey = deployProcess(migrator.getCamundaClient(), t -> t);
    PROCESS_DEFINITION_KEYS.put(TaskImplementation.JOB_WORKER, jobWorkerProcessDefinitionKey);

    final var zeebeProcessDefinitionKey =
        deployProcess(migrator.getCamundaClient(), AbstractUserTaskBuilder::zeebeUserTask);
    PROCESS_DEFINITION_KEYS.put(TaskImplementation.ZEEBE_USER_TASK, zeebeProcessDefinitionKey);

    // Run User Tasks
    generateState87(migrator, actuatorUrl);
  }

  private static void generateState87(final CamundaMigrator migrator, final String clockActuatorUrl)
      throws IOException, InterruptedException {
    final List<Long> waitForArchival = new ArrayList<>();

    for (int i = 0; i < TASK_COUNT; i++) {
      final TaskImplementation implementation =
          i % 2 == 0 ? TaskImplementation.JOB_WORKER : TaskImplementation.ZEEBE_USER_TASK;
      final var processInstanceKey =
          startProcessInstance(
              migrator.getCamundaClient(), PROCESS_DEFINITION_KEYS.get(implementation));
      final var taskKey = waitForTaskToBeImportedReturningId(migrator, processInstanceKey);
      PI_TASKS.put(
          processInstanceKey,
          new TaskData(implementation, processInstanceKey, taskKey, false, clock, false, false));

      if (waitForArchival.size() <= TASK_COUNT / 2) {
        assignAndComplete87Task(migrator, taskKey);
        waitForArchival.add(taskKey);
      }
      if (i % 2 != 0) {
        clock = clock.plus(ONE_DAY_MILLIS, ChronoUnit.MILLIS);
        progressClock(clockActuatorUrl, ONE_DAY_MILLIS);
      }
    }
    clock = clock.plus(CLOCK_STEP_MILLIS, ChronoUnit.MILLIS);
    progressClock(clockActuatorUrl, CLOCK_STEP_MILLIS);
    waitForTasksToBeArchived(legacyIndex.getFullQualifiedName(), migrator, waitForArchival, 120);
  }

  private void generateState(final CamundaMigrator migrator, final String clockActuatorUrl)
      throws IOException, InterruptedException {

    for (int i = 0; i < TASK_COUNT; i++) {
      final TaskImplementation implementation =
          i % 2 == 0 ? TaskImplementation.JOB_WORKER : TaskImplementation.ZEEBE_USER_TASK;
      final var processInstanceKey =
          startProcessInstance(
              migrator.getCamundaClient(), PROCESS_DEFINITION_KEYS.get(implementation));
      final var taskKey = waitFor88CreatedTaskToBeImportedReturningId(migrator, processInstanceKey);
      PI_TASKS.put(
          processInstanceKey,
          new TaskData(implementation, processInstanceKey, taskKey, false, clock, true, false));

      if (i % 2 != 0) {
        clock = clock.plus(ONE_DAY_MILLIS, ChronoUnit.MILLIS);
        progressClock(clockActuatorUrl, ONE_DAY_MILLIS);
      }
    }
  }

  private static void assignAndComplete87Task(final CamundaMigrator migrator, final long taskKey) {
    assignUserTask(migrator, String.valueOf(taskKey), "demo");

    completeUserTask(migrator, String.valueOf(taskKey));

    final var entry =
        PI_TASKS.entrySet().stream()
            .filter(e -> e.getValue().taskKey() == taskKey)
            .findFirst()
            .orElseThrow();
    PI_TASKS.compute(
        entry.getKey(),
        (k, currentTaskData) ->
            new TaskData(
                currentTaskData.implementation,
                currentTaskData.processInstanceKey,
                entry.getValue().taskKey,
                true,
                currentTaskData.date,
                currentTaskData.startedAfterUpgrade,
                false));
  }

  private void tryAssignAndCompleteTask(final CamundaMigrator migrator, final TaskData taskData) {
    if (Math.random() > 0.6) {
      if (taskData.implementation.equals(TaskImplementation.ZEEBE_USER_TASK)) {
        migrator
            .getCamundaClient()
            .newAssignUserTaskCommand(taskData.taskKey)
            .assignee("demo")
            .send()
            .join();

        migrator.getCamundaClient().newCompleteUserTaskCommand(taskData.taskKey).send().join();

      } else {
        var res =
            migrator
                .getTasklistClient()
                .withAuthentication("demo", "demo")
                .assignUserTask(taskData.taskKey, "demo");
        assertThat(res.statusCode()).isEqualTo(200);
        res =
            migrator
                .getTasklistClient()
                .withAuthentication("demo", "demo")
                .completeUserTask(taskData.taskKey);
        assertThat(res.statusCode()).isEqualTo(200);
      }
      ARCHIVING_BACKLOG.add(taskData.taskKey);
      final var entry =
          PI_TASKS.entrySet().stream()
              .filter(e -> e.getValue().taskKey() == taskData.taskKey())
              .findFirst()
              .orElseThrow();
      PI_TASKS.compute(
          entry.getKey(),
          (k, currentTaskData) ->
              new TaskData(
                  currentTaskData.implementation,
                  currentTaskData.processInstanceKey,
                  entry.getValue().taskKey,
                  true,
                  clock,
                  currentTaskData.startedAfterUpgrade,
                  true));
    }

    clock = clock.plus(CLOCK_STEP_MILLIS, ChronoUnit.MILLIS);
    try {
      progressClock(migrator.getActuatorUrl() + "/clock", CLOCK_STEP_MILLIS);
    } catch (final IOException | InterruptedException e) {
      throw new UncheckedException(e);
    }
  }

  // Use plain HTTP to pin the clock as 8.8 Actuator is not compatible with 8.7
  private static void pinClock(final String actuatorUrl, final long epochMilli)
      throws IOException, InterruptedException {
    final var request =
        HttpRequest.newBuilder()
            .uri(URI.create(actuatorUrl + "/pin"))
            .header("Content-Type", "application/json")
            .POST(
                HttpRequest.BodyPublishers.ofString(
                    String.format("{\"epochMilli\":\"%s\"}", epochMilli)))
            .build();

    HTTP_CLIENT.send(request, BodyHandlers.discarding());
  }

  // Use plain HTTP to progress the clock as 8.8 Actuator is not compatible with 8.7
  private static void progressClock(final String actuatorUrl, final long offsetMilli)
      throws IOException, InterruptedException {

    final var request =
        HttpRequest.newBuilder()
            .uri(URI.create(actuatorUrl + "/add"))
            .header("Content-Type", "application/json")
            .POST(
                HttpRequest.BodyPublishers.ofString(
                    String.format("{\"offsetMilli\":\"%s\"}", offsetMilli)))
            .build();

    HTTP_CLIENT.send(request, BodyHandlers.discarding());
  }

  private void assertArchiverState(
      final TasklistImportPositionIndex tasklistImportPositionIndex, final boolean blocked) {
    Awaitility.await()
        .atMost(Duration.ofSeconds(30))
        .untilAsserted(
            () -> {
              try {
                assertThat(fetchArchiverBlockedProperty(tasklistImportPositionIndex))
                    .withFailMessage("Expected Archiver to have state blocked:" + blocked)
                    .isEqualTo(blocked);
              } catch (final IOException | InterruptedException e) {
                throw new RuntimeException(e);
              }
            });
  }

  private boolean fetchArchiverBlockedProperty(
      final TasklistImportPositionIndex tasklistImportPositionIndex)
      throws IOException, InterruptedException {
    final var httpReq =
        HttpRequest.newBuilder()
            .uri(
                URI.create(
                    PROVIDER.getDatabaseUrl()
                        + "/"
                        + tasklistImportPositionIndex.getFullQualifiedName()
                        + "/_mapping"))
            .build();
    final var res = HTTP_CLIENT.send(httpReq, BodyHandlers.ofString());
    final var node = OBJECT_MAPPER.readTree(res.body());
    final var blockedPropertyNode =
        node.at(
            "/"
                + tasklistImportPositionIndex.getFullQualifiedName()
                + "/mappings/_meta/"
                + SchemaManager.ARCHIVING_BLOCKED_META_KEY);
    return blockedPropertyNode.isMissingNode() ? false : blockedPropertyNode.asBoolean();
  }

  private void assertTasksAreInCorrectIndices(final CamundaMigrator migrator) {
    final var taskIndex = new TaskTemplate(migrator.getIndexPrefix(), migrator.isElasticsearch());
    PI_TASKS.forEach(
        (key, value) -> {
          final var taskEntityIndexPair = awaitTaskPresentInSingleIndex(migrator, value);
          final var completionDate = value.date.atZone(ZoneId.systemDefault()).toLocalDate();
          if (value.completed) {
            if (!value.completedAfterUpgrade) {
              final var indexName =
                  String.format(
                      "%s%d-%02d-%02d",
                      taskIndex.getFullQualifiedName(),
                      completionDate.getYear(),
                      completionDate.getMonthValue(),
                      completionDate.getDayOfMonth());
              assertThat(taskEntityIndexPair.getKey()).isEqualTo(indexName);
            } else {
              assertThat(taskEntityIndexPair.getKey())
                  .isNotEqualTo(taskIndex.getFullQualifiedName());
            }
          } else {
            assertThat(taskEntityIndexPair.getKey()).isEqualTo(taskIndex.getFullQualifiedName());
          }

          assertThat(taskEntityIndexPair.getValue())
              .withFailMessage(
                  "Found %s when expecting %s in %s",
                  taskEntityIndexPair.getValue(), value, taskEntityIndexPair.getKey())
              .extracting(
                  TaskEntity::getKey,
                  TaskEntity::getAssignee,
                  TaskEntity::getImplementation,
                  TaskEntity::getCompletionTime)
              .containsExactly(
                  value.taskKey,
                  value.completed ? "demo" : null,
                  value.implementation(),
                  value.completed() ? taskEntityIndexPair.getValue().getCompletionTime() : null);
          if (value.completed()) {
            assertThat(taskEntityIndexPair.getValue().getCompletionTime()).isNotNull();
          } else {
            assertThat(taskEntityIndexPair.getValue().getCompletionTime()).isNull();
          }
        });
  }

  private Pair<String, TaskEntity> awaitTaskPresentInSingleIndex(
      final CamundaMigrator migrator, final TaskData taskData) {
    final var taskIndex = new TaskTemplate(migrator.getIndexPrefix(), migrator.isElasticsearch());
    final var search =
        SearchQueryRequest.of(
            s ->
                s.query(SearchQueryBuilders.term(TaskTemplate.KEY, taskData.taskKey))
                    .index(taskIndex.getFullQualifiedName() + "*"));

    final var tuple = new AtomicReference<Pair<String, TaskEntity>>();

    Awaitility.await("Wait for task to be present only on one index")
        .pollInterval(Duration.ofSeconds(1))
        .ignoreExceptions()
        .atMost(Duration.ofMinutes(1))
        .untilAsserted(
            () -> taskData,
            (task) -> {
              final var searchResponse =
                  migrator.getSearchClient().search(search, TaskEntity.class);
              assertThat(searchResponse.hits().size())
                  .withFailMessage(
                      "Expected task %s to be in exactly 1 index but found %d. Task exists in indices: %s",
                      taskData,
                      searchResponse.hits().size(),
                      searchResponse.hits().stream().map(SearchQueryHit::index).distinct().toList())
                  .isOne();
              final var taskEntity =
                  searchResponse.hits().stream().findFirst().map(SearchQueryHit::source);
              assertThat(taskEntity).isPresent();
              tuple.set(
                  Pair.of(
                      searchResponse.hits().stream().findFirst().get().index(), taskEntity.get()));
            });
    return tuple.get();
  }

  private record TaskData(
      TaskImplementation implementation,
      long processInstanceKey,
      long taskKey,
      boolean completed,
      Instant date,
      boolean startedAfterUpgrade,
      boolean completedAfterUpgrade) {}
}
