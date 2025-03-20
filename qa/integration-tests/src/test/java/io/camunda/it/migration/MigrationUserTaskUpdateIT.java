/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.it.migration;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.client.CamundaClient;
import io.camunda.client.api.search.response.UserTaskState;
import io.camunda.it.migration.util.MigrationITInvocationProvider;
import io.camunda.it.migration.util.MigrationITInvocationProvider.DatabaseType;
import io.camunda.it.migration.util.TasklistMigrationHelper;
import io.camunda.tasklist.webapp.api.rest.v1.entities.TaskSearchRequest;
import io.camunda.webapps.schema.entities.tasklist.TaskEntity.TaskImplementation;
import io.camunda.webapps.schema.entities.tasklist.TaskState;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.model.bpmn.BpmnModelInstance;
import java.io.IOException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.MethodOrderer.OrderAnnotation;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.api.TestTemplate;
import org.junit.jupiter.api.extension.RegisterExtension;

@Disabled
@TestMethodOrder(OrderAnnotation.class)
public class MigrationUserTaskUpdateIT {

  @RegisterExtension
  static final MigrationITInvocationProvider PROVIDER =
      new MigrationITInvocationProvider()
          .withDatabaseTypes(DatabaseType.ELASTICSEARCH, DatabaseType.OPENSEARCH)
          .withRunBefore(MigrationUserTaskUpdateIT::generateData)
          .withRunAfter(MigrationUserTaskUpdateIT::generate87Data);

  private static final String API_V2 = "V2";

  @TestTemplate
  @Order(1)
  void shouldAssignZeebeTask(
      final CamundaClient camundaClient,
      final TasklistMigrationHelper tasklistMigrationHelper,
      final TasklistMigrationHelper.UserTaskArg param)
      throws IOException, InterruptedException {

    if (API_V2.equals(param.apiVersion())) {
      camundaClient.newUserTaskAssignCommand(param.key()).assignee("demo").send().join();
    } else {
      final var res = tasklistMigrationHelper.assign(param.key(), "demo");
      assertThat(res.statusCode()).isEqualTo(200);
    }

    Awaitility.await()
        .atMost(Duration.ofSeconds(15))
        .until(
            () -> {
              final var task =
                  camundaClient
                      .newUserTaskQuery()
                      .filter(f -> f.userTaskKey(param.key()))
                      .send()
                      .join()
                      .items()
                      .getFirst();
              return task != null
                  && task.getAssignee() != null
                  && task.getAssignee().equals("demo");
            });
  }

  @Order(1)
  @TestTemplate
  void shouldAssignJobWorker(
      final CamundaClient camundaClient,
      final TasklistMigrationHelper tasklistMigrationHelper,
      final TasklistMigrationHelper.UserTaskArg param)
      throws IOException, InterruptedException {

    final var res = tasklistMigrationHelper.assign(param.key(), "demo");
    assertThat(res.statusCode()).isEqualTo(200);

    Awaitility.await()
        .atMost(Duration.ofSeconds(15))
        .until(
            () -> {
              final var taskOpt = tasklistMigrationHelper.getUserTask(param.key());

              return taskOpt.isPresent() && taskOpt.get().getAssignee().equals("demo");
            });
  }

  @Order(2)
  @TestTemplate
  void shouldUnassignZeebeTask(
      final CamundaClient camundaClient,
      final TasklistMigrationHelper tasklistMigrationHelper,
      final TasklistMigrationHelper.UserTaskArg param)
      throws IOException, InterruptedException {

    if (API_V2.equals(param.apiVersion())) {
      camundaClient.newUserTaskUnassignCommand(param.key()).send().join();
    } else {
      final var res = tasklistMigrationHelper.unassignTask(param.key());
      assertThat(res.statusCode()).isEqualTo(200);
    }

    Awaitility.await()
        .atMost(Duration.ofSeconds(15))
        .until(
            () -> {
              final var task =
                  camundaClient
                      .newUserTaskQuery()
                      .filter(f -> f.userTaskKey(param.key()))
                      .send()
                      .join()
                      .items()
                      .getFirst();
              return task != null && task.getAssignee() == null;
            });
  }

  @Order(2)
  @TestTemplate
  void shouldUnAssignJobWorker(
      final CamundaClient camundaClient,
      final TasklistMigrationHelper tasklistMigrationHelper,
      final TasklistMigrationHelper.UserTaskArg param)
      throws IOException, InterruptedException {
    final var res = tasklistMigrationHelper.unassignTask(param.key());
    assertThat(res.statusCode()).isEqualTo(200);

    Awaitility.await()
        .atMost(Duration.ofSeconds(15))
        .until(
            () -> {
              final var taskOpt = tasklistMigrationHelper.getUserTask(param.key());

              return taskOpt.isPresent() && taskOpt.get().getAssignee() == null;
            });
  }

  @Order(3)
  @TestTemplate
  void shouldUpdateZeebeTask(
      final CamundaClient camundaClient, final TasklistMigrationHelper.UserTaskArg param) {

    if (API_V2.equals(param.apiVersion())) {
      camundaClient.newUserTaskUpdateCommand(param.key()).priority(88).send().join();
    }

    Awaitility.await()
        .atMost(Duration.ofSeconds(15))
        .until(
            () -> {
              final var task =
                  camundaClient
                      .newUserTaskQuery()
                      .filter(f -> f.userTaskKey(param.key()))
                      .send()
                      .join()
                      .items()
                      .getFirst();
              return task != null && task.getPriority() == 88;
            });
  }

  @Order(4)
  @TestTemplate
  void shouldCompleteZeebeTask(
      final CamundaClient camundaClient,
      final TasklistMigrationHelper tasklistMigrationHelper,
      final TasklistMigrationHelper.UserTaskArg param)
      throws IOException, InterruptedException {

    if (API_V2.equals(param.apiVersion())) {
      camundaClient.newUserTaskAssignCommand(param.key()).assignee("demo").send().join();
      camundaClient.newUserTaskCompleteCommand(param.key()).send().join();
    } else {
      final var assignRes = tasklistMigrationHelper.assign(param.key(), "demo");
      assertThat(assignRes.statusCode()).isEqualTo(200);
      final var res = tasklistMigrationHelper.completeTask(param.key());
      assertThat(res.statusCode()).isEqualTo(200);
    }

    Awaitility.await()
        .atMost(Duration.ofSeconds(15))
        .until(
            () -> {
              final var task =
                  camundaClient
                      .newUserTaskQuery()
                      .filter(f -> f.userTaskKey(param.key()))
                      .send()
                      .join()
                      .items()
                      .getFirst();
              return task != null && UserTaskState.COMPLETED.equals(task.getState());
            });
  }

  @Order(2)
  @TestTemplate
  void shouldCompleteJobWorker(
      final CamundaClient camundaClient,
      final TasklistMigrationHelper tasklistMigrationHelper,
      final TasklistMigrationHelper.UserTaskArg param)
      throws IOException, InterruptedException {
    tasklistMigrationHelper.assign(param.key(), "demo");
    final var res = tasklistMigrationHelper.completeTask(param.key());
    assertThat(res.statusCode()).isEqualTo(200);

    Awaitility.await()
        .atMost(Duration.ofSeconds(15))
        .until(
            () -> {
              final var taskOpt = tasklistMigrationHelper.getUserTask(param.key());

              return taskOpt.isPresent()
                  && taskOpt.get().getTaskState().equals(TaskState.COMPLETED);
            });
  }

  private static void generate87Data(
      final CamundaClient camundaClient, final TasklistMigrationHelper tasklistMigrationHelper) {

    final List<String> instances = List.of("zeebeV187task", "zeebeV287task", "jobWorkerV187task");

    instances.forEach(
        i -> {
          BpmnModelInstance process = null;
          if (i.contains("zeebe")) {
            process =
                Bpmn.createExecutableProcess(i + "-process")
                    .startEvent()
                    .name("start")
                    .userTask(i)
                    .zeebeUserTask()
                    .endEvent()
                    .done();
          } else {
            process =
                Bpmn.createExecutableProcess(i + "-process")
                    .startEvent()
                    .name("start")
                    .userTask(i)
                    .endEvent()
                    .done();
          }

          camundaClient
              .newDeployResourceCommand()
              .addProcessModel(process, i + ".bpmn")
              .send()
              .join();

          camundaClient
              .newCreateInstanceCommand()
              .bpmnProcessId(i + "-process")
              .latestVersion()
              .send()
              .join()
              .getProcessInstanceKey();
        });

    Awaitility.await()
        .atMost(Duration.ofSeconds(30))
        .pollInterval(Duration.ofSeconds(2))
        .until(
            () -> {
              // Check if Zeebe User Tasks are present
              final var tasks = camundaClient.newUserTaskQuery().send().join().items();
              boolean zeebeTasksPresent = false;
              if (tasks.size() == 4) {
                zeebeTasksPresent = true;
                tasks.forEach(
                    t -> {
                      final var apiVersion = t.getBpmnProcessId().contains("V1") ? "V1" : "V2";
                      tasklistMigrationHelper.generatedTasks.putIfAbsent(
                          TaskImplementation.ZEEBE_USER_TASK, new ArrayList<>());
                      final var existsOpt =
                          tasklistMigrationHelper.generatedTasks.values().stream()
                              .flatMap(List::stream)
                              .filter(f -> f.key() == t.getUserTaskKey())
                              .findFirst();
                      if (existsOpt.isEmpty()) {
                        tasklistMigrationHelper
                            .generatedTasks
                            .get(TaskImplementation.ZEEBE_USER_TASK)
                            .add(
                                new TasklistMigrationHelper.UserTaskArg(
                                    t.getUserTaskKey(), "8.7", apiVersion));
                      }
                    });
              }

              // Check if Job Worker Task is present
              final var tasklistV1Tasks =
                  tasklistMigrationHelper.searchUserTasks(
                      new TaskSearchRequest().setTaskDefinitionId("jobWorkerV187task"));
              final boolean jobWorkerTaskPresent = tasklistV1Tasks.size() == 1;
              tasklistV1Tasks.forEach(
                  t -> {
                    tasklistMigrationHelper.generatedTasks.putIfAbsent(
                        TaskImplementation.JOB_WORKER, new ArrayList<>());
                    tasklistMigrationHelper
                        .generatedTasks
                        .get(TaskImplementation.JOB_WORKER)
                        .add(
                            new TasklistMigrationHelper.UserTaskArg(
                                Long.parseLong(t.getId()), "8.7", "V1"));
                  });

              return zeebeTasksPresent && jobWorkerTaskPresent;
            });
  }

  private static void generateData(
      final CamundaClient camundaClient, final TasklistMigrationHelper tasklistMigrationHelper) {

    final List<String> instances = List.of("zeebeV186task", "zeebeV286task", "jobWorkerV186task");

    instances.forEach(
        i -> {
          final BpmnModelInstance process;
          if (i.contains("zeebe")) {
            process =
                Bpmn.createExecutableProcess(i + "-process")
                    .startEvent()
                    .name("start")
                    .userTask(i)
                    .zeebeUserTask()
                    .endEvent()
                    .done();
          } else {
            process =
                Bpmn.createExecutableProcess(i + "-process")
                    .startEvent()
                    .name("start")
                    .userTask(i)
                    .endEvent()
                    .done();
          }

          camundaClient
              .newDeployResourceCommand()
              .addProcessModel(process, i + ".bpmn")
              .send()
              .join();

          camundaClient
              .newCreateInstanceCommand()
              .bpmnProcessId(i + "-process")
              .latestVersion()
              .send()
              .join()
              .getProcessInstanceKey();
        });
    tasklistMigrationHelper.waitForTasksToBeImported(3);
  }
}
