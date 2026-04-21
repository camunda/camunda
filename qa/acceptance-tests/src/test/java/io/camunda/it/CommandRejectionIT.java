/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.it;

import static io.camunda.it.util.TestHelper.waitForProcessInstancesToStart;
import static io.camunda.it.util.TestHelper.waitUntilJobExistsForProcessInstance;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.camunda.client.CamundaClient;
import io.camunda.client.api.command.ProblemException;
import io.camunda.qa.util.cluster.TestCamundaApplication;
import io.camunda.qa.util.multidb.MultiDbTest;
import io.camunda.qa.util.multidb.MultiDbTestApplication;
import io.camunda.security.entity.AuthenticationMethod;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.model.bpmn.BpmnModelInstance;
import io.camunda.zeebe.qa.util.actuator.BanningActuator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledIfSystemProperty;

@MultiDbTest
@DisabledIfSystemProperty(named = "test.integration.camunda.database.type", matches = "rdbms.*$")
@DisabledIfSystemProperty(named = "test.integration.camunda.database.type", matches = "AWS_OS")
public class CommandRejectionIT {

  @MultiDbTestApplication
  private static final TestCamundaApplication CAMUNDA_APPLICATION =
      new TestCamundaApplication()
          .withAuthenticationMethod(AuthenticationMethod.BASIC)
          .withUnauthenticatedAccess();

  private static CamundaClient camundaClient;

  @Test
  public void shouldRejectClientRequestWhenInstanceIsBannedWithKnownProcessInstanceKey() {
    // given - a running process instance waiting on a service task job
    final var processId = "banned-modification-process";
    final var jobType = "banned-modification-type";
    deployProcess(processId, jobType);

    final long processInstanceKey = createProcessInstance(processId);

    waitForProcessInstancesToStart(camundaClient, f -> f.processInstanceKey(processInstanceKey), 1);

    // and - the process instance is banned
    BanningActuator.of(CAMUNDA_APPLICATION).ban(processInstanceKey);

    // when - we attempt to modify the banned process instance
    final var modifyFuture =
        assertThatThrownBy(
                () ->
                    camundaClient
                        .newModifyProcessInstanceCommand(processInstanceKey)
                        .activateElement("taskB")
                        .send()
                        .join())
            .isInstanceOf(ProblemException.class)
            .hasMessageContaining("INVALID_STATE")
            .hasMessageContaining(
                "Expected to process command for process instance with key '%d', but the process instance is banned"
                    .formatted(processInstanceKey));
  }

  @Test
  public void shouldRejectClientRequestWhenInstanceIsBannedWithUnknownProcessInstanceKey() {
    // given - a running process instance waiting on a service task job
    final var processId = "banned-job-complete-process";
    final var jobType = "banned-job-complete-type";
    deployProcess(processId, jobType);

    final long processInstanceKey = createProcessInstance(processId);
    waitForProcessInstancesToStart(camundaClient, f -> f.processInstanceKey(processInstanceKey), 1);
    waitUntilJobExistsForProcessInstance(camundaClient, processInstanceKey);
    final long jobKey =
        camundaClient
            .newJobSearchRequest()
            .filter(f -> f.processInstanceKey(processInstanceKey))
            .send()
            .join()
            .items()
            .getFirst()
            .getJobKey();

    // and - the process instance is banned
    BanningActuator.of(CAMUNDA_APPLICATION).ban(processInstanceKey);

    // when - we attempt to complete the job of the banned process instance
    assertThatThrownBy(() -> camundaClient.newCompleteCommand(jobKey).send().join())
        .isInstanceOf(ProblemException.class)
        .hasMessageContaining("INVALID_STATE")
        .hasMessageContaining(
            "Expected to process command for process instance with key '%d', but the process instance is banned"
                .formatted(processInstanceKey));
  }

  private static BpmnModelInstance twoServiceTasksProcess(
      final String processId, final String jobType) {
    return Bpmn.createExecutableProcess(processId)
        .startEvent()
        .serviceTask("taskA", t -> t.zeebeJobType(jobType))
        .serviceTask("taskB", t -> t.zeebeJobType(jobType))
        .endEvent()
        .done();
  }

  private static void deployProcess(final String processId, final String jobType) {
    camundaClient
        .newDeployResourceCommand()
        .addProcessModel(twoServiceTasksProcess(processId, jobType), processId + ".bpmn")
        .send()
        .join();
  }

  private static long createProcessInstance(final String processId) {
    return camundaClient
        .newCreateInstanceCommand()
        .bpmnProcessId(processId)
        .latestVersion()
        .send()
        .join()
        .getProcessInstanceKey();
  }
}
