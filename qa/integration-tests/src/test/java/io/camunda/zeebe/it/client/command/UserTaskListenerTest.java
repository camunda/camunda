/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.it.client.command;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.zeebe.client.api.usertask.UserTaskListenerJob;
import io.camunda.zeebe.it.util.GrpcClientRule;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.model.bpmn.instance.zeebe.ZeebeUserTaskListenerEventType;
import io.camunda.zeebe.qa.util.cluster.TestStandaloneBroker;
import io.camunda.zeebe.qa.util.junit.ZeebeIntegration;
import io.camunda.zeebe.qa.util.junit.ZeebeIntegration.TestZeebe;
import io.camunda.zeebe.test.util.Strings;
import java.util.ArrayList;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

@ZeebeIntegration
final class UserTaskListenerTest {

  @TestZeebe
  private static final TestStandaloneBroker ZEEBE =
      new TestStandaloneBroker().withRecordingExporter(true);

  private static GrpcClientRule client;

  private final String jobType = Strings.newRandomValidBpmnId();

  @BeforeAll
  static void beforeAll() {
    client = new GrpcClientRule(ZEEBE.newClientBuilder().build());
  }

  @AfterAll
  static void afterAll() {
    client.after();
  }

  @Test
  void shouldWork() {
    // given
    final var process =
        Bpmn.createExecutableProcess("process")
            .startEvent()
            .userTask(
                "task",
                t ->
                    t.zeebeUserTaskListener(
                        "first-listener", ZeebeUserTaskListenerEventType.CREATE))
            .done();
    final var processDefinitionKey = client.deployProcess(process);
    final var processInstanceKey =
        client.createProcessInstance(processDefinitionKey, "{\"a\":1, \"b\":2}");

    // when
    final var userTasks = new ArrayList<UserTaskListenerJob>();

    client
        .getClient()
        .newUserTaskListener()
        .eventType("CREATE")
        .listenerName("first-listener")
        .handler(
            userTaskJob -> {
              System.out.println("I found a user task: " + userTaskJob.getUserTaskKey());

              userTasks.add(userTaskJob);
            })
        .open();

    Awaitility.await("until all jobs are activated")
        .untilAsserted(() -> assertThat(userTasks).hasSize(1));
  }
}
