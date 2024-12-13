/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.it.migration;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.it.migration.util.MigrationUtil;
import io.camunda.webapps.schema.entities.tasklist.TaskEntity.TaskImplementation;
import io.camunda.webapps.schema.entities.tasklist.TaskState;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.model.bpmn.BpmnModelInstance;
import java.io.IOException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.MethodOrderer.OrderAnnotation;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(OrderAnnotation.class)
public class MigrationTest extends MigrationUtil {
  private static final String API_V1 = "V1";
  private static final String API_V2 = "V2";

  @BeforeAll
  static void setUp() throws IOException, InterruptedException {
    ES_CONTAINER.setPortBindings(List.of("9200:9200"));
    MigrationUtil.setup();

    TasklistUtil.startTasklist();

    generateData();
    TasklistUtil.tasklistContainer.stop();

    ZeebeUtil.zeebeClient.close();
    ZeebeUtil.zeebeContainer.stop();

    ZeebeUtil.start87Broker();

    TasklistUtil.startTasklist();
    generate87Data();
  }

  @ParameterizedTest(name = "Assign Zeebe User Task {0} using {2}")
  @MethodSource("zeebeTasks")
  @Order(1)
  void shouldAssignTask(final String recordVersion, final long taskKey, final String apiVersion)
      throws IOException, InterruptedException {

    if (API_V2.equals(apiVersion)) {
      ZeebeUtil.zeebeClient.newUserTaskAssignCommand(taskKey).assignee("demo").send().join();
    } else {
      final var res = TasklistUtil.assign(taskKey);
      assertThat(res.statusCode()).isEqualTo(200);
    }

    Awaitility.await()
        .atMost(Duration.ofSeconds(15))
        .until(
            () -> {
              final var task =
                  ZeebeUtil.zeebeClient
                      .newUserTaskQuery()
                      .filter(f -> f.userTaskKey(taskKey))
                      .send()
                      .join()
                      .items()
                      .getFirst();
              return task != null
                  && task.getAssignee() != null
                  && task.getAssignee().equals("demo");
            });
  }

  @ParameterizedTest(name = "Assign JobWorker {0}")
  @MethodSource("jobWorkers")
  @Order(1)
  void shouldAssignJobWorker(final String recordVersion, final long taskKey)
      throws IOException, InterruptedException {

    final var res = TasklistUtil.assign(taskKey);
    assertThat(res.statusCode()).isEqualTo(200);

    Awaitility.await()
        .atMost(Duration.ofSeconds(15))
        .until(
            () -> {
              final var task =
                  ZeebeUtil.zeebeClient
                      .newUserTaskQuery()
                      .filter(f -> f.userTaskKey(taskKey))
                      .send()
                      .join()
                      .items()
                      .getFirst();
              return task != null
                  && task.getAssignee() != null
                  && task.getAssignee().equals("demo");
            });
  }

  @ParameterizedTest(name = "Unassign Zeebe User Task {0} using {2}")
  @MethodSource("zeebeTasks")
  @Order(2)
  void shouldUnassignTask(final String recordVersion, final long taskKey, final String apiVersion)
      throws IOException, InterruptedException {

    if (API_V2.equals(apiVersion)) {
      ZeebeUtil.zeebeClient.newUserTaskUnassignCommand(taskKey).send().join();
    } else {
      final var res = TasklistUtil.unassignTask(taskKey);
      assertThat(res.statusCode()).isEqualTo(200);
    }

    Awaitility.await()
        .atMost(Duration.ofSeconds(15))
        .until(
            () -> {
              final var task =
                  ZeebeUtil.zeebeClient
                      .newUserTaskQuery()
                      .filter(f -> f.userTaskKey(taskKey))
                      .send()
                      .join()
                      .items()
                      .getFirst();
              return task != null && task.getAssignee() == null;
            });
  }

  @ParameterizedTest(name = "Assign JobWorker {0}")
  @MethodSource("jobWorkers")
  @Order(2)
  void shouldUnAssignJobWorker(final String recordVersion, final long taskKey)
      throws IOException, InterruptedException {
    final var res = TasklistUtil.unassignTask(taskKey);
    assertThat(res.statusCode()).isEqualTo(200);

    Awaitility.await()
        .atMost(Duration.ofSeconds(15))
        .until(
            () -> {
              final var task =
                  ZeebeUtil.zeebeClient
                      .newUserTaskQuery()
                      .filter(f -> f.userTaskKey(taskKey))
                      .send()
                      .join()
                      .items()
                      .getFirst();
              return task != null && task.getAssignee() == null;
            });
  }

  @ParameterizedTest(name = "Update Zeebe User Task {0} using {2}")
  @MethodSource("zeebeTasksForUpdate")
  @Order(3)
  void shouldUpdateTask(final String recordVersion, final long taskKey, final String apiVersion) {

    if (API_V2.equals(apiVersion)) {
      ZeebeUtil.zeebeClient.newUserTaskUpdateCommand(taskKey).priority(88).send().join();
    }

    Awaitility.await()
        .atMost(Duration.ofSeconds(15))
        .until(
            () -> {
              final var task =
                  ZeebeUtil.zeebeClient
                      .newUserTaskQuery()
                      .filter(f -> f.userTaskKey(taskKey))
                      .send()
                      .join()
                      .items()
                      .getFirst();
              return task != null && task.getPriority() == 88;
            });
  }

  @ParameterizedTest(name = "Complete Zeebe User Task {0} using {2}")
  @MethodSource("zeebeTasks")
  @Order(4)
  void shouldCompleteTask(final String recordVersion, final long taskKey, final String apiVersion)
      throws IOException, InterruptedException {

    if (API_V2.equals(apiVersion)) {
      ZeebeUtil.zeebeClient.newUserTaskAssignCommand(taskKey).assignee("demo").send().join();
      ZeebeUtil.zeebeClient.newUserTaskCompleteCommand(taskKey).send().join();
    } else {
      TasklistUtil.assign(taskKey);
      final var res = TasklistUtil.completeTask(taskKey);
      assertThat(res.statusCode()).isEqualTo(200);
    }

    Awaitility.await()
        .atMost(Duration.ofSeconds(15))
        .until(
            () -> {
              final var task =
                  ZeebeUtil.zeebeClient
                      .newUserTaskQuery()
                      .filter(f -> f.userTaskKey(taskKey))
                      .send()
                      .join()
                      .items()
                      .getFirst();
              return task != null && TaskState.COMPLETED.name().equals(task.getState());
            });
  }

  @ParameterizedTest(name = "Complete JobWorker {0}")
  @MethodSource("jobWorkers")
  @Order(2)
  void shouldCompleteJobWorker(final String recordVersion, final long taskKey)
      throws IOException, InterruptedException {
    TasklistUtil.assign(taskKey);
    final var res = TasklistUtil.completeTask(taskKey);
    assertThat(res.statusCode()).isEqualTo(200);

    Awaitility.await()
        .atMost(Duration.ofSeconds(15))
        .until(
            () -> {
              final var task =
                  ZeebeUtil.zeebeClient
                      .newUserTaskQuery()
                      .filter(f -> f.userTaskKey(taskKey))
                      .send()
                      .join()
                      .items()
                      .getFirst();
              return task != null && task.getState().equals(TaskState.COMPLETED.name());
            });
  }

  private static void generate87Data() {

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

          ZeebeUtil.zeebeClient
              .newDeployResourceCommand()
              .addProcessModel(process, i + ".bpmn")
              .send()
              .join();

          ZeebeUtil.zeebeClient
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
              final var tasks = ZeebeUtil.zeebeClient.newUserTaskQuery().send().join().items();
              if (tasks.size() == 6) {
                tasks.forEach(
                    t -> {
                      final var impl =
                          t.getBpmnProcessId().contains("zeebe")
                              ? TaskImplementation.ZEEBE_USER_TASK
                              : TaskImplementation.JOB_WORKER;
                      final var apiVersion = t.getBpmnProcessId().contains("V1") ? "V1" : "V2";
                      TasklistUtil.TASKS.putIfAbsent(impl, new ArrayList<>());
                      final var existsOpt =
                          TasklistUtil.TASKS.values().stream()
                              .flatMap(List::stream)
                              .filter(f -> f.key() == t.getUserTaskKey())
                              .findFirst();
                      if (existsOpt.isEmpty()) {
                        TasklistUtil.TASKS
                            .get(impl)
                            .add(new TestTarget(t.getUserTaskKey(), "8.7", apiVersion));
                      }
                    });
                return true;
              }
              return false;
            });
  }

  private static void generateData() {

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

          ZeebeUtil.zeebeClient
              .newDeployResourceCommand()
              .addProcessModel(process, i + ".bpmn")
              .send()
              .join();

          ZeebeUtil.zeebeClient
              .newCreateInstanceCommand()
              .bpmnProcessId(i + "-process")
              .latestVersion()
              .send()
              .join()
              .getProcessInstanceKey();
        });

    TasklistUtil.waitForTasksToBeImported(3);
  }

  private static Stream<Arguments> zeebeTasks() {
    final Set<String> versions = Set.of(API_V1, API_V2);
    return TasklistUtil.TASKS.get(TaskImplementation.ZEEBE_USER_TASK).stream()
        .flatMap(
            t ->
                versions.stream()
                    .filter(f -> f.equals(t.apiVersion()))
                    .map(v -> Arguments.of(t.version(), t.key(), v)));
  }

  public Stream<Arguments> zeebeTasksForUpdate() {
    return TasklistUtil.TASKS.get(TaskImplementation.ZEEBE_USER_TASK).stream()
        .filter(t -> t.apiVersion().equals(API_V2))
        .map(t -> Arguments.of(t.version(), t.key(), API_V2));
  }

  private static Stream<Arguments> jobWorkers() {
    return TasklistUtil.TASKS.get(TaskImplementation.JOB_WORKER).stream()
        .map(t -> Arguments.of(t.version(), t.key()));
  }
}
