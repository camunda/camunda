/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.it.migration.usertask;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.client.CamundaClient;
import io.camunda.it.migration.util.CamundaMigrator;
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
import java.net.URI;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.UnaryOperator;
import org.awaitility.Awaitility;

public abstract class UserTaskMigrationHelper {
  protected static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
  protected static final Map<String, Long> USER_TASK_KEYS = new HashMap<>();
  protected static final Map<TaskImplementation, Long> PROCESS_DEFINITION_KEYS = new HashMap<>();

  static void setup(
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
}
