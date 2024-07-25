/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.it.client;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.client.CamundaClient;
import io.camunda.qa.util.cluster.TestStandaloneCamunda;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.qa.util.junit.ZeebeIntegration;
import io.camunda.zeebe.qa.util.junit.ZeebeIntegration.TestZeebe;
import java.time.Duration;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestInstance.Lifecycle;

@ZeebeIntegration
class SearchUserTaskTest {
  @TestZeebe
  static final TestStandaloneCamunda testStandaloneCamunda =
      new TestStandaloneCamunda();
  static final CamundaClient camundaClient = testStandaloneCamunda.newClientBuilder().build();
  private static Long userTaskKeyTaskAssigned;

  @BeforeAll
  public static void setup() {
    deployProcess("process", "simple.bpmn", "test");
    deployProcess("process-2", "simple-2.bpmn", "test-2");

    startProcessInstance("process");
    startProcessInstance("process-2");
    startProcessInstance("process");

    waitForTasksBeingExported();
  }

  @Test
  public void shouldRetrieveTaskByAssignee() {
    final var result = camundaClient.newUserTaskQuery().filter(f->f.userTaskAssignee("demo")).send().join();
    assertThat(result.items().size()).isEqualTo(1);
    assertThat(result.items().getFirst().getAssignee()).isEqualTo("demo");
    assertThat(result.items().getFirst().getUserTaskKey()).isEqualTo(userTaskKeyTaskAssigned);
  }

  @Test
  public void shouldRetrieveTaskByState(){
    final var result = camundaClient.newUserTaskQuery().filter(f->f.userTaskState("CREATED")).send().join();
    assertThat(result.items().size()).isEqualTo(3);
    result.items().forEach(item -> assertThat(item.getTaskState()).isEqualTo("CREATED"));
  }

//  @Test
//  public void shouldRetrieveTaskByTaskDefinitionId(){
//    final var result = camundaClient.newUserTaskQuery().filter(f->f.userTaskTaskDefinitionId("test-2")).send().join();
//    assertThat(result.items().size()).isEqualTo(1);
//    result.items().forEach(item -> assertThat(item.getTaskState()).isEqualTo("test-2"));
//  }



  private static void deployProcess(final String processId, final String resourceName,
      final String userTaskName) {
    camundaClient
        .newDeployResourceCommand()
        .addProcessModel(
            Bpmn.createExecutableProcess(processId)
                .startEvent()
                .userTask(userTaskName)
                .zeebeUserTask()
                .endEvent()
                .done(),
            resourceName)
        .send()
        .join();
  }

  private static void startProcessInstance(final String processId) {
    camundaClient
        .newCreateInstanceCommand()
        .bpmnProcessId(processId)
        .latestVersion()
        .send()
        .join();
  }

  private static void waitForTasksBeingExported() {
    Awaitility.await("should receive data from ES")
        .atMost(Duration.ofMinutes(1))
        .ignoreExceptions() // Ignore exceptions and continue retrying
        .untilAsserted(
            () -> {
              final var result = camundaClient
                  .newUserTaskQuery()
                  .send()
                  .join();
              assertThat(result.items().size()).isEqualTo(3);
              userTaskKeyTaskAssigned = result.items().getFirst().getUserTaskKey();
            });

    camundaClient.newUserTaskAssignCommand(userTaskKeyTaskAssigned).assignee("demo").action("assignee").send().join();

    Awaitility.await("should export Assigned task and Completed to ElasticSearch")
        .atMost(Duration.ofMinutes(1))
        .ignoreExceptions() // Ignore exceptions and continue retrying
        .untilAsserted(
            () -> {
              final var result = camundaClient
                  .newUserTaskQuery().filter(f -> f.userTaskAssignee("demo"))
                  .send()
                  .join();
              assertThat(result.items().size()).isEqualTo(1);
            });
  }
}
