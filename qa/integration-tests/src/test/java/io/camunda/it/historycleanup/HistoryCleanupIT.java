/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.it.historycleanup;

import static io.camunda.it.client.QueryTest.deployResource;
import static io.camunda.it.client.QueryTest.startProcessInstance;
import static io.camunda.it.client.QueryTest.waitForFlowNodeInstances;
import static io.camunda.it.client.QueryTest.waitForProcessInstancesToStart;
import static io.camunda.it.client.QueryTest.waitForProcessesToBeDeployed;
import static io.camunda.it.client.QueryTest.waitUntilProcessInstanceIsEnded;
import static io.camunda.it.client.QueryTest.waitUntilProcessInstanceIsGone;
import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.client.CamundaClient;
import io.camunda.client.api.response.ProcessInstanceEvent;
import io.camunda.client.api.search.response.UserTask;
import io.camunda.it.utils.BrokerITInvocationProvider;
import org.junit.jupiter.api.TestTemplate;
import org.junit.jupiter.api.extension.RegisterExtension;

public class HistoryCleanupIT {

  static final String RESOURCE_NAME = "process/process_with_assigned_user_task.bpmn";

  // TODO use MultiDbTest when camunda exporter also supports it
  @RegisterExtension
  static final BrokerITInvocationProvider PROVIDER =
      new BrokerITInvocationProvider()
          .withoutCamundaExporter();

  @TestTemplate
  void shouldDeleteProcessesWhichAreMarkedForCleanup(final CamundaClient camundaClient) {
    // given
    deployResource(camundaClient, RESOURCE_NAME).getProcesses().getFirst();
    waitForProcessesToBeDeployed(camundaClient, 1);
    final ProcessInstanceEvent processInstanceEvent = startProcessInstance(camundaClient,
        "foo", "{\"variable\":\"bud\"}");
    waitForProcessInstancesToStart(camundaClient, 1);
    waitForFlowNodeInstances(camundaClient, 2);

    // when we complete the user task
    final UserTask userTask = camundaClient.newUserTaskQuery().send().join().items().getFirst();
    assertThat(userTask).isNotNull();
    camundaClient.newUserTaskCompleteCommand(userTask.getUserTaskKey()).send().join();

    // then process should be ended
    waitUntilProcessInstanceIsEnded(camundaClient, processInstanceEvent.getProcessInstanceKey());

    // and soon it should be gone
    waitUntilProcessInstanceIsGone(camundaClient, processInstanceEvent.getProcessInstanceKey());
    final var taskAmount = camundaClient.newUserTaskQuery()
        .filter(b -> b.userTaskKey(userTask.getUserTaskKey())).send()
        .join().page().totalItems();
    assertThat(taskAmount).isZero();
  }

}
