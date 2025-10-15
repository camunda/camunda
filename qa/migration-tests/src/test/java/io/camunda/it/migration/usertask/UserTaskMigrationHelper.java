/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.it.migration.usertask;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Fail.fail;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.client.CamundaClient;
import io.camunda.it.migration.util.CamundaMigrator;
import io.camunda.migration.task.adapter.TaskLegacyIndex;
import io.camunda.qa.util.multidb.CamundaMultiDBExtension.DatabaseType;
import io.camunda.search.clients.core.SearchQueryHit;
import io.camunda.search.clients.core.SearchQueryRequest;
import io.camunda.search.clients.query.SearchQueryBuilders;
import io.camunda.tasklist.webapp.api.rest.v1.entities.TaskSearchRequest;
import io.camunda.tasklist.webapp.api.rest.v1.entities.TaskSearchResponse;
import io.camunda.webapps.schema.descriptors.template.TaskTemplate;
import io.camunda.webapps.schema.entities.usertask.TaskEntity;
import io.camunda.webapps.schema.entities.usertask.TaskEntity.TaskImplementation;
import io.camunda.webapps.schema.entities.usertask.TaskJoinRelationship.TaskJoinRelationshipType;
import io.camunda.webapps.schema.entities.usertask.TaskState;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.model.bpmn.builder.UserTaskBuilder;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodyHandlers;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.UnaryOperator;
import org.apache.commons.lang3.exception.UncheckedException;
import org.awaitility.Awaitility;

public abstract class UserTaskMigrationHelper {
  protected static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
  protected static final Map<String, Long> USER_TASK_KEYS = new HashMap<>();
  protected static final Map<TaskImplementation, Long> PROCESS_DEFINITION_KEYS = new HashMap<>();

  protected static void setup(
      final DatabaseType databaseType, final CamundaMigrator migrator, final String assignee) {

    final var jobWorkerProcessDefinitionKey =
        deployProcess(migrator.getCamundaClient(), t -> t.zeebeAssignee(assignee));
    PROCESS_DEFINITION_KEYS.put(TaskImplementation.JOB_WORKER, jobWorkerProcessDefinitionKey);

    final var zeebeProcessDefinitionKey =
        deployProcess(migrator.getCamundaClient(), t -> t.zeebeUserTask().zeebeAssignee(assignee));
    PROCESS_DEFINITION_KEYS.put(TaskImplementation.ZEEBE_USER_TASK, zeebeProcessDefinitionKey);

    var processInstanceKey =
        startProcessInstance(migrator.getCamundaClient(), zeebeProcessDefinitionKey);
    var taskKey = waitForTaskToBeImportedReturningId(migrator, processInstanceKey);
    USER_TASK_KEYS.put("first", taskKey);

    processInstanceKey =
        startProcessInstance(migrator.getCamundaClient(), zeebeProcessDefinitionKey);
    taskKey = waitForTaskToBeImportedReturningId(migrator, processInstanceKey);
    USER_TASK_KEYS.put("second", taskKey);

    processInstanceKey =
        startProcessInstance(migrator.getCamundaClient(), jobWorkerProcessDefinitionKey);
    taskKey = waitForTaskToBeImportedReturningId(migrator, processInstanceKey);
    USER_TASK_KEYS.put("third", taskKey);
  }

  protected static void completeUserTaskAndWaitForArchiving(
      final CamundaMigrator migrator, final long taskKey, final int waitPeriodSeconds) {
    try {
      final var res =
          migrator.request(
              b ->
                  b.POST(HttpRequest.BodyPublishers.noBody())
                      .uri(
                          URI.create(
                              migrator.getWebappsUrl()
                                  + "/user-tasks/%s/completion".formatted(taskKey))),
              BodyHandlers.discarding());
      assertThat(res.statusCode()).isEqualTo(204);
    } catch (final Exception e) {
      fail("Failed to complete user task with key %d".formatted(taskKey), e);
    }
    waitFor87TaskToBeArchived(migrator, taskKey, waitPeriodSeconds);
  }

  protected static void createEmpty87TaskDatedIndex(final CamundaMigrator migrator) {
    try {
      final var legacyIndex =
          new TaskLegacyIndex(migrator.getIndexPrefix(), migrator.isElasticsearch());
      final var response =
          migrator.request(
              b ->
                  b.PUT(HttpRequest.BodyPublishers.noBody())
                      .uri(
                          URI.create(
                              migrator.getDatabaseUrl()
                                  + "/"
                                  + legacyIndex.getFullQualifiedName()
                                  + "2025-01-01")),
              BodyHandlers.discarding());
    } catch (final Exception e) {
      throw new UncheckedException(e);
    }
  }

  protected static long startProcessInstance(
      final CamundaClient client, final long processDefinitionKey) {
    return client
        .newCreateInstanceCommand()
        .processDefinitionKey(processDefinitionKey)
        .send()
        .join()
        .getProcessInstanceKey();
  }

  protected static long waitForTaskToBeImportedReturningId(
      final CamundaMigrator migrator, final long piKey) {
    final AtomicLong userTaskKey = new AtomicLong();
    final String body;
    try {
      body =
          OBJECT_MAPPER.writeValueAsString(
              new TaskSearchRequest().setProcessInstanceKey(String.valueOf(piKey)));
    } catch (final JsonProcessingException e) {
      throw new RuntimeException(e);
    }
    Awaitility.await("Wait for tasks to be imported")
        .ignoreExceptions()
        .pollInterval(Duration.ofSeconds(1))
        .atMost(Duration.ofSeconds(30))
        .until(
            () -> {
              final var response =
                  migrator.request(
                      b ->
                          b.POST(HttpRequest.BodyPublishers.ofString(body))
                              .uri(URI.create(migrator.getWebappsUrl() + "/tasks/search")),
                      HttpResponse.BodyHandlers.ofString());
              final var tasks =
                  OBJECT_MAPPER.readValue(
                      response.body(), new TypeReference<List<TaskSearchResponse>>() {});
              if (!tasks.isEmpty()) {
                userTaskKey.set(Long.parseLong(tasks.getFirst().getId()));
                return true;
              }
              return false;
            });

    return userTaskKey.get();
  }

  protected long waitFor88CreatedTaskToBeImportedReturningId(
      final CamundaMigrator migrator, final long piKey) {
    final AtomicLong userTaskKey = new AtomicLong();

    final var processInstanceQuery =
        SearchQueryBuilders.and(
            SearchQueryBuilders.term(TaskTemplate.PROCESS_INSTANCE_ID, piKey),
            SearchQueryBuilders.term(
                TaskTemplate.JOIN_FIELD_NAME, TaskJoinRelationshipType.TASK.getType()),
            SearchQueryBuilders.term(TaskTemplate.STATE, TaskState.CREATED.name()));
    final var req =
        SearchQueryRequest.of(
            s ->
                s.query(processInstanceQuery)
                    .index(migrator.indexFor(TaskTemplate.class).getAlias()));
    Awaitility.await("Wait for tasks with CREATED state to be imported")
        .ignoreExceptions()
        .pollInterval(Duration.ofSeconds(1))
        .atMost(Duration.ofSeconds(30))
        .until(
            () -> {
              final Optional<Long> taskId =
                  migrator.getSearchClient().search(req, TaskEntity.class).hits().stream()
                      .findFirst()
                      .map(SearchQueryHit::source)
                      .map(TaskEntity::getKey);

              if (taskId.isPresent()) {
                userTaskKey.set(taskId.get());
                return true;
              }
              return false;
            });

    return userTaskKey.get();
  }

  protected static void waitFor87TaskToBeArchived(
      final CamundaMigrator migrator, final long taskKey, final int waitPeriodSeconds) {

    final var legacyIndex =
        new TaskLegacyIndex(migrator.getIndexPrefix(), migrator.isElasticsearch());

    final var request =
        SearchQueryRequest.of(
            s ->
                s.query(SearchQueryBuilders.term(TaskTemplate.KEY, taskKey))
                    .index(legacyIndex.getFullQualifiedName()));

    awaitArchivalOfTask(migrator, waitPeriodSeconds, request);
  }

  protected static void waitForTasksToBeArchived(
      final String indexName,
      final CamundaMigrator migrator,
      final List<Long> taskKeys,
      final int timeout) {
    final var request =
        SearchQueryRequest.of(
            s ->
                s.query(
                        SearchQueryBuilders.terms(
                                t -> t.field(TaskTemplate.KEY).longTerms(taskKeys))
                            .toSearchQuery())
                    .index(indexName));

    Awaitility.await("Wait for tasks to be archived")
        .pollInterval(Duration.ofSeconds(1))
        .atMost(Duration.ofSeconds(timeout))
        .until(() -> migrator.getSearchClient().search(request, TaskEntity.class).hits().isEmpty());
  }

  private static void awaitArchivalOfTask(
      final CamundaMigrator migrator,
      final int waitPeriodSeconds,
      final SearchQueryRequest request) {
    Awaitility.await("Wait for task to be archived")
        .pollInterval(Duration.ofSeconds(1))
        .atMost(Duration.ofSeconds(waitPeriodSeconds))
        .until(
            () -> {
              final Optional<Long> taskId =
                  migrator.getSearchClient().search(request, TaskEntity.class).hits().stream()
                      .findFirst()
                      .map(SearchQueryHit::source)
                      .map(TaskEntity::getKey);
              return taskId.isEmpty();
            });
  }

  protected static long deployProcess(
      final CamundaClient client, final UnaryOperator<UserTaskBuilder> builder) {
    final var process =
        Bpmn.createExecutableProcess("task-process")
            .startEvent()
            .name("start")
            .userTask("user-task", builder::apply)
            .endEvent()
            .done();

    return client
        .newDeployResourceCommand()
        .addProcessModel(process, "task-process.bpmn")
        .send()
        .join()
        .getProcesses()
        .getFirst()
        .getProcessDefinitionKey();
  }

  /// Complete a User Task in 8.7 Tasklist
  protected static void completeUserTask(final CamundaMigrator migrator, final String taskId) {
    try {
      migrator.request(
          b ->
              b.method("PATCH", HttpRequest.BodyPublishers.noBody())
                  .uri(URI.create(migrator.getWebappsUrl() + "/tasks/" + taskId + "/complete")),
          HttpResponse.BodyHandlers.discarding());
    } catch (final IOException | InterruptedException e) {
      throw new UncheckedException(e);
    }
  }

  /// Assign a User Task in 8.7 Tasklist
  protected static void assignUserTask(
      final CamundaMigrator migrator, final String taskId, final String assignee) {
    final String assignPayload;
    try {
      assignPayload = OBJECT_MAPPER.writeValueAsString(Map.of("assignee", assignee));
    } catch (final JsonProcessingException e) {
      throw new UncheckedException(e);
    }

    try {
      migrator.request(
          b ->
              b.method("PATCH", HttpRequest.BodyPublishers.ofString(assignPayload))
                  .uri(URI.create(migrator.getWebappsUrl() + "/tasks/" + taskId + "/assign")),
          HttpResponse.BodyHandlers.discarding());
    } catch (final IOException | InterruptedException e) {
      throw new UncheckedException(e);
    }
  }
}
