/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.it.migration;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.it.migration.util.ExporterUpdateITProvider;
import io.camunda.it.migration.util.ExporterUpdateITProvider.Component;
import io.camunda.it.migration.util.TasklistComponentHelper;
import io.camunda.search.connect.configuration.DatabaseType;
import io.camunda.tasklist.webapp.api.rest.v1.entities.TaskSearchRequest;
import io.camunda.tasklist.webapp.api.rest.v1.entities.TaskSearchResponse;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.model.bpmn.builder.AbstractUserTaskBuilder;
import io.camunda.zeebe.model.bpmn.builder.UserTaskBuilder;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.UnaryOperator;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestInstance.Lifecycle;
import org.junit.jupiter.api.TestTemplate;
import org.junit.jupiter.api.extension.RegisterExtension;

@TestInstance(Lifecycle.PER_CLASS)
public class MigrationUserTaskUpdateIT {

  @RegisterExtension
  static final ExporterUpdateITProvider provider = new ExporterUpdateITProvider();

  private static final ObjectMapper objectMapper = new ObjectMapper();

  private HttpResponse<Void> assignTask(
      final TasklistComponentHelper tasklistComponentHelper,
      final long userTaskKey,
      final String assignee)
      throws IOException, InterruptedException {
    return tasklistComponentHelper.request(
        b ->
            b.method(
                    "PATCH",
                    HttpRequest.BodyPublishers.ofString("{\"assignee\":\"" + assignee + "\"}"))
                .uri(
                    URI.create(
                        tasklistComponentHelper.getUrl() + "/tasks/" + userTaskKey + "/assign")),
        HttpResponse.BodyHandlers.discarding());
  }

  private HttpResponse<Void> unassignTask(
      final TasklistComponentHelper tasklistComponentHelper, final long userTaskKey)
      throws IOException, InterruptedException {
    return tasklistComponentHelper.request(
        b ->
            b.method("PATCH", HttpRequest.BodyPublishers.noBody())
                .uri(
                    URI.create(
                        tasklistComponentHelper.getUrl() + "/tasks/" + userTaskKey + "/unassign")),
        HttpResponse.BodyHandlers.discarding());
  }

  private HttpResponse<Void> completeTask(
      final TasklistComponentHelper tasklistComponentHelper, final long userTaskKey)
      throws InterruptedException, IOException {
    return tasklistComponentHelper.request(
        b ->
            b.method("PATCH", HttpRequest.BodyPublishers.noBody())
                .uri(
                    URI.create(
                        tasklistComponentHelper.getUrl() + "/tasks/" + userTaskKey + "/complete")),
        HttpResponse.BodyHandlers.discarding());
  }

  private long deployAndStartUserTask(
      final DatabaseType databaseType, final UnaryOperator<UserTaskBuilder> builder) {
    final var process =
        Bpmn.createExecutableProcess("task-process")
            .startEvent()
            .name("start")
            .userTask("user-task", builder::apply)
            .endEvent()
            .done();

    provider
        .getCamundaClient(databaseType)
        .newDeployResourceCommand()
        .addProcessModel(process, "task-process.bpmn")
        .send()
        .join();

    return provider
        .getCamundaClient(databaseType)
        .newCreateInstanceCommand()
        .bpmnProcessId("task-process")
        .latestVersion()
        .send()
        .join()
        .getProcessInstanceKey();
  }

  private long waitForTaskToBeImportedReturningId(
      final TasklistComponentHelper tasklist, final long piKey) throws JsonProcessingException {
    final AtomicLong userTaskKey = new AtomicLong();
    final String body =
        objectMapper.writeValueAsString(
            new TaskSearchRequest().setProcessInstanceKey(String.valueOf(piKey)));
    Awaitility.await("Wait for tasks to be imported")
        .ignoreExceptions()
        .pollInterval(Duration.ofSeconds(1))
        .atMost(Duration.ofSeconds(30))
        .until(
            () -> {
              final var response =
                  tasklist.request(
                      b ->
                          b.POST(HttpRequest.BodyPublishers.ofString(body))
                              .uri(URI.create(tasklist.getUrl() + "/tasks/search")),
                      HttpResponse.BodyHandlers.ofString());
              try {
                final var tasks =
                    objectMapper.readValue(
                        response.body(), new TypeReference<List<TaskSearchResponse>>() {});
                if (!tasks.isEmpty()) {
                  userTaskKey.set(Long.parseLong(tasks.getFirst().getId()));
                  return true;
                }
              } catch (final JsonProcessingException ignore) {
                // ignore
              }
              return false;
            });

    return userTaskKey.get();
  }

  @Nested
  class AssignUserTaskTests {

    @TestTemplate
    void shouldAssign87ZeebeTaskV1(
        final DatabaseType databaseType, final TasklistComponentHelper tasklistComponentHelper)
        throws IOException, InterruptedException {
      long startTime = System.nanoTime();
      final var piKey =
          deployAndStartUserTask(databaseType, AbstractUserTaskBuilder::zeebeUserTask);
      final var taskKey = waitForTaskToBeImportedReturningId(tasklistComponentHelper, piKey);
      long elapsedTime = (System.nanoTime() - startTime) / 1_000_000;
      System.out.println("Pre condition time: " + elapsedTime + " ms");

      startTime = System.nanoTime();
      provider.upgradeComponent(Component.ZEEBE, databaseType);
      elapsedTime = (System.nanoTime() - startTime) / 1_000_000;
      System.out.println("Upgrade time Zeebe: " + elapsedTime + " ms");
      startTime = System.nanoTime();
      provider.upgradeComponent(Component.TASKLIST, databaseType);
      elapsedTime = (System.nanoTime() - startTime) / 1_000_000;
      System.out.println("Upgrade time Zeebe: " + elapsedTime + " ms");
      // Print the elapsed time
      final var res = assignTask(tasklistComponentHelper, taskKey, "demo");
      assertThat(res.statusCode()).isEqualTo(200);

      shouldBeAssigned(databaseType, taskKey);
    }

    @TestTemplate
    void shouldAssign87ZeebeTaskV2(
        final DatabaseType databaseType, final TasklistComponentHelper tasklistComponentHelper)
        throws IOException {

      final var piKey =
          deployAndStartUserTask(databaseType, AbstractUserTaskBuilder::zeebeUserTask);
      final var taskKey = waitForTaskToBeImportedReturningId(tasklistComponentHelper, piKey);

      provider.upgradeComponent(Component.ZEEBE, databaseType);
      provider.upgradeComponent(Component.TASKLIST, databaseType);

      provider
          .getCamundaClient(databaseType)
          .newUserTaskAssignCommand(taskKey)
          .assignee("demo")
          .send()
          .join();

      shouldBeAssigned(databaseType, taskKey);
    }

    @TestTemplate
    void shouldAssign88ZeebeTaskV1(
        final DatabaseType databaseType, final TasklistComponentHelper tasklistComponentHelper)
        throws IOException, InterruptedException {

      provider.upgradeComponent(Component.ZEEBE, databaseType);
      provider.upgradeComponent(Component.TASKLIST, databaseType);

      final var piKey =
          deployAndStartUserTask(databaseType, AbstractUserTaskBuilder::zeebeUserTask);
      final var taskKey = waitForTaskToBeImportedReturningId(tasklistComponentHelper, piKey);

      final var res = assignTask(tasklistComponentHelper, taskKey, "demo");
      assertThat(res.statusCode()).isEqualTo(200);

      shouldBeAssigned(databaseType, taskKey);
    }

    @TestTemplate
    void shouldAssign88ZeebeTaskV2(
        final DatabaseType databaseType, final TasklistComponentHelper tasklistComponentHelper)
        throws IOException, InterruptedException {

      provider.upgradeComponent(Component.ZEEBE, databaseType);
      provider.upgradeComponent(Component.TASKLIST, databaseType);

      final var piKey =
          deployAndStartUserTask(databaseType, AbstractUserTaskBuilder::zeebeUserTask);
      final var taskKey = waitForTaskToBeImportedReturningId(tasklistComponentHelper, piKey);

      provider
          .getCamundaClient(databaseType)
          .newUserTaskAssignCommand(taskKey)
          .assignee("demo")
          .send()
          .join();

      shouldBeAssigned(databaseType, taskKey);
    }

    @TestTemplate
    void shouldAssign87JobWorkerV1(
        final DatabaseType databaseType, final TasklistComponentHelper tasklistComponentHelper)
        throws IOException, InterruptedException {

      final var piKey = deployAndStartUserTask(databaseType, t -> t);
      final var taskKey = waitForTaskToBeImportedReturningId(tasklistComponentHelper, piKey);

      provider.upgradeComponent(Component.ZEEBE, databaseType);
      provider.upgradeComponent(Component.TASKLIST, databaseType);

      final var res = assignTask(tasklistComponentHelper, taskKey, "demo");
      assertThat(res.statusCode()).isEqualTo(200);

      shouldBeAssigned(databaseType, taskKey);
    }

    @TestTemplate
    void shouldAssign88JobWorkerV1(
        final DatabaseType databaseType, final TasklistComponentHelper tasklistComponentHelper)
        throws IOException, InterruptedException {

      provider.upgradeComponent(Component.ZEEBE, databaseType);
      provider.upgradeComponent(Component.TASKLIST, databaseType);

      final var piKey = deployAndStartUserTask(databaseType, t -> t);
      final var taskKey = waitForTaskToBeImportedReturningId(tasklistComponentHelper, piKey);

      final var res = assignTask(tasklistComponentHelper, taskKey, "demo");
      assertThat(res.statusCode()).isEqualTo(200);

      shouldBeAssigned(databaseType, taskKey);
    }

    private void shouldBeAssigned(final DatabaseType databaseType, final long userTaskKey) {
      Awaitility.await("User Task should be assigned")
          .atMost(Duration.ofSeconds(15))
          .pollInterval(Duration.ofSeconds(1))
          .untilAsserted(
              () -> {
                final var tasks =
                    provider
                        .getCamundaClient(databaseType)
                        .newUserTaskQuery()
                        .filter(f -> f.userTaskKey(userTaskKey))
                        .send()
                        .join()
                        .items();
                if (!tasks.isEmpty()) {
                  assertThat(tasks.getFirst().getAssignee()).isEqualTo("demo");
                }
              });
    }
  }

  @Nested
  class UnassignUserTaskTests {
    @TestTemplate
    void shouldUnassign87ZeebeTaskV1(
        final DatabaseType databaseType, final TasklistComponentHelper tasklistComponentHelper)
        throws IOException, InterruptedException {

      final var piKey =
          deployAndStartUserTask(databaseType, t -> t.zeebeUserTask().zeebeAssignee("test"));
      final var taskKey = waitForTaskToBeImportedReturningId(tasklistComponentHelper, piKey);

      provider.upgradeComponent(Component.ZEEBE, databaseType);
      provider.upgradeComponent(Component.TASKLIST, databaseType);

      final var res = unassignTask(tasklistComponentHelper, taskKey);
      assertThat(res.statusCode()).isEqualTo(200);

      shouldBeUnassigned(databaseType, taskKey);
    }

    @TestTemplate
    void shouldUnassign87ZeebeTaskV2(
        final DatabaseType databaseType, final TasklistComponentHelper tasklistComponentHelper)
        throws IOException {

      final var piKey =
          deployAndStartUserTask(databaseType, t -> t.zeebeUserTask().zeebeAssignee("test"));
      final var taskKey = waitForTaskToBeImportedReturningId(tasklistComponentHelper, piKey);

      provider.upgradeComponent(Component.ZEEBE, databaseType);
      provider.upgradeComponent(Component.TASKLIST, databaseType);

      provider.getCamundaClient(databaseType).newUserTaskUnassignCommand(taskKey).send().join();

      shouldBeUnassigned(databaseType, taskKey);
    }

    @TestTemplate
    void shouldUnassign88ZeebeTaskV1(
        final DatabaseType databaseType, final TasklistComponentHelper tasklistComponentHelper)
        throws IOException, InterruptedException {

      provider.upgradeComponent(Component.ZEEBE, databaseType);
      provider.upgradeComponent(Component.TASKLIST, databaseType);

      final var piKey =
          deployAndStartUserTask(databaseType, t -> t.zeebeUserTask().zeebeAssignee("test"));
      final var taskKey = waitForTaskToBeImportedReturningId(tasklistComponentHelper, piKey);

      final var res = unassignTask(tasklistComponentHelper, taskKey);
      assertThat(res.statusCode()).isEqualTo(200);

      shouldBeUnassigned(databaseType, taskKey);
    }

    @TestTemplate
    void shouldUnassign88ZeebeTaskV2(
        final DatabaseType databaseType, final TasklistComponentHelper tasklistComponentHelper)
        throws IOException {

      provider.upgradeComponent(Component.ZEEBE, databaseType);
      provider.upgradeComponent(Component.TASKLIST, databaseType);

      final var piKey =
          deployAndStartUserTask(databaseType, t -> t.zeebeUserTask().zeebeAssignee("test"));
      final var taskKey = waitForTaskToBeImportedReturningId(tasklistComponentHelper, piKey);

      provider.getCamundaClient(databaseType).newUserTaskUnassignCommand(taskKey).send().join();

      shouldBeUnassigned(databaseType, taskKey);
    }

    @TestTemplate
    void shouldUnAssign87JobWorkerV1(
        final DatabaseType databaseType, final TasklistComponentHelper tasklistComponentHelper)
        throws IOException, InterruptedException {

      final var piKey = deployAndStartUserTask(databaseType, t -> t.zeebeAssignee("test"));
      final var taskKey = waitForTaskToBeImportedReturningId(tasklistComponentHelper, piKey);

      provider.upgradeComponent(Component.ZEEBE, databaseType);
      provider.upgradeComponent(Component.TASKLIST, databaseType);

      final var res = unassignTask(tasklistComponentHelper, taskKey);
      assertThat(res.statusCode()).isEqualTo(200);

      shouldBeUnassigned(databaseType, taskKey);
    }

    @TestTemplate
    void shouldUnAssign88JobWorkerV1(
        final DatabaseType databaseType, final TasklistComponentHelper tasklistComponentHelper)
        throws IOException, InterruptedException {

      provider.upgradeComponent(Component.ZEEBE, databaseType);
      provider.upgradeComponent(Component.TASKLIST, databaseType);

      final var piKey = deployAndStartUserTask(databaseType, t -> t.zeebeAssignee("test"));
      final var taskKey = waitForTaskToBeImportedReturningId(tasklistComponentHelper, piKey);

      final var res = unassignTask(tasklistComponentHelper, taskKey);
      assertThat(res.statusCode()).isEqualTo(200);

      shouldBeUnassigned(databaseType, taskKey);
    }

    private void shouldBeUnassigned(final DatabaseType databaseType, final long taskKey) {
      Awaitility.await("User Task should be unassigned")
          .atMost(Duration.ofSeconds(15))
          .pollInterval(Duration.ofSeconds(1))
          .untilAsserted(
              () -> {
                final var tasks =
                    provider
                        .getCamundaClient(databaseType)
                        .newUserTaskQuery()
                        .filter(f -> f.userTaskKey(taskKey))
                        .send()
                        .join()
                        .items();
                if (!tasks.isEmpty()) {
                  assertThat(tasks.getFirst().getAssignee()).isNull();
                }
              });
    }
  }

  @Nested
  class CompleteUserTaskTests {
    @TestTemplate
    void shouldComplete87ZeebeTaskV1(
        final DatabaseType databaseType, final TasklistComponentHelper tasklistComponentHelper)
        throws IOException, InterruptedException {

      final var piKey =
          deployAndStartUserTask(databaseType, t -> t.zeebeUserTask().zeebeAssignee("demo"));
      final var taskKey = waitForTaskToBeImportedReturningId(tasklistComponentHelper, piKey);

      provider.upgradeComponent(Component.ZEEBE, databaseType);
      provider.upgradeComponent(Component.TASKLIST, databaseType);

      final var res = completeTask(tasklistComponentHelper, taskKey);
      assertThat(res.statusCode()).isEqualTo(200);

      shouldBeCompleted(databaseType, taskKey);
    }

    @TestTemplate
    void shouldComplete87ZeebeTaskV2(
        final DatabaseType databaseType, final TasklistComponentHelper tasklistComponentHelper)
        throws IOException {

      final var piKey =
          deployAndStartUserTask(databaseType, t -> t.zeebeUserTask().zeebeAssignee("demo"));
      final var taskKey = waitForTaskToBeImportedReturningId(tasklistComponentHelper, piKey);

      provider.upgradeComponent(Component.ZEEBE, databaseType);
      provider.upgradeComponent(Component.TASKLIST, databaseType);

      final var res =
          provider.getCamundaClient(databaseType).newUserTaskCompleteCommand(taskKey).send().join();

      shouldBeCompleted(databaseType, taskKey);
    }

    @TestTemplate
    void shouldComplete88ZeebeTaskV1(
        final DatabaseType databaseType, final TasklistComponentHelper tasklistComponentHelper)
        throws IOException, InterruptedException {

      provider.upgradeComponent(Component.ZEEBE, databaseType);
      provider.upgradeComponent(Component.TASKLIST, databaseType);

      final var piKey =
          deployAndStartUserTask(databaseType, t -> t.zeebeUserTask().zeebeAssignee("demo"));
      final var taskKey = waitForTaskToBeImportedReturningId(tasklistComponentHelper, piKey);

      final var res = completeTask(tasklistComponentHelper, taskKey);
      assertThat(res.statusCode()).isEqualTo(200);

      shouldBeCompleted(databaseType, taskKey);
    }

    @TestTemplate
    void shouldComplete88ZeebeTaskV2(
        final DatabaseType databaseType, final TasklistComponentHelper tasklistComponentHelper)
        throws IOException {

      provider.upgradeComponent(Component.ZEEBE, databaseType);
      provider.upgradeComponent(Component.TASKLIST, databaseType);

      final var piKey =
          deployAndStartUserTask(databaseType, t -> t.zeebeUserTask().zeebeAssignee("demo"));
      final var taskKey = waitForTaskToBeImportedReturningId(tasklistComponentHelper, piKey);

      provider.getCamundaClient(databaseType).newUserTaskCompleteCommand(taskKey).send().join();

      shouldBeCompleted(databaseType, taskKey);
    }

    @TestTemplate
    void shouldComplete87JobWorkerV1(
        final DatabaseType databaseType, final TasklistComponentHelper tasklistComponentHelper)
        throws IOException, InterruptedException {

      final var piKey = deployAndStartUserTask(databaseType, t -> t.zeebeAssignee("demo"));
      final var taskKey = waitForTaskToBeImportedReturningId(tasklistComponentHelper, piKey);

      provider.upgradeComponent(Component.ZEEBE, databaseType);
      provider.upgradeComponent(Component.TASKLIST, databaseType);

      final var res = completeTask(tasklistComponentHelper, taskKey);
      assertThat(res.statusCode()).isEqualTo(200);

      shouldBeCompleted(databaseType, taskKey);
    }

    @TestTemplate
    void shouldComplete88JobWorkerV1(
        final DatabaseType databaseType, final TasklistComponentHelper tasklistComponentHelper)
        throws IOException, InterruptedException {

      provider.upgradeComponent(Component.ZEEBE, databaseType);
      provider.upgradeComponent(Component.TASKLIST, databaseType);

      final var piKey = deployAndStartUserTask(databaseType, t -> t.zeebeAssignee("demo"));
      final var taskKey = waitForTaskToBeImportedReturningId(tasklistComponentHelper, piKey);

      final var res = completeTask(tasklistComponentHelper, taskKey);
      assertThat(res.statusCode()).isEqualTo(200);

      shouldBeCompleted(databaseType, taskKey);
    }

    private void shouldBeCompleted(final DatabaseType databaseType, final long taskKey) {
      Awaitility.await("User Task should be completed")
          .atMost(Duration.ofSeconds(15))
          .pollInterval(Duration.ofSeconds(1))
          .untilAsserted(
              () -> {
                final var tasks =
                    provider
                        .getCamundaClient(databaseType)
                        .newUserTaskQuery()
                        .filter(f -> f.userTaskKey(taskKey))
                        .send()
                        .join()
                        .items();
                if (!tasks.isEmpty()) {
                  assertThat(tasks.getFirst().getState().name()).isEqualTo("COMPLETED");
                }
              });
    }
  }
}
