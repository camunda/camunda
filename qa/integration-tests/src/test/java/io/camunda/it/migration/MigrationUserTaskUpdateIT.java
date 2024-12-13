/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.it.migration;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.it.migration.util.MigrationITInvocationProvider;
import io.camunda.it.migration.util.MigrationITInvocationProvider.DatabaseType;
import io.camunda.it.migration.util.TasklistUtil;
import io.camunda.webapps.schema.entities.tasklist.TaskEntity.TaskImplementation;
import io.camunda.webapps.schema.entities.tasklist.TaskState;
import io.camunda.zeebe.client.ZeebeClient;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.model.bpmn.BpmnModelInstance;
import java.io.IOException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.MethodOrderer.OrderAnnotation;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.api.TestTemplate;
import org.junit.jupiter.api.extension.RegisterExtension;

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
      final ZeebeClient zeebeClient,
      final TasklistUtil tasklistUtil,
      final TasklistUtil.UserTaskArg param)
      throws IOException, InterruptedException {

    if (API_V2.equals(param.apiVersion())) {
      zeebeClient.newUserTaskAssignCommand(param.key()).assignee("demo").send().join();
    } else {
      final var res = tasklistUtil.assign(param.key(), "demo");
      assertThat(res.statusCode()).isEqualTo(200);
    }

    Awaitility.await()
        .atMost(Duration.ofSeconds(15))
        .until(
            () -> {
              final var task =
                  zeebeClient
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
      final ZeebeClient zeebeClient,
      final TasklistUtil tasklistUtil,
      final TasklistUtil.UserTaskArg param)
      throws IOException, InterruptedException {

    final var res = tasklistUtil.assign(param.key(), "demo");
    assertThat(res.statusCode()).isEqualTo(200);

    Awaitility.await()
        .atMost(Duration.ofSeconds(15))
        .until(
            () -> {
              final var task =
                  zeebeClient
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

  @Order(2)
  @TestTemplate
  void shouldUnassignZeebeTask(
      final ZeebeClient zeebeClient,
      final TasklistUtil tasklistUtil,
      final TasklistUtil.UserTaskArg param)
      throws IOException, InterruptedException {

    if (API_V2.equals(param.apiVersion())) {
      zeebeClient.newUserTaskUnassignCommand(param.key()).send().join();
    } else {
      final var res = tasklistUtil.unassignTask(param.key());
      assertThat(res.statusCode()).isEqualTo(200);
    }

    Awaitility.await()
        .atMost(Duration.ofSeconds(15))
        .until(
            () -> {
              final var task =
                  zeebeClient
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
      final ZeebeClient zeebeClient,
      final TasklistUtil tasklistUtil,
      final TasklistUtil.UserTaskArg param)
      throws IOException, InterruptedException {
    final var res = tasklistUtil.unassignTask(param.key());
    assertThat(res.statusCode()).isEqualTo(200);

    Awaitility.await()
        .atMost(Duration.ofSeconds(15))
        .until(
            () -> {
              final var task =
                  zeebeClient
                      .newUserTaskQuery()
                      .filter(f -> f.userTaskKey(param.key()))
                      .send()
                      .join()
                      .items()
                      .getFirst();
              return task != null && task.getAssignee() == null;
            });
  }

  @Order(3)
  @TestTemplate
  void shouldUpdateZeebeTask(final ZeebeClient zeebeClient, final TasklistUtil.UserTaskArg param) {

    if (API_V2.equals(param.apiVersion())) {
      zeebeClient.newUserTaskUpdateCommand(param.key()).priority(88).send().join();
    }

    Awaitility.await()
        .atMost(Duration.ofSeconds(15))
        .until(
            () -> {
              final var task =
                  zeebeClient
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
      final ZeebeClient zeebeClient,
      final TasklistUtil tasklistUtil,
      final TasklistUtil.UserTaskArg param)
      throws IOException, InterruptedException {

    if (API_V2.equals(param.apiVersion())) {
      zeebeClient.newUserTaskAssignCommand(param.key()).assignee("demo").send().join();
      zeebeClient.newUserTaskCompleteCommand(param.key()).send().join();
    } else {
      final var assignRes = tasklistUtil.assign(param.key(), "demo");
      assertThat(assignRes.statusCode()).isEqualTo(200);
      final var res = tasklistUtil.completeTask(param.key());
      assertThat(res.statusCode()).isEqualTo(200);
    }

    Awaitility.await()
        .atMost(Duration.ofSeconds(15))
        .until(
            () -> {
              final var task =
                  zeebeClient
                      .newUserTaskQuery()
                      .filter(f -> f.userTaskKey(param.key()))
                      .send()
                      .join()
                      .items()
                      .getFirst();
              return task != null && TaskState.COMPLETED.name().equals(task.getState());
            });
  }

  @Order(2)
  @TestTemplate
  void shouldCompleteJobWorker(
      final ZeebeClient zeebeClient,
      final TasklistUtil tasklistUtil,
      final TasklistUtil.UserTaskArg param)
      throws IOException, InterruptedException {
    tasklistUtil.assign(param.key(), "demo");
    final var res = tasklistUtil.completeTask(param.key());
    assertThat(res.statusCode()).isEqualTo(200);

    Awaitility.await()
        .atMost(Duration.ofSeconds(15))
        .until(
            () -> {
              final var task =
                  zeebeClient
                      .newUserTaskQuery()
                      .filter(f -> f.userTaskKey(param.key()))
                      .send()
                      .join()
                      .items()
                      .getFirst();
              return task != null && task.getState().equals(TaskState.COMPLETED.name());
            });
  }

  private static void generate87Data(
      final ZeebeClient zeebeClient, final TasklistUtil tasklistUtil) {

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

          zeebeClient
              .newDeployResourceCommand()
              .addProcessModel(process, i + ".bpmn")
              .send()
              .join();

          zeebeClient
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
              final var tasks = zeebeClient.newUserTaskQuery().send().join().items();
              if (tasks.size() == 6) {
                tasks.forEach(
                    t -> {
                      final var impl =
                          t.getBpmnProcessId().contains("zeebe")
                              ? TaskImplementation.ZEEBE_USER_TASK
                              : TaskImplementation.JOB_WORKER;
                      final var apiVersion = t.getBpmnProcessId().contains("V1") ? "V1" : "V2";
                      tasklistUtil.generatedTasks.putIfAbsent(impl, new ArrayList<>());
                      final var existsOpt =
                          tasklistUtil.generatedTasks.values().stream()
                              .flatMap(List::stream)
                              .filter(f -> f.key() == t.getUserTaskKey())
                              .findFirst();
                      if (existsOpt.isEmpty()) {
                        tasklistUtil
                            .generatedTasks
                            .get(impl)
                            .add(
                                new TasklistUtil.UserTaskArg(
                                    t.getUserTaskKey(), "8.7", apiVersion));
                      }
                    });
                return true;
              }
              return false;
            });
  }

  private static void generateData(final ZeebeClient zeebeClient, final TasklistUtil tasklistUtil) {

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

          zeebeClient
              .newDeployResourceCommand()
              .addProcessModel(process, i + ".bpmn")
              .send()
              .join();

          zeebeClient
              .newCreateInstanceCommand()
              .bpmnProcessId(i + "-process")
              .latestVersion()
              .send()
              .join()
              .getProcessInstanceKey();
        });
    tasklistUtil.waitForTasksToBeImported(3);
  }
}
