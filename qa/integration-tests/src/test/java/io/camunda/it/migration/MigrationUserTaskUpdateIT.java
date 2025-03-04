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
import io.camunda.it.migration.util.MigrationHelper;
import io.camunda.it.migration.util.MigrationITExtension;
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

  @RegisterExtension static final MigrationITExtension PROVIDER = new MigrationITExtension();

  private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

  private long deployAndStartUserTaskProcess(
      final DatabaseType databaseType, final UnaryOperator<UserTaskBuilder> builder) {
    final var process =
        Bpmn.createExecutableProcess("task-process")
            .startEvent()
            .name("start")
            .userTask("user-task", builder::apply)
            .endEvent()
            .done();

    PROVIDER
        .getCamundaClient(databaseType)
        .newDeployResourceCommand()
        .addProcessModel(process, "task-process.bpmn")
        .send()
        .join();

    return PROVIDER
        .getCamundaClient(databaseType)
        .newCreateInstanceCommand()
        .bpmnProcessId("task-process")
        .latestVersion()
        .send()
        .join()
        .getProcessInstanceKey();
  }

  private long waitForTaskToBeImportedReturningId(final MigrationHelper helper, final long piKey)
      throws JsonProcessingException {
    final AtomicLong userTaskKey = new AtomicLong();
    final String body =
        OBJECT_MAPPER.writeValueAsString(
            new TaskSearchRequest().setProcessInstanceKey(String.valueOf(piKey)));
    Awaitility.await("Wait for tasks to be imported")
        .ignoreExceptions()
        .pollInterval(Duration.ofSeconds(1))
        .atMost(Duration.ofSeconds(30))
        .until(
            () -> {
              try {
                final var response =
                    helper.request(
                        b ->
                            b.POST(HttpRequest.BodyPublishers.ofString(body))
                                .uri(URI.create(helper.tasklistUrl() + "/tasks/search")),
                        HttpResponse.BodyHandlers.ofString());
                final var tasks =
                    OBJECT_MAPPER.readValue(
                        response.body(), new TypeReference<List<TaskSearchResponse>>() {});
                if (!tasks.isEmpty()) {
                  userTaskKey.set(Long.parseLong(tasks.getFirst().getId()));
                  return true;
                }
              } catch (final IOException ignore) {
                // ignore
              }
              return false;
            });

    return userTaskKey.get();
  }

  @Nested
  class AssignUserTaskTests {

    @TestTemplate
    void shouldAssign87ZeebeTaskV1(final DatabaseType databaseType, final MigrationHelper helper)
        throws IOException {
      final var piKey =
          deployAndStartUserTaskProcess(databaseType, AbstractUserTaskBuilder::zeebeUserTask);
      final var taskKey = waitForTaskToBeImportedReturningId(helper, piKey);
      PROVIDER.has87Data(databaseType);
      PROVIDER.upgrade(databaseType);
      final var res =
          helper
              .getTasklistClient()
              .withAuthentication("demo", "demo")
              .assignUserTask(taskKey, "demo");
      assertThat(res.statusCode()).isEqualTo(200);

      shouldBeAssigned(databaseType, taskKey);
    }

    @TestTemplate
    void shouldAssign87ZeebeTaskV2(final DatabaseType databaseType, final MigrationHelper helper)
        throws IOException {

      final var piKey =
          deployAndStartUserTaskProcess(databaseType, AbstractUserTaskBuilder::zeebeUserTask);
      final var taskKey = waitForTaskToBeImportedReturningId(helper, piKey);

      PROVIDER.has87Data(databaseType);
      PROVIDER.upgrade(databaseType);

      PROVIDER
          .getCamundaClient(databaseType)
          .newUserTaskAssignCommand(taskKey)
          .assignee("demo")
          .send()
          .join();

      shouldBeAssigned(databaseType, taskKey);
    }

    @TestTemplate
    void shouldAssign88ZeebeTaskV1(final DatabaseType databaseType, final MigrationHelper helper)
        throws IOException {

      PROVIDER.upgrade(databaseType);

      final var piKey =
          deployAndStartUserTaskProcess(databaseType, AbstractUserTaskBuilder::zeebeUserTask);
      final var taskKey = waitForTaskToBeImportedReturningId(helper, piKey);

      final var res =
          helper
              .getTasklistClient()
              .withAuthentication("demo", "demo")
              .assignUserTask(taskKey, "demo");
      assertThat(res.statusCode()).isEqualTo(200);

      shouldBeAssigned(databaseType, taskKey);
    }

    @TestTemplate
    void shouldAssign88ZeebeTaskV2(final DatabaseType databaseType, final MigrationHelper helper)
        throws IOException {

      PROVIDER.upgrade(databaseType);

      final var piKey =
          deployAndStartUserTaskProcess(databaseType, AbstractUserTaskBuilder::zeebeUserTask);
      final var taskKey = waitForTaskToBeImportedReturningId(helper, piKey);

      PROVIDER
          .getCamundaClient(databaseType)
          .newUserTaskAssignCommand(taskKey)
          .assignee("demo")
          .send()
          .join();

      shouldBeAssigned(databaseType, taskKey);
    }

    @TestTemplate
    void shouldAssign87JobWorkerV1(final DatabaseType databaseType, final MigrationHelper helper)
        throws IOException {

      final var piKey = deployAndStartUserTaskProcess(databaseType, t -> t);
      final var taskKey = waitForTaskToBeImportedReturningId(helper, piKey);
      PROVIDER.has87Data(databaseType);
      PROVIDER.upgrade(databaseType);

      final var res =
          helper
              .getTasklistClient()
              .withAuthentication("demo", "demo")
              .assignUserTask(taskKey, "demo");
      assertThat(res.statusCode()).isEqualTo(200);

      shouldBeAssigned(databaseType, taskKey);
    }

    @TestTemplate
    void shouldAssign88JobWorkerV1(final DatabaseType databaseType, final MigrationHelper helper)
        throws IOException {

      PROVIDER.upgrade(databaseType);

      final var piKey = deployAndStartUserTaskProcess(databaseType, t -> t);
      final var taskKey = waitForTaskToBeImportedReturningId(helper, piKey);

      final var res =
          helper
              .getTasklistClient()
              .withAuthentication("demo", "demo")
              .assignUserTask(taskKey, "demo");
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
                    PROVIDER
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
    void shouldUnassign87ZeebeTaskV1(final DatabaseType databaseType, final MigrationHelper helper)
        throws IOException {

      final var piKey =
          deployAndStartUserTaskProcess(databaseType, t -> t.zeebeUserTask().zeebeAssignee("test"));
      final var taskKey = waitForTaskToBeImportedReturningId(helper, piKey);

      PROVIDER.has87Data(databaseType);
      PROVIDER.upgrade(databaseType);

      final var res =
          helper.getTasklistClient().withAuthentication("demo", "demo").unassignUserTask(taskKey);
      assertThat(res.statusCode()).isEqualTo(200);

      shouldBeUnassigned(databaseType, taskKey);
    }

    @TestTemplate
    void shouldUnassign87ZeebeTaskV2(final DatabaseType databaseType, final MigrationHelper helper)
        throws IOException {

      final var piKey =
          deployAndStartUserTaskProcess(databaseType, t -> t.zeebeUserTask().zeebeAssignee("test"));
      final var taskKey = waitForTaskToBeImportedReturningId(helper, piKey);

      PROVIDER.has87Data(databaseType);
      PROVIDER.upgrade(databaseType);

      PROVIDER.getCamundaClient(databaseType).newUserTaskUnassignCommand(taskKey).send().join();

      shouldBeUnassigned(databaseType, taskKey);
    }

    @TestTemplate
    void shouldUnassign88ZeebeTaskV1(final DatabaseType databaseType, final MigrationHelper helper)
        throws IOException {

      PROVIDER.upgrade(databaseType);

      final var piKey =
          deployAndStartUserTaskProcess(databaseType, t -> t.zeebeUserTask().zeebeAssignee("test"));
      final var taskKey = waitForTaskToBeImportedReturningId(helper, piKey);

      final var res =
          helper.getTasklistClient().withAuthentication("demo", "demo").unassignUserTask(taskKey);
      assertThat(res.statusCode()).isEqualTo(200);

      shouldBeUnassigned(databaseType, taskKey);
    }

    @TestTemplate
    void shouldUnassign88ZeebeTaskV2(final DatabaseType databaseType, final MigrationHelper helper)
        throws IOException {

      PROVIDER.upgrade(databaseType);

      final var piKey =
          deployAndStartUserTaskProcess(databaseType, t -> t.zeebeUserTask().zeebeAssignee("test"));
      final var taskKey = waitForTaskToBeImportedReturningId(helper, piKey);

      PROVIDER.getCamundaClient(databaseType).newUserTaskUnassignCommand(taskKey).send().join();

      shouldBeUnassigned(databaseType, taskKey);
    }

    @TestTemplate
    void shouldUnAssign87JobWorkerV1(final DatabaseType databaseType, final MigrationHelper helper)
        throws IOException {

      final var piKey = deployAndStartUserTaskProcess(databaseType, t -> t.zeebeAssignee("test"));
      final var taskKey = waitForTaskToBeImportedReturningId(helper, piKey);

      PROVIDER.has87Data(databaseType);
      PROVIDER.upgrade(databaseType);

      final var res =
          helper.getTasklistClient().withAuthentication("demo", "demo").unassignUserTask(taskKey);
      assertThat(res.statusCode()).isEqualTo(200);

      shouldBeUnassigned(databaseType, taskKey);
    }

    @TestTemplate
    void shouldUnAssign88JobWorkerV1(final DatabaseType databaseType, final MigrationHelper helper)
        throws IOException {

      PROVIDER.upgrade(databaseType);

      final var piKey = deployAndStartUserTaskProcess(databaseType, t -> t.zeebeAssignee("test"));
      final var taskKey = waitForTaskToBeImportedReturningId(helper, piKey);

      final var res =
          helper.getTasklistClient().withAuthentication("demo", "demo").unassignUserTask(taskKey);
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
                    PROVIDER
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
    void shouldComplete87ZeebeTaskV1(final DatabaseType databaseType, final MigrationHelper helper)
        throws IOException {

      final var piKey =
          deployAndStartUserTaskProcess(databaseType, t -> t.zeebeUserTask().zeebeAssignee("demo"));
      final var taskKey = waitForTaskToBeImportedReturningId(helper, piKey);

      PROVIDER.has87Data(databaseType);
      PROVIDER.upgrade(databaseType);

      final var res =
          helper.getTasklistClient().withAuthentication("demo", "demo").completeUserTask(taskKey);
      assertThat(res.statusCode()).isEqualTo(200);

      shouldBeCompleted(databaseType, taskKey);
    }

    @TestTemplate
    void shouldComplete87ZeebeTaskV2(final DatabaseType databaseType, final MigrationHelper helper)
        throws IOException {

      final var piKey =
          deployAndStartUserTaskProcess(databaseType, t -> t.zeebeUserTask().zeebeAssignee("demo"));
      final var taskKey = waitForTaskToBeImportedReturningId(helper, piKey);

      PROVIDER.has87Data(databaseType);
      PROVIDER.upgrade(databaseType);

      final var res =
          PROVIDER.getCamundaClient(databaseType).newUserTaskCompleteCommand(taskKey).send().join();

      shouldBeCompleted(databaseType, taskKey);
    }

    @TestTemplate
    void shouldComplete88ZeebeTaskV1(final DatabaseType databaseType, final MigrationHelper helper)
        throws IOException {

      PROVIDER.upgrade(databaseType);

      final var piKey =
          deployAndStartUserTaskProcess(databaseType, t -> t.zeebeUserTask().zeebeAssignee("demo"));
      final var taskKey = waitForTaskToBeImportedReturningId(helper, piKey);

      final var res =
          helper.getTasklistClient().withAuthentication("demo", "demo").completeUserTask(taskKey);
      assertThat(res.statusCode()).isEqualTo(200);

      shouldBeCompleted(databaseType, taskKey);
    }

    @TestTemplate
    void shouldComplete88ZeebeTaskV2(final DatabaseType databaseType, final MigrationHelper helper)
        throws IOException {

      PROVIDER.upgrade(databaseType);

      final var piKey =
          deployAndStartUserTaskProcess(databaseType, t -> t.zeebeUserTask().zeebeAssignee("demo"));
      final var taskKey = waitForTaskToBeImportedReturningId(helper, piKey);

      PROVIDER.getCamundaClient(databaseType).newUserTaskCompleteCommand(taskKey).send().join();

      shouldBeCompleted(databaseType, taskKey);
    }

    @TestTemplate
    void shouldComplete87JobWorkerV1(final DatabaseType databaseType, final MigrationHelper helper)
        throws IOException {

      final var piKey = deployAndStartUserTaskProcess(databaseType, t -> t.zeebeAssignee("demo"));
      final var taskKey = waitForTaskToBeImportedReturningId(helper, piKey);

      PROVIDER.has87Data(databaseType);
      PROVIDER.upgrade(databaseType);

      final var res =
          helper.getTasklistClient().withAuthentication("demo", "demo").completeUserTask(taskKey);
      assertThat(res.statusCode()).isEqualTo(200);

      shouldBeCompleted(databaseType, taskKey);
    }

    @TestTemplate
    void shouldComplete88JobWorkerV1(final DatabaseType databaseType, final MigrationHelper helper)
        throws IOException {

      PROVIDER.upgrade(databaseType);

      final var piKey = deployAndStartUserTaskProcess(databaseType, t -> t.zeebeAssignee("demo"));
      final var taskKey = waitForTaskToBeImportedReturningId(helper, piKey);

      final var res =
          helper.getTasklistClient().withAuthentication("demo", "demo").completeUserTask(taskKey);
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
                    PROVIDER
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
